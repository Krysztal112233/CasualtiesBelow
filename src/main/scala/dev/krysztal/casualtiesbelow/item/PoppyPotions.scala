package dev.krysztal.casualtiesbelow.item

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.alchemy.Potion

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi

/** Internal no-effect carrier potion owned by Casualties: Below.
  *
  * Vanilla's brewing registry only accepts potion containers whose potion contents are non-empty,
  * so the poppy liquids must carry some potion while they reuse the brewing stand. Carrying water
  * would additionally accept every vanilla water-base mix; no vanilla potion mix uses this
  * dedicated carrier as an input, leaving glowstone refinement as the only built-in brew.
  */
private[casualtiesbelow] object PoppyPotions {
  private[item] val CarrierKey: ResourceKey[Potion] =
    ResourceKey.create(Registries.POTION, CasualtiesBelowApi.id("poppy_carrier"))

  /** Holder for the carrier, bound on first use and no later than [[register]]. Registration must
    * stay lazy so frozen-registry unit tests can reference the key without registering content.
    */
  private[item] lazy val PoppyCarrier: Holder[Potion] =
    Registry.registerForHolder(BuiltInRegistries.POTION, CarrierKey, new Potion("poppy_carrier"))

  /** Eagerly binds the carrier holder; must run before the poppy liquid items initialize. */
  def register(): Unit = {
    PoppyCarrier
    ()
  }
}
