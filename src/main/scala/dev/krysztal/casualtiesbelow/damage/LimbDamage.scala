package dev.krysztal.casualtiesbelow.damage

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.ItemAttributeModifiers

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbCondition
import dev.krysztal.casualtiesbelow.api.data.FallRulesData
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.wound.HitLocation
import dev.krysztal.casualtiesbelow.api.wound.WoundProfile
import dev.krysztal.casualtiesbelow.api.wound.WoundProfiles
import dev.krysztal.casualtiesbelow.bleeding.BleedingCalc
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.pain.PainCalc
import dev.krysztal.casualtiesbelow.progression.StarvationProgression

/** Attributes incoming damage to body parts.
  *
  * Fall damage is attributed in [[onFallDamage]] (hooked from `LivingEntity.causeFallDamage`, where
  * the impact context — fall distance, damage modifier, formula output — is available). Other
  * damage goes through [[afterDamage]] (Fabric's `ServerLivingEntityEvents.AFTER_DAMAGE`), which
  * carries no hit-location information: the affected part is guessed from hit geometry (see
  * [[HitLocation]]). What kind of wound a hit inflicts — bite, cut, blunt, pierce, burn, blast — is
  * classified from the damage source by [[WoundProfiles]] (rule-driven; see the
  * `casualtiesbelow:wound_rule` registry).
  */
object LimbDamage {

  def register(): Unit = {
    ServerLivingEntityEvents.AFTER_DAMAGE.register(afterDamage)
  }

  /** Invoked from `LivingEntity.causeFallDamage` (see `LivingEntityMixin`). Attributes fall damage
    * to the legs when the impact actually dealt damage (`damaged` is the method's return value:
    * false for creative flight, slime-block landings, zero-damage falls, etc.).
    *
    * Server-side and players only: the body component exists on players and is authoritative on the
    * server.
    *
    * Note: `fallDistance` here is the raw method argument; `causeFallDamage` internally shortens it
    * during post-impulse grace (wind charges, mace smashes). In that edge case the limb attribution
    * slightly overshoots — accepted for now.
    */
  def onFallDamage(
      entity: LivingEntity,
      fallDistance: Double,
      damageModifier: Float,
      source: DamageSource,
      damaged: Boolean
  ): Unit = {
    val isEligibleFall = damaged && isFallDamage(source) &&
      entity.isInstanceOf[ServerPlayer] && entity.level().isInstanceOf[ServerLevel]
    if (!isEligibleFall) return

    val player = entity.asInstanceOf[ServerPlayer]
    if (player.isCreative || player.isSpectator) return

    // Players always use the custom formula (see FallDamageFormula.appliesTo), so the
    // severity can be recomputed deterministically from the same inputs.
    val damage = FallDamageFormula
      .calculateCustom(player, fallDistance, damageModifier)
      .toDouble
    if (damage > 0.0) {
      // Sync is automatic: applied injuries mark the player dirty, flushed at tick end
      // (see LimbInjuries.register).
      attributeFallDamage(player, source, damage)
    }

  }

  /** Invoked after a living entity takes damage, before armor/enchantment reduction, and only when
    * the entity survives. Server-side only (`LivingEntity.hurtServer`).
    *
    * Fall damage is skipped here: it is already attributed with richer context in [[onFallDamage]].
    */
  private val afterDamage: ServerLivingEntityEvents.AfterDamage =
    (entity, source, _, damageTaken, blocked) => onAfterDamage(entity, source, damageTaken, blocked)

  private def onAfterDamage(
      entity: LivingEntity,
      source: DamageSource,
      damageTaken: Float,
      blocked: Boolean
  ): Unit = {
    if (!entity.isInstanceOf[ServerPlayer]) return // Components are server-player authoritative.

    val player = entity.asInstanceOf[ServerPlayer]
    if (player.isCreative || player.isSpectator) return
    if (isFallDamage(source)) return
    if (blocked) return
    if (damageTaken <= 0) return

    StarvationProgression.onAfterDamage(player, source, damageTaken)
    val damage = damageTaken.toDouble
    WoundProfiles.classify(player.level(), player, source) match {
      case None                         => ()
      case Some(wound) if wound.scatter =>
        applyScatter(player, source, damage, wound.profile)
      case Some(wound) =>
        val part = wound.forcedPart.getOrElse(HitLocation.pick(player, source))
        applyWound(player, part, source, damage, wound.profile)
    }
  }

