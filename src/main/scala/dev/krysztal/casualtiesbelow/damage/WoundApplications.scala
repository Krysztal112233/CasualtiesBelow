package dev.krysztal.casualtiesbelow.damage

import scala.jdk.OptionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.enchantment.EnchantmentHelper

import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.data.FallRulesData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData
import dev.krysztal.casualtiesbelow.api.wound.ClassifiedWoundRule
import dev.krysztal.casualtiesbelow.api.wound.ConditionStepData
import dev.krysztal.casualtiesbelow.api.wound.FixedTargetData
import dev.krysztal.casualtiesbelow.api.wound.HitLocation
import dev.krysztal.casualtiesbelow.api.wound.HitLocationTargetData
import dev.krysztal.casualtiesbelow.api.wound.LocalizedApplicationData
import dev.krysztal.casualtiesbelow.api.wound.PairedImpactApplicationData
import dev.krysztal.casualtiesbelow.api.wound.ResolvedLegacyWoundApplication
import dev.krysztal.casualtiesbelow.api.wound.ResolvedTypedWoundApplication
import dev.krysztal.casualtiesbelow.api.wound.ResolvedWoundContribution
import dev.krysztal.casualtiesbelow.api.wound.ScatterApplicationData
import dev.krysztal.casualtiesbelow.api.wound.SpillImpactData
import dev.krysztal.casualtiesbelow.api.wound.WeightedTargetData
import dev.krysztal.casualtiesbelow.api.wound.WoundApplicationData
import dev.krysztal.casualtiesbelow.api.wound.WoundSeverityPolicy
import dev.krysztal.casualtiesbelow.api.wound.WoundTargetData
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue

/** Executes the typed applications of one classified damage event. All body mutation still passes
  * through [[LimbInjuries]], preserving per-limb armor, jitter, callbacks, and batched sync.
  */
object WoundApplications {

  private final case class PairedOptions(
      primary: WoundTargetData,
      pairedFraction: Option[Double],
      spill: Option[SpillImpactData],
      conditions: List[ConditionStepData],
      severityPolicy: WoundSeverityPolicy
  )

  def execute(
      player: ServerPlayer,
      source: DamageSource,
      damage: Double,
      rule: ClassifiedWoundRule
  ): Unit = {
    rule.applications.foreach {
      case application: ResolvedTypedWoundApplication =>
        executeTyped(
          DamageContext(player, source, damage, rule.ruleId, applicationType(application.data)),
          application
        )
      case application: ResolvedLegacyWoundApplication =>
        executeLegacy(
          DamageContext(
            player,
            source,
            damage,
            rule.ruleId,
            legacyApplicationType(source, application)
          ),
          application
        )
    }
  }

  private def applicationType(application: WoundApplicationData) = application match {
    case _: LocalizedApplicationData    => WoundApplicationData.LocalizedType
    case _: ScatterApplicationData      => WoundApplicationData.ScatterType
    case _: PairedImpactApplicationData => WoundApplicationData.PairedImpactType
  }

  private def legacyApplicationType(
      source: DamageSource,
      application: ResolvedLegacyWoundApplication
  ) = {
    if (source.is(DamageTypes.FALL)) WoundApplicationData.PairedImpactType
    else if (application.scatter) WoundApplicationData.ScatterType
    else WoundApplicationData.LocalizedType
  }

  private def executeTyped(
      context: DamageContext,
      resolved: ResolvedTypedWoundApplication
  ): Unit = {
    resolved.data match {
      case application: LocalizedApplicationData =>
        val part = pickTarget(context.player, context.source, application.target)
        applyContributions(context, part, context.damage, resolved.wounds, "localized")
      case application: ScatterApplicationData =>
        applyScatter(context, resolved.wounds, application)
      case application: PairedImpactApplicationData =>
        val options = pairedOptions(application, resolved.legacyFallRules, context.player)
        applyPairedImpact(context, resolved.wounds, options)
    }
  }

  private def executeLegacy(
      context: DamageContext,
      application: ResolvedLegacyWoundApplication
  ): Unit = {
    val wound = ResolvedWoundContribution(application.profileId, application.profile, 1.0)
    if (context.source.is(DamageTypes.FALL)) {
      val rules = matchingLegacyFallRules(application.fallRules, context.player)
        .getOrElse(FallRulesData.Fallback)
      val options = PairedOptions(
        WeightedTargetData(application.weights.getOrElse(WoundRuleData.DefaultWeights)),
        Some(rules.pairedFraction),
        Some(SpillImpactData(rules.secondaryThreshold, 1, rules.secondaryFraction)),
        legacyConditions(rules),
        WoundSeverityPolicy.FallImpact
      )
      applyPairedImpact(context, List(wound), options)
    } else if (application.scatter) {
      applyScatter(
        context,
        List(wound),
        ScatterApplicationData(List.empty, 2, 3)
      )
    } else {
      val target = application.forcedPart
        .map(FixedTargetData.apply)
        .getOrElse(
          HitLocationTargetData(application.weights.getOrElse(WoundRuleData.DefaultWeights))
        )
      val part = pickTarget(context.player, context.source, target)
      applyContributions(context, part, context.damage, List(wound), "localized")
    }
  }

