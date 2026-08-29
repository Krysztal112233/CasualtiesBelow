package dev.krysztal.casualtiesbelow.damage

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.enchantment.EnchantmentHelper

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.data.FallRulesData
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData
import dev.krysztal.casualtiesbelow.api.wound.HitLocation
import dev.krysztal.casualtiesbelow.api.wound.Wound
import dev.krysztal.casualtiesbelow.api.wound.WoundProfile
import dev.krysztal.casualtiesbelow.api.wound.WoundProfiles
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.pain.PainCalc
import dev.krysztal.casualtiesbelow.progression.StarvationProgression

/** Attributes incoming damage to body parts from Fabric's post-damage event. Damage kinds are
  * classified by datapack wound rules, then every standard wound flows through [[applyWound]].
  * Exact `minecraft:fall` impacts additionally use their data-driven primary location, mitigation,
  * spill, and condition rules; other sources use hit geometry (see [[HitLocation]]).
  */
object LimbDamage {

  def register(): Unit = {
    ServerLivingEntityEvents.AFTER_DAMAGE.register(afterDamage)
  }

  /** Invoked after a living entity takes damage, before armor/enchantment reduction, and only when
    * the entity survives. Server-side only (`LivingEntity.hurtServer`).
    */
  private val afterDamage: ServerLivingEntityEvents.AfterDamage =
    (entity, source, _, damageTaken, blocked) => onAfterDamage(entity, source, damageTaken, blocked)

  private def onAfterDamage(
      entity: LivingEntity,
      source: DamageSource,
      damageTaken: Float,
      blocked: Boolean
  ): Unit = {
    // Components are server-player authoritative.
    val player = entity match {
      case player: ServerPlayer => player
      case _                    => return
    }
    if (player.isCreative || player.isSpectator) return
    if (blocked) return
    if (damageTaken <= 0) return

    StarvationProgression.onAfterDamage(player, source, damageTaken)
    val damage = damageTaken.toDouble
    WoundProfiles.classify(player.level(), player, source) match {
      case None                                       => ()
      case Some(wound) if source.is(DamageTypes.FALL) =>
        applyFall(player, source, damage, wound)
      case Some(wound) if wound.scatter =>
        applyScatter(player, source, damage, wound.profile)
      case Some(wound) =>
        val part = wound.forcedPart.getOrElse(
          HitLocation.pick(player, source, wound.weights.getOrElse(WoundRuleData.DefaultWeights))
        )
        applyWound(player, part, source, damage, wound.profile)
    }
  }

  /** Applies a wound profile to one located part: skin loss (with bleeding if the profile bleeds,
    * capped linearly by the post-hit skin integrity), muscle loss, and pain proportional to the
    * damage. Returns whether the central injury event accepted the wound. Sync is automatic:
    * applied injuries mark the player dirty, flushed at tick end (see [[LimbInjuries.register]]).
    */
  private def applyWound(
      player: Player,
      part: BodyPart,
      source: DamageSource,
      damage: Double,
      profile: WoundProfile
  ): Boolean = {
    val mitigated = ArmorProtection.mitigate(player, part, source, profile)
    LimbInjuries(player, part, source, damage, pain = damage * mitigated.painPerPoint) {
      (stats, effectiveDamage) =>
        if (mitigated.bleedRatePerWound > 0.0) {
          BleedingCalc.applyWound(
            stats,
            effectiveDamage * mitigated.skinPerPoint,
            mitigated.bleedRatePerWound,
            player.getRandom
          )
        } else if (mitigated.skinPerPoint > 0.0) {
          stats.skinIntegrity =
            (stats.skinIntegrity - effectiveDamage * mitigated.skinPerPoint).max(0.0)
        }
        stats.muscleHealth =
          (stats.muscleHealth - effectiveDamage * mitigated.musclePerPoint).max(0.0)
    }
  }

  /** Explosion shrapnel: the damage is split evenly across 2–3 random body parts, each taking a
    * full wound of its own (each cancellable via the injury event on its own).
    */
  private def applyScatter(
      player: Player,
      source: DamageSource,
      damage: Double,
      profile: WoundProfile
  ): Unit = {
    val count = 2 + player.getRandom.nextInt(2)
    val picked = scala.collection.mutable.LinkedHashSet.empty[BodyPart]
    while (picked.size < count) {
      picked += BodyPart.values(player.getRandom.nextInt(BodyPart.values.length))
    }

    picked.foreach { part =>
      applyWound(player, part, source, damage / count, profile)
    }
  }

