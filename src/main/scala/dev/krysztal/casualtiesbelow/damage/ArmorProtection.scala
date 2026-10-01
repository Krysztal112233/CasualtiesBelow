package dev.krysztal.casualtiesbelow.damage

import scala.jdk.OptionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.ItemAttributeModifiers

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.data.schema.FormulaSource
import dev.krysztal.casualtiesbelow.data.schema.WoundProfile
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

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
      profile: WoundProfile,
      gameplayData: GameplayDataStore
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

    // A datapack override matches by item identity and replaces the fixed formula for the factors
    // it defines — including for pieces with zero armor value, which the fallback path skips.
    val entry = GameplayDataLookup.armorProtection(stack, gameplayData)
    if (entry.isEmpty && armor <= 0.0 && toughness <= 0.0) return profile

    val skinFactor = factor(
      entry.flatMap(_.skinFactor.toScala),
      Consts.Armor.ArmorSkinFactorFormula,
      armor,
      toughness
    )
    val muscleFactor = factor(
      entry.flatMap(_.muscleFactor.toScala),
      Consts.Armor.ArmorMuscleFactorFormula,
      armor,
      toughness,
      skinFactor
    )
    applyFactors(profile, skinFactor, muscleFactor)
  }

  /** One protection factor, clamped to [0, 1]: the datapack override formula when present and
    * evaluable, else the fixed fallback formula.
    */
  private def factor(
      overrideFormula: Option[FormulaSource],
      fallback: Consts.FixedFormula,
      values: Double*
  ): Double = {
    overrideFormula
      .flatMap(_.evaluate(values*))
      .getOrElse(fallback.evaluate(values*))
      .boundedFraction
  }

  private def applyFactors(
      profile: WoundProfile,
      skinFactor: Double,
      muscleFactor: Double
  ): WoundProfile = {
    profile.copy(
      skinPerPoint = profile.skinPerPoint * skinFactor,
      musclePerPoint = profile.musclePerPoint * muscleFactor,
      bleedRatePerWound = profile.bleedRatePerWound * skinFactor,
      painPerPoint = profile.painPerPoint * muscleFactor
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