  private def applyScatter(
      context: DamageContext,
      wounds: List[ResolvedWoundContribution],
      application: ScatterApplicationData
  ): Unit = {
    val count = application.minCount + context.player.getRandom.nextInt(
      application.maxCount - application.minCount + 1
    )
    val picked = scala.collection.mutable.LinkedHashSet.empty[BodyPart]
    while (picked.size < count) {
      picked += BodyPart.values(context.player.getRandom.nextInt(BodyPart.values.length))
    }
    picked.foreach { part =>
      applyContributions(context, part, context.damage / count, wounds, "scatter")
    }
  }

  private def applyPairedImpact(
      context: DamageContext,
      wounds: List[ResolvedWoundContribution],
      options: PairedOptions
  ): Unit = {
    val severity = woundSeverity(
      context.player,
      context.source,
      context.damage,
      options.severityPolicy
    )
    if (severity <= 0.0) return

    val primary = pickTarget(context.player, context.source, options.primary)
    val wounded = scala.collection.mutable.LinkedHashSet.empty[BodyPart]
    if (applyContributions(context, primary, severity, wounds, "primary")) {
      wounded += primary
    }

    options.pairedFraction.foreach { fraction =>
      pairedPart(primary).foreach { partner =>
        if (applyContributions(context, partner, severity * fraction, wounds, "paired")) {
          wounded += partner
        }
      }
    }

    options.spill.foreach { spill =>
      val selected = scala.collection.mutable.LinkedHashSet.empty[BodyPart]
      if (severity > spill.above) {
        var exhausted = false
        while (selected.size < spill.count && !exhausted) {
          val candidates = BodyPart.values.filterNot(part =>
            part == primary || wounded.contains(part) || selected.contains(part)
          )
          if (candidates.isEmpty) {
            exhausted = true
          } else {
            val secondary = candidates(context.player.getRandom.nextInt(candidates.length))
            selected += secondary
            if (
              applyContributions(context, secondary, severity * spill.fraction, wounds, "spill")
            ) {
              wounded += secondary
            }
          }
        }
      }
    }

    applyCondition(
      context,
      primary,
      conditionSeverity(context.player, severity, options.severityPolicy),
      options.conditions
    )
  }

  private def applyContributions(
      context: DamageContext,
      part: BodyPart,
      severity: Double,
      wounds: List[ResolvedWoundContribution],
      role: String
  ): Boolean = {
    var accepted = false
    wounds.foreach { wound =>
      val contributionSeverity = severity * wound.severityMultiplier
      if (
        contributionSeverity > 0.0 &&
        applyWound(context, part, contributionSeverity, wound, role)
      ) {
        accepted = true
      }
    }
    accepted
  }

  private def applyWound(
      context: DamageContext,
      part: BodyPart,
      damage: Double,
      wound: ResolvedWoundContribution,
      role: String
  ): Boolean = {
    val mitigated =
      ArmorProtection.mitigate(context.player, part, context.source, wound.profile)
    LimbInjuries(
      context.player,
      part,
      context.source,
      damage,
      pain = damage * mitigated.painPerPoint,
      ruleId = Some(context.ruleId),
      profileId = Some(wound.profileId),
      applicationType = Some(context.applicationType),
      role = Some(role)
    ) { (stats, effectiveDamage) =>
      if (mitigated.bleedRatePerWound > 0.0) {
        BleedingCalc.applyWound(
          stats,
          effectiveDamage * mitigated.skinPerPoint,
          mitigated.bleedRatePerWound,
          context.player.getRandom
        )
      } else if (mitigated.skinPerPoint > 0.0) {
        stats.skinIntegrity =
          (stats.skinIntegrity - effectiveDamage * mitigated.skinPerPoint).max(0.0)
      }
      stats.muscleHealth =
        (stats.muscleHealth - effectiveDamage * mitigated.musclePerPoint).max(0.0)
    }
  }

  private def pickTarget(
      player: ServerPlayer,
      source: DamageSource,
      target: WoundTargetData
  ): BodyPart = target match {
    case HitLocationTargetData(weights) => HitLocation.pick(player, source, weights)
    case FixedTargetData(part)          => part
    case WeightedTargetData(weights)    => HitLocation.weightedPart(player, weights)
  }

