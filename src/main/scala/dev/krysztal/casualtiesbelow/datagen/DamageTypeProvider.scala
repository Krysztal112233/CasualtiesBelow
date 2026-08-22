package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes

/** Emits the mod's dynamic-registry entries (currently: damage types) registered in
  * [[CasualtiesBelowDataGenerator.buildRegistry]].
  */
final class DamageTypeProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricDynamicRegistryProvider(output, registries) {

  override protected def configure(
      registries: HolderLookup.Provider,
      entries: FabricDynamicRegistryProvider.Entries
  ): Unit = {
    entries.add(
      registries.lookupOrThrow(Registries.DAMAGE_TYPE),
      CasualtiesBelowDamageTypes.BloodLoss
    )
    entries.add(
      registries.lookupOrThrow(Registries.DAMAGE_TYPE),
      CasualtiesBelowDamageTypes.Sepsis
    )
  }

  override def getName(): String = "Damage Types"
}
