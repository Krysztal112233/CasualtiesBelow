package dev.krysztal.casualtiesbelow.damage

import net.minecraft.core.component.DataComponents
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.ItemAttributeModifiers

import dev.krysztal.casualtiesbelow.component.BodyPart
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
    if (armor <= 0.0 && toughness <= 0.0) return profile

    val config = CasualtiesBelowConfig
    val skinFactor = config.ArmorSkinFactorFormula
      .evaluate(armor, toughness)
      .max(0.0)
      .min(1.0)
    val muscleFactor = config.ArmorMuscleFactorFormula
      .evaluate(armor, toughness, skinFactor)
      .max(0.0)
      .min(1.0)

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