  private def pairedOptions(
      application: PairedImpactApplicationData,
      legacyRules: List[FallRulesData],
      player: ServerPlayer
  ): PairedOptions = {
    matchingLegacyFallRules(legacyRules, player)
      .map(rules =>
        PairedOptions(
          application.primary,
          Some(rules.pairedFraction),
          Some(SpillImpactData(rules.secondaryThreshold, 1, rules.secondaryFraction)),
          legacyConditions(rules),
          application.severityPolicy
        )
      )
      .getOrElse(
        PairedOptions(
          application.primary,
          application.paired.toScala.map(_.fraction),
          application.spill.toScala,
          application.conditionLadder,
          application.severityPolicy
        )
      )
  }

  private def matchingLegacyFallRules(
      rules: List[FallRulesData],
      player: ServerPlayer
  ): Option[FallRulesData] = {
    rules.find(_.entities.contains(player.typeHolder()))
  }

  private def legacyConditions(rules: FallRulesData): List[ConditionStepData] = List(
    ConditionStepData(
      LimbCondition.Fracture,
      rules.fractureThreshold,
      rules.fracturePain,
      java.util.Optional.of(rules.fractureBaseRecoveryTicks)
    ),
    ConditionStepData(
      LimbCondition.Dislocation,
      rules.dislocationThreshold,
      rules.dislocationPain,
      java.util.Optional.empty()
    )
  )

  private def woundSeverity(
      player: ServerPlayer,
      source: DamageSource,
      damage: Double,
      policy: WoundSeverityPolicy
  ): Double = policy match {
    case WoundSeverityPolicy.Standard   => damage
    case WoundSeverityPolicy.FallImpact =>
      val enchantmentProtection =
        EnchantmentHelper.getDamageProtection(player.level(), player, source)
      val afterEnchantments =
        if (enchantmentProtection > 0.0f) {
          CombatRules.getDamageAfterMagicAbsorb(damage.toFloat, enchantmentProtection).toDouble
        } else damage
      val enchantmentRatio = (afterEnchantments / damage).max(0.0)
      damage * enchantmentRatio * (1.0 - slotFormulaFactor(
        player,
        EquipmentSlot.FEET,
        CasualtiesBelowConfig.BootsCushionFormula
      ))
  }

  private def conditionSeverity(
      player: ServerPlayer,
      severity: Double,
      policy: WoundSeverityPolicy
  ): Double = policy match {
    case WoundSeverityPolicy.Standard   => severity
    case WoundSeverityPolicy.FallImpact =>
      severity * (1.0 - slotFormulaFactor(
        player,
        EquipmentSlot.LEGS,
        CasualtiesBelowConfig.LeggingsConditionProtectionFormula
      ))
  }

  private def applyCondition(
      context: DamageContext,
      part: BodyPart,
      severity: Double,
      steps: List[ConditionStepData]
  ): Unit = {
    val selected = steps.find(step => severity >= step.atLeast)
    if (selected.isEmpty) return

    val step = selected.get
    val current = CasualtiesBelowComponents.Body.get(context.player).stats(part)
    if (current.fractureRecoveryTicks.isDefined) return
    if (step.condition == LimbCondition.Dislocation && current.dislocated) return

    LimbInjuries(
      context.player,
      part,
      context.source,
      severity,
      Some(step.condition),
      step.pain,
      ruleId = Some(context.ruleId),
      applicationType = Some(context.applicationType),
      role = Some("condition")
    ) { (stats, effectiveDamage) =>
      step.condition match {
        case LimbCondition.Fracture =>
          val baseTicks = step.baseRecoveryTicks.orElseThrow().intValue()
          stats.fractureRecoveryTicks = Some((baseTicks * effectiveDamage / step.atLeast).toInt)
        case LimbCondition.Dislocation => stats.dislocated = true
      }
    }
  }

  private def pairedPart(part: BodyPart): Option[BodyPart] = part match {
    case BodyPart.LegLeft  => Some(BodyPart.LegRight)
    case BodyPart.LegRight => Some(BodyPart.LegLeft)
    case BodyPart.ArmLeft  => Some(BodyPart.ArmRight)
    case BodyPart.ArmRight => Some(BodyPart.ArmLeft)
    case _                 => None
  }

  private def slotFormulaFactor(
      player: Player,
      slot: EquipmentSlot,
      formula: FormulaConfigValue
  ): Double = {
    val stack = player.getItemBySlot(slot)
    if (stack.isEmpty) return 0.0
    val modifiers =
      stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
    val armor = modifiers.compute(Attributes.ARMOR, 0.0, slot)
    val toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot)
    if (armor <= 0.0 && toughness <= 0.0) 0.0
    else formula.evaluate(armor, toughness).max(0.0).min(1.0)
  }
}
