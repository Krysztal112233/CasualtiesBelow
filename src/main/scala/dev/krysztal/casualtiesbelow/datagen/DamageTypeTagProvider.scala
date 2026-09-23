package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags

/** Generates the vanilla damage-type tags for the mod's own damage types. */
final class DamageTypeTagProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricTagsProvider[DamageType](output, Registries.DAMAGE_TYPE, registries) {

  override protected def addTags(registries: HolderLookup.Provider): Unit = {
    // Physiological fatal checks are not preventable by gear or effects.
    val bloodLoss = CasualtiesBelowDamageTypes.BloodLoss
    val sepsis = CasualtiesBelowDamageTypes.Sepsis
    val hypoxia = CasualtiesBelowDamageTypes.Hypoxia
    val starvation = CasualtiesBelowDamageTypes.Starvation
    builder(DamageTypeTags.BYPASSES_ARMOR).add(bloodLoss, sepsis, hypoxia, starvation)
    builder(DamageTypeTags.BYPASSES_EFFECTS).add(bloodLoss, sepsis, hypoxia, starvation)
    builder(DamageTypeTags.BYPASSES_ENCHANTMENTS).add(bloodLoss, sepsis, hypoxia, starvation)
    builder(DamageTypeTags.BYPASSES_RESISTANCE).add(bloodLoss, sepsis, hypoxia, starvation)
    // Forced deaths and physiological fatal checks must not be delayed by an earlier hurt's
    // invulnerability window, including the Float.MaxValue hit left behind by a totem rescue.
    builder(DamageTypeTags.BYPASSES_COOLDOWN)
      .addTag(DamageTypeTags.BYPASSES_INVULNERABILITY)
      .add(bloodLoss, sepsis, hypoxia, starvation)

    // Forced vanilla deaths and the mod's physiological fatal sources must reach health zero.
    builder(CasualtiesBelowTags.BypassesHealthRedirect)
      .addTag(DamageTypeTags.BYPASSES_INVULNERABILITY)
      .add(DamageTypes.OUTSIDE_BORDER, bloodLoss, sepsis, hypoxia, starvation)

    // Sepsis is terminal; the other physiological sources remain eligible for death protection.
    builder(DamageTypeTags.BYPASSES_INVULNERABILITY).add(sepsis)

    // HolderSet codecs cannot represent a union of one tag and direct entries. Give the blast
    // wound rule one semantic tag that datapacks can extend.
    builder(CasualtiesBelowTags.BlastSources)
      .forceAddTag(DamageTypeTags.IS_EXPLOSION)
      .add(DamageTypes.WITHER_SKULL)

    // `#minecraft:is_fall` also includes pearl and stalagmite damage, which deliberately use
    // localized prick/pierce rules. This semantic tag starts with exact fall impact only.
    builder(CasualtiesBelowTags.FallImpacts).add(DamageTypes.FALL)

    // Damage-type heat tiers map to config rates and are datapack-extensible. Fireball impacts are
    // omitted as transient hits; lightning is explicitly classified as extreme heat.
    builder(CasualtiesBelowTags.HeatExtreme).add(DamageTypes.LAVA).add(DamageTypes.LIGHTNING_BOLT)
    builder(CasualtiesBelowTags.HeatStrong).add(DamageTypes.IN_FIRE, DamageTypes.ON_FIRE)
    builder(CasualtiesBelowTags.HeatNormal)
      .add(DamageTypes.HOT_FLOOR, DamageTypes.CAMPFIRE)
  }
}
