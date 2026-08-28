package dev.krysztal.casualtiesbelow.damage

import scala.jdk.OptionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.ItemAttributeModifiers

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.api.wound.WoundProfile
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Armor as a wound barrier: armor covering the struck body part transforms the wound profile —
  * skin damage and bleeding are mostly blocked (teeth, claws and blades fail to break skin), while
  * part of the impact still transmits to muscle as blunt trauma, and pain with it.
  *
  * Coverage: helmets protect the head, chestplates the torso and arms, leggings the legs; boots map
  * to nothing (feet are not modeled). Protection strength is read from the item's own
  * [[Attributes.ARMOR]] / [[Attributes.ARMOR_TOUGHNESS]] modifiers, so modded armor works out of
  * the box and pieces with no armor value (elytra, pumpkins) protect nothing. Damage sources
  * bypassing armor in vanilla ([[DamageTypeTags.BYPASSES_ARMOR]]) skip this entirely.
  *
  * Note the knock-on effect this is designed around: blocking skin damage keeps wounds below the
  * infection onset threshold, so wearing armor prevents infections without any direct coupling to
  * the immune system.
  */
object ArmorProtection {

  /** [profile] as mitigated by the armor covering [part] on [player]; unchanged when the part is
    * uncovered, the covering piece has no armor value, or the damage bypasses armor.
    */
  def mitigate(
      player: Player,
      part: BodyPart,
      source: DamageSource,
      profile: WoundProfile
  ): WoundProfile = {
    if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return profile
    val slot = coveringSlot(part)
    if (slot.isEmpty) return profile
    val stack = player.getItemBySlot(slot.get)
    if (stack.isEmpty) return profile

    val modifiers =
      stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
    val armor = modifiers.compute(Attributes.ARMOR, 0.0, slot.get)
    val toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot.get)

    val config = CasualtiesBelowConfig

    // A datapack override matches by item identity and replaces the config formula for the factors
    // it defines — including for pieces with zero armor value, which the fallback path skips.
    GameplayDataLookup.armorProtection(stack) match {
      case Some(entry) =>
        val skinFactor = entry.skinFactor.toScala
          .flatMap(_.evaluate(armor, toughness))
          .getOrElse(config.ArmorSkinFactorFormula.evaluate(armor, toughness))
          .max(0.0)
          .min(1.0)
        val muscleFactor = entry.muscleFactor.toScala
          .flatMap(_.evaluate(armor, toughness, skinFactor))
          .getOrElse(config.ArmorMuscleFactorFormula.evaluate(armor, toughness, skinFactor))
          .max(0.0)
          .min(1.0)
        applyFactors(profile, skinFactor, muscleFactor)
      case None =>
        if (armor <= 0.0 && toughness <= 0.0) return profile
        val skinFactor = config.ArmorSkinFactorFormula
          .evaluate(armor, toughness)
          .max(0.0)
          .min(1.0)
        val muscleFactor = config.ArmorMuscleFactorFormula
          .evaluate(armor, toughness, skinFactor)
          .max(0.0)
          .min(1.0)
        applyFactors(profile, skinFactor, muscleFactor)
    }
  }

  private def applyFactors(
      profile: WoundProfile,
      skinFactor: Double,
      muscleFactor: Double
  ): WoundProfile = {
    WoundProfile(
      profile.skinPerPoint * skinFactor,
      profile.musclePerPoint * muscleFactor,
      profile.bleedRatePerWound * skinFactor,
      profile.painPerPoint * muscleFactor
    )
  }

  /** The armor slot covering [part]: helmets cover the head, chestplates the torso and arms,
    * leggings the legs.
    */
  private def coveringSlot(part: BodyPart): Option[EquipmentSlot] = part match {
    case BodyPart.Head                                         => Some(EquipmentSlot.HEAD)
    case BodyPart.Torso | BodyPart.ArmLeft | BodyPart.ArmRight => Some(EquipmentSlot.CHEST)
    case BodyPart.LegLeft | BodyPart.LegRight                  => Some(EquipmentSlot.LEGS)
  }
}