  /** Attributes an exact `minecraft:fall` through the classified profile. Vanilla magic mitigation
    * (including Feather Falling and Protection) is applied first, then worn boots cushion the
    * remaining severity. One weighted primary part always receives the standard wound, a paired
    * limb receives a fractional matching wound, and sufficiently severe impacts spill onto one
    * otherwise unwounded part. Conditions are independently applied to the primary part only.
    */
  private def applyFall(
      player: ServerPlayer,
      source: DamageSource,
      damage: Double,
      wound: Wound
  ): Unit = {
    val enchantmentProtection =
      EnchantmentHelper.getDamageProtection(player.level(), player, source)
    val afterEnchantments =
      if (enchantmentProtection > 0.0f) {
        CombatRules.getDamageAfterMagicAbsorb(damage.toFloat, enchantmentProtection).toDouble
      } else {
        damage
      }
    val enchantmentRatio = (afterEnchantments / damage).max(0.0)
    val severity = damage * enchantmentRatio * (1.0 - bootsCushion(player))
    if (severity <= 0.0) return

    val rules = GameplayDataLookup.fallRules(player)
    val primary = HitLocation.weightedPart(
      player,
      wound.weights.getOrElse(WoundRuleData.DefaultWeights)
    )
    val wounded = scala.collection.mutable.LinkedHashSet.empty[BodyPart]
    if (applyWound(player, primary, source, severity, wound.profile)) {
      wounded += primary
    }
    pairedPart(primary).foreach { partner =>
      if (
        applyWound(
          player,
          partner,
          source,
          severity * rules.pairedFraction,
          wound.profile
        )
      ) {
        wounded += partner
      }
    }

    if (severity > rules.secondaryThreshold) {
      val candidates = BodyPart.values.filterNot(part => part == primary || wounded.contains(part))
      if (candidates.nonEmpty) {
        val secondary = candidates(player.getRandom.nextInt(candidates.length))
        if (
          applyWound(
            player,
            secondary,
            source,
            severity * rules.secondaryFraction,
            wound.profile
          )
        ) {
          wounded += secondary
        }
      }
    }

    applyFallCondition(
      player,
      primary,
      source,
      severity * (1.0 - leggingsProtection(player)),
      rules
    )
  }

  private def pairedPart(part: BodyPart): Option[BodyPart] = part match {
    case BodyPart.LegLeft  => Some(BodyPart.LegRight)
    case BodyPart.LegRight => Some(BodyPart.LegLeft)
    case BodyPart.ArmLeft  => Some(BodyPart.ArmRight)
    case BodyPart.ArmRight => Some(BodyPart.ArmLeft)
    case _                 => None
  }

  /** Fraction of the fall impact absorbed by worn boots (0 without). */
  private def bootsCushion(player: Player): Double = {
    slotFormulaFactor(player, EquipmentSlot.FEET, CasualtiesBelowConfig.BootsCushionFormula)
  }

  /** Fraction of the fall impact ignored for dislocation/fracture rolls, from worn leggings. */
  private def leggingsProtection(player: Player): Double = {
    slotFormulaFactor(
      player,
      EquipmentSlot.LEGS,
      CasualtiesBelowConfig.LeggingsConditionProtectionFormula
    )
  }

  /** Evaluates a `[fall]` equipment formula against the armor/toughness of the piece in [slot],
    * clamped to [0, 1]; 0 when the slot is empty or the piece has neither attribute.
    */
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
    if (armor <= 0.0 && toughness <= 0.0) {
      0.0
    } else {
      formula.evaluate(armor, toughness).max(0.0).min(1.0)
    }
  }

  /** Fall-specific fracture/dislocation ladder on the primary part. Threshold comparison uses the
    * leggings-blunted primary severity. The condition onset remains a separate central injury event
    * with its fixed one-time pain grant; spill wounds never call this path.
    */
  private def applyFallCondition(
      player: ServerPlayer,
      part: BodyPart,
      source: DamageSource,
      severity: Double,
      rules: FallRulesData
  ): Unit = {
    if (severity < rules.dislocationThreshold) return

    val current = CasualtiesBelowComponents.Body.get(player).stats(part)
    if (current.fractureRecoveryTicks.isDefined) return

    val condition =
      if (severity >= rules.fractureThreshold) {
        LimbCondition.Fracture
      } else {
        if (current.dislocated) return
        LimbCondition.Dislocation
      }

    LimbInjuries(
      player,
      part,
      source,
      severity,
      Some(condition),
      PainCalc.onConditionOnset(player, condition)
    ) { (stats, effectiveDamage) =>
      if (condition == LimbCondition.Fracture) {
        stats.fractureRecoveryTicks = Some(
          (rules.fractureBaseRecoveryTicks
            .intValue() * effectiveDamage / rules.fractureThreshold).toInt
        )
      } else {
        stats.dislocated = true
      }
    }
  }
}
