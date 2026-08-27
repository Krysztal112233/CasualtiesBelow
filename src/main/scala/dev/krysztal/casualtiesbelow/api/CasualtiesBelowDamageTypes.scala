package dev.krysztal.casualtiesbelow.api

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.level.Level

import dev.krysztal.casualtiesbelow.CasualtiesBelow

/** The mod's own damage types, registered as datapack JSON under
  * `data/casualtiesbelow/damage_type/`.
  */
object CasualtiesBelowDamageTypes {

  /** Fatal blood loss, dealt when the blood volume reaches zero. Tagged `bypasses_armor`,
    * `bypasses_effects`, `bypasses_enchantments`, `bypasses_resistance` and `bypasses_cooldown`
    * (see `data/minecraft/tags/damage_type/`): bleeding out is not preventable or delayed by gear
    * or hurt cooldown, but vanilla death protection may still provide one rescue window.
    */
  val BloodLoss: ResourceKey[DamageType] =
    ResourceKey.create(Registries.DAMAGE_TYPE, CasualtiesBelow.ofIdentifier("blood_loss"))

  /** Fatal sepsis, dealt when sepsis compresses the effective blood volume cap to zero. Same
    * gear/effect/cooldown bypasses as [[BloodLoss]], plus `bypasses_invulnerability`, so death
    * protection cannot rescue it.
    */
  val Sepsis: ResourceKey[DamageType] =
    ResourceKey.create(Registries.DAMAGE_TYPE, CasualtiesBelow.ofIdentifier("sepsis"))

  /** A sourceless blood-loss damage instance. */
  def bloodLoss(level: Level): DamageSource =
    new DamageSource(
      level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BloodLoss)
    )

  /** A sourceless sepsis damage instance. */
  def sepsis(level: Level): DamageSource =
    new DamageSource(
      level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(Sepsis)
    )
}