  /** Whether the source is fall damage. Vanilla's `IS_FALL` tag also covers ender pearls and
    * stalagmites.
    */
  def isFallDamage(source: DamageSource): Boolean =
    source.is(DamageTypeTags.IS_FALL)

  /** Applies a wound profile to one located part: skin loss (with bleeding if the profile bleeds,
    * capped linearly by the post-hit skin integrity), muscle loss, and pain proportional to the
    * damage. Sync is automatic: applied injuries mark the player dirty, flushed at tick end (see
    * [[LimbInjuries.register]]).
    */
  private def applyWound(
      player: Player,
      part: BodyPart,
      source: DamageSource,
      damage: Double,
      profile: WoundProfile
  ): Unit = {
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

  /** Maps fall damage (in half-hearts, as produced by [[FallDamageFormula]]) to leg injuries:
    *
    *   - worn boots cushion the whole impact (the `[fall]` cushion formula)
    *   - any damage: both legs lose muscle health and gain pain
    *   - above the registry scrape threshold: skin scrape and external bleeding capped by skin
    *     damage
    *   - at the registry dislocation threshold: one random leg is dislocated
    *   - at the registry fracture threshold: one random leg fractures instead, with recovery time
    *     scaling with the damage; worn leggings blunt the impact for these condition rolls (the
    *     `[fall]` protection formula)
    *
    * Impact and condition values come from the matching `fall_rules` entry; equipment factors stay
    * in config formulas.
    */
  private def attributeFallDamage(
      player: ServerPlayer,
      source: DamageSource,
      damage: Double
  ): Unit = {
    val cushioned = damage * (1.0 - bootsCushion(player))
    if (cushioned <= 0.0) return

    val rules = GameplayDataLookup.fallRules(player.registryAccess(), player)
    val severeLeg =
      if (player.getRandom.nextBoolean()) BodyPart.LegLeft else BodyPart.LegRight

    // General impact on both legs, then the severe injury on the randomly picked leg — the severe
    // leg effectively suffers two injuries, each cancellable on its own.
    BodyPart.Legs.foreach { leg =>
      applyFallInjury(player, leg, source, cushioned, rules)
    }
    applySevereFallInjury(
      player,
      severeLeg,
      source,
      cushioned * (1.0 - leggingsProtection(player)),
      rules
    )
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

  /** Fall impact rules for one leg: muscle health, impact pain ([[PainCalc.onFall]] via the injury
    * context), skin scrape, and bleeding capped linearly by the post-impact skin integrity.
    * Application mechanics (context, event, commit) live in [[LimbInjuries.apply]].
    */
  private def applyFallInjury(
      player: ServerPlayer,
      leg: BodyPart,
      source: DamageSource,
      damage: Double,
      rules: FallRulesData
  ): Unit = {
    LimbInjuries(player, leg, source, damage, pain = PainCalc.onFall(player, damage)) {
      (stats, effectiveDamage) =>
        stats.muscleHealth =
          (stats.muscleHealth - effectiveDamage * rules.muscleDamagePerPoint).max(0.0)

        if (effectiveDamage > rules.scrapeThreshold) {
          BleedingCalc.applyWound(
            stats,
            (effectiveDamage - rules.scrapeThreshold) * rules.scrapePerPoint,
            rules.fallBleedingRatePerWound,
            player.getRandom
          )
        }
    }
  }

  /** Severe fall injury rules for the picked leg: fracture above the registry fracture threshold,
    * dislocation above its dislocation threshold — discrete condition onsets carrying a fixed
    * one-time pain grant ([[PainCalc.onConditionOnset]]), independent of impact pain.
    *
    * Onset guards prevent re-granting: an already-fractured leg takes no new condition, and an
    * already-dislocated leg is not re-dislocated (a dislocated leg can still progress to a
    * fracture).
    */
  private def applySevereFallInjury(
      player: ServerPlayer,
      leg: BodyPart,
      source: DamageSource,
      damage: Double,
      rules: FallRulesData
  ): Unit = {
    if (damage < rules.dislocationThreshold) return

    val current = CasualtiesBelowComponents.Body.get(player).stats(leg)
    if (current.fractureRecoveryTicks.isDefined) return

    val condition =
      if (damage >= rules.fractureThreshold) {
        LimbCondition.Fracture
      } else {
        if (current.dislocated) return
        LimbCondition.Dislocation
      }

    LimbInjuries(
      player,
      leg,
      source,
      damage,
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
