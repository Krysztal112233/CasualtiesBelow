package dev.krysztal.casualtiesbelow.api

import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

import dev.krysztal.casualtiesbelow.CasualtiesBelow

/** The mod's own vanilla-registry tags, used for wound classification (see
  * [[dev.krysztal.casualtiesbelow.api.wound.WoundProfiles]]). Being datapack tags, both the
  * sharp-weapon set and the blunt-attacker exceptions are overridable/extendable by datapacks and
  * other mods.
  */
object CasualtiesBelowTags {

  /** Melee weapons that cut skin open (swords, axes by default). */
  val SharpMeleeItems: TagKey[Item] =
    TagKey.create(Registries.ITEM, CasualtiesBelow.ofIdentifier("sharp_melee"))

  /** Unarmed attackers whose hits are blunt slams rather than bites/scratches (slimes, golems,
    * ...). Bare-handed attackers not in this tag default to bite/scratch wounds.
    */
  val BluntMeleeEntities: TagKey[EntityType[?]] =
    TagKey.create(Registries.ENTITY_TYPE, CasualtiesBelow.ofIdentifier("blunt_melee"))
}
