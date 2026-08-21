package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageType

import dev.krysztal.casualtiesbelow.damage.CasualtiesBelowDamageTypes

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

/** Generates the vanilla damage-type tags for the mod's own damage types. */
final class DamageTypeTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[DamageType](output, Registries.DAMAGE_TYPE, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    // Bleeding out and sepsis are not preventable by gear or effects.
    val bloodLoss = CasualtiesBelowDamageTypes.BloodLoss
    val sepsis = CasualtiesBelowDamageTypes.Sepsis
    builder(DamageTypeTags.BYPASSES_ARMOR).add(bloodLoss, sepsis)
    builder(DamageTypeTags.BYPASSES_EFFECTS).add(bloodLoss, sepsis)
    builder(DamageTypeTags.BYPASSES_ENCHANTMENTS).add(bloodLoss, sepsis)
    builder(DamageTypeTags.BYPASSES_RESISTANCE).add(bloodLoss, sepsis)
  }
}
