package dev.krysztal.casualtiesbelow.api

import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block

/** The mod's own vanilla-registry tags, used for fatal-damage passthrough, wound classification and
  * food discomfort tiers (see `discomfort.Discomfort`). Being datapack tags, all of them are
  * overridable/extendable by datapacks and other mods.
  */
object CasualtiesBelowTags {

  /** Damage sources whose vanilla player-health write remains authoritative. Datapacks and other
    * mods may extend this tag for damage that must retain vanilla fatal semantics.
    */
  val BypassesHealthRedirect: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelowApi.id("bypasses_health_redirect"))

  /** Damage sources classified as blast wounds. This joins vanilla's explosion tag with special
    * explosion-like damage types such as wither skulls.
    */
  val BlastSources: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelowApi.id("blast_sources"))

  /** Impact sources that use the paired-limb application. Kept narrower than vanilla's broad
    * `is_fall` tag, whose pearl and stalagmite damage retain separate wound profiles.
    */
  val FallImpacts: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelowApi.id("fall_impacts"))

  /** Damage types that supply the extreme direct-contact heat tier — lava by default (see
    * `temperature.HeatDamageContribution`). Datapacks may extend the tier.
    */
  val HeatExtreme: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelowApi.id("heat_extreme"))

  /** Damage types that supply the strong direct-contact heat tier: standing in fire and the burning
    * DOT (see `temperature.HeatDamageContribution`). Datapacks may extend the tier.
    */
  val HeatStrong: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelowApi.id("heat_strong"))

  /** Damage types that supply the normal contact heat tier: magma blocks (sneak-exempt by vanilla)
    * and campfires (see `temperature.HeatDamageContribution`). Datapacks may extend.
    */
  val HeatNormal: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelowApi.id("heat_normal"))

  /** Melee weapons that cut skin open (swords, axes by default). */
  val SharpMeleeItems: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("sharp_melee"))

  /** Plant materials that vanilla shelves dry into fiber cloth. */
  val DriesToFiberClothItems: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("dries_to_fiber_cloth"))

  /** Unarmed attackers whose hits are blunt slams rather than bites/scratches (slimes, golems,
    * ...). Bare-handed attackers not in this tag default to bite/scratch wounds.
    */
  val BluntMeleeEntities: TagKey[EntityType[?]] =
    TagKey.create(Registries.ENTITY_TYPE, CasualtiesBelowApi.id("blunt_melee"))

  /** Tier-1 discomfort food: edible but raw/starchy/sickly-sweet (raw fish, honey by the bottle).
    */
  val Discomfort1Food: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("discomfort_1"))

  /** Tier-2 discomfort food: clearly hard to swallow (raw meat, dried kelp, chorus fruit). */
  val Discomfort2Food: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("discomfort_2"))

  /** Tier-3 discomfort food: rotten, poisonous or not human food (rotten flesh, pufferfish). */
  val Discomfort3Food: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("discomfort_3"))

  /** Nourishing prepared soups, priced as a group by the matching `food_immune/tag` entry (see
    * `immune.FoodImmunity`). Packs extend the group by adding members to this tag.
    */
  val HealthySoupsItems: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("healthy_soups"))

  /** Carried items that supply the weak ambient heat tier: small open flames and strong heat
    * thematics (torches, lanterns, blaze powder). Ambient source tiers run 1 = weak, 2 = strong, 3 =
    * extreme/freezing (see `temperature`). Datapacks may extend the tier.
    */
  val HeatSource1Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("heat_source_1"))

  /** Carried items that supply the strong ambient heat tier: reliably-lit campfires, carried magma
    * and blaze rods. Datapacks may extend the tier.
    */
  val HeatSource2Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("heat_source_2"))

  /** Carried items that supply the extreme ambient heat tier (lava buckets). Datapacks may extend
    * the tier.
    */
  val HeatSource3Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("heat_source_3"))

  /** Carried items that supply the weak ambient cold tier: meltable ice, snow and carried water.
    * Datapacks may extend the tier.
    */
  val ColdSource1Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("cold_source_1"))

  /** Carried items that supply the strong ambient cold tier (packed ice). Datapacks may extend the
    * tier.
    */
  val ColdSource2Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("cold_source_2"))

  /** Carried items that supply the freezing ambient cold tier (powder snow buckets, blue ice).
    * Datapacks may extend the tier.
    */
  val ColdSource3Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelowApi.id("cold_source_3"))

  /** Biomes whose murky water washes dirtiness at a reduced rate (swamps by default; see
    * `hygiene.Dirtiness`).
    */
  val DirtyWaterBiomes: TagKey[Biome] =
    TagKey.create(Registries.BIOME, CasualtiesBelowApi.id("dirty_water"))

  /** Loose blocks whose breaking kicks up grime (dirt, sand, gravel, mud; see
    * `hygiene.DirtinessSources`).
    */
  val DiggableDirtyBlocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("diggable_dirty"))

  /** Blocks whose breaking raises no dust at all (leaves, wool, wood, glass, ice; see
    * `hygiene.DirtinessSources`). Membership in [[DiggableDirtyBlocks]] wins over this tag.
    */
  val DiggableDustlessBlocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("diggable_dustless"))

  /** Blocks that radiate the weak ambient heat tier: enclosed or small flames (lit furnaces,
    * torches, lanterns, candles). Membership is block-level, so consumers must check `LIT` for
    * state-dependent members themselves. Datapacks may extend the tier.
    */
  val HeatSource1Blocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("heat_source_1"))

  /** Blocks that radiate the strong ambient heat tier: open flames and hot floors (lit campfires,
    * fire, magma blocks). Datapacks may extend the tier.
    */
  val HeatSource2Blocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("heat_source_2"))

  /** Blocks that radiate the extreme ambient heat tier (lava, still or cauldron-contained).
    * Datapacks may extend the tier.
    */
  val HeatSource3Blocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("heat_source_3"))

  /** Blocks that radiate the weak ambient cold tier: meltable or transient cold (ice, snow layers,
    * frosted ice). Datapacks may extend the tier.
    */
  val ColdSource1Blocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("cold_source_1"))

  /** Blocks that radiate the strong ambient cold tier: permanent ice and solid snow. Datapacks may
    * extend the tier.
    */
  val ColdSource2Blocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("cold_source_2"))

  /** Blocks that radiate the freezing ambient cold tier: powder snow, loose or cauldron-contained.
    * Datapacks may extend the tier.
    */
  val ColdSource3Blocks: TagKey[Block] =
    TagKey.create(Registries.BLOCK, CasualtiesBelowApi.id("cold_source_3"))
}
