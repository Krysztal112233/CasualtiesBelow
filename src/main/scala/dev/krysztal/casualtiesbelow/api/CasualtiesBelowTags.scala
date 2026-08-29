package dev.krysztal.casualtiesbelow.api

import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

import dev.krysztal.casualtiesbelow.CasualtiesBelow

/** The mod's own vanilla-registry tags, used for fatal-damage passthrough, wound classification
  * (see [[dev.krysztal.casualtiesbelow.api.wound.WoundProfiles]]) and food discomfort tiers (see
  * `discomfort.Discomfort`). Being datapack tags, all of them are overridable/extendable by
  * datapacks and other mods.
  */
object CasualtiesBelowTags {

  /** Damage sources whose vanilla player-health write remains authoritative. Datapacks and other
    * mods may extend this tag for damage that must retain vanilla fatal semantics.
    */
  val BypassesHealthRedirect: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelow.ofIdentifier("bypasses_health_redirect"))

  /** Damage sources classified as blast wounds. This joins vanilla's explosion tag with special
    * explosion-like damage types such as wither skulls.
    */
  val BlastSources: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelow.ofIdentifier("blast_sources"))

  /** Impact sources that use the paired-limb application. Kept narrower than vanilla's broad
    * `is_fall` tag, whose pearl and stalagmite damage retain separate wound profiles.
    */
  val FallImpacts: TagKey[DamageType] =
    TagKey.create(Registries.DAMAGE_TYPE, CasualtiesBelow.ofIdentifier("fall_impacts"))

  /** Melee weapons that cut skin open (swords, axes by default). */
  val SharpMeleeItems: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelow.ofIdentifier("sharp_melee"))

  /** Unarmed attackers whose hits are blunt slams rather than bites/scratches (slimes, golems,
    * ...). Bare-handed attackers not in this tag default to bite/scratch wounds.
    */
  val BluntMeleeEntities: TagKey[EntityType[?]] =
    TagKey.create(Registries.ENTITY_TYPE, CasualtiesBelow.ofIdentifier("blunt_melee"))

  /** Tier-1 discomfort food: edible but raw/starchy/sickly-sweet (raw fish, honey by the bottle).
    */
  val Discomfort1Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelow.ofIdentifier("discomfort_1"))

  /** Tier-2 discomfort food: clearly hard to swallow (raw meat, dried kelp, chorus fruit). */
  val Discomfort2Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelow.ofIdentifier("discomfort_2"))

  /** Tier-3 discomfort food: rotten, poisonous or not human food (rotten flesh, pufferfish). */
  val Discomfort3Items: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelow.ofIdentifier("discomfort_3"))
}
