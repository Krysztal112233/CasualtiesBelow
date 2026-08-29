package dev.krysztal.casualtiesbelow.datagen

import java.lang.Boolean as JBoolean
import java.util.List as JList
import java.util.Optional

import scala.jdk.CollectionConverters.*

import net.minecraft.advancements.predicates.DamageSourcePredicate
import net.minecraft.advancements.predicates.DataComponentMatchers
import net.minecraft.advancements.predicates.ItemPredicate
import net.minecraft.advancements.predicates.MinMaxBounds
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.DamageTypeTags
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.ArmorProtectionData
import dev.krysztal.casualtiesbelow.api.data.DiscomfortData
import dev.krysztal.casualtiesbelow.api.data.FallRulesData
import dev.krysztal.casualtiesbelow.api.data.FormulaSource
import dev.krysztal.casualtiesbelow.api.data.HitLocationData
import dev.krysztal.casualtiesbelow.api.data.WoundProfileData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData

/** Built-in entries for the mod's six keyed gameplay-data directories. These values deliberately
  * duplicate the compiled consumer fallbacks so deleting an entry remains safe without changing
  * defaults.
  */
object CasualtiesBelowDataDefaults {
  val WoundProfiles: Map[Identifier, WoundProfileData] = List(
    "bite" -> WoundProfileData(1.5, 2.0, 0.1, 4.0),
    "cut" -> WoundProfileData(2.0, 3.0, 0.2, 4.0),
    "blunt" -> WoundProfileData(0.0, 3.0, 0.0, 4.0),
    "pierce" -> WoundProfileData(3.0, 2.0, 0.15, 5.0),
    "burn" -> WoundProfileData(1.0, 0.2, 0.0, 2.0),
    "prick" -> WoundProfileData(1.5, 0.0, 0.05, 1.0),
    "blast" -> WoundProfileData(2.0, 2.0, 0.25, 6.0),
    "fall" -> WoundProfileData(4.0, 4.0, 0.5, 6.0)
  ).map((id, value) => entryId(id) -> value).toMap

  def woundRules(registries: HolderLookup.Provider): Map[Identifier, WoundRuleData] = {
    val damageTypes = registries.lookupOrThrow(Registries.DAMAGE_TYPE)
    val entityTypes = registries.lookupOrThrow(Registries.ENTITY_TYPE)
    val items = registries.lookupOrThrow(Registries.ITEM)

    def damageSet(keys: ResourceKey[DamageType]*): HolderSet[DamageType] =
      HolderSet.direct(keys.map(damageTypes.getOrThrow).asJava)

    def damageTag(tag: TagKey[DamageType]): HolderSet[DamageType] =
      damageTypes.getOrThrow(tag)

    def entityPredicate(entityType: EntityType[?]): DamageSourcePredicate = {
      val direct = EntityPredicate.Builder.entity().of(entityTypes, entityType).build()
      DamageSourcePredicate(
        JList.of(),
        Optional.of(direct),
        Optional.empty(),
        Optional.empty()
      )
    }

    def entityTagPredicate(tag: TagKey[EntityType[?]]): DamageSourcePredicate = {
      val direct = EntityPredicate.Builder.entity().of(entityTypes, tag).build()
      DamageSourcePredicate(
        JList.of(),
        Optional.of(direct),
        Optional.empty(),
        Optional.empty()
      )
    }

    val sharpWeapon = ItemPredicate(
      Optional.of(items.getOrThrow(CasualtiesBelowTags.SharpMeleeItems)),
      MinMaxBounds.Ints.ANY,
      DataComponentMatchers.ANY
    )

    List(
      "fire" -> rule(
        "burn",
        damageTypes = Some(damageTag(DamageTypeTags.IS_FIRE)),
        priority = 100
      ),
      "explosion" -> rule(
        "blast",
        damageTypes = Some(damageTag(CasualtiesBelowTags.BlastSources)),
        scatter = true,
        priority = 90
      ),
      "piercing_projectile" -> rule(
        "pierce",
        damageTypes =
          Some(damageSet(DamageTypes.ARROW, DamageTypes.TRIDENT, DamageTypes.MOB_PROJECTILE)),
        priority = 80
      ),
      "falling_pierce" -> rule(
        "pierce",
        damageTypes = Some(damageSet(DamageTypes.FALLING_STALACTITE)),
        forcedPart = Some(BodyPart.Head),
        priority = 70
      ),
      "falling_crush" -> rule(
        "blunt",
        damageTypes = Some(damageSet(DamageTypes.FALLING_ANVIL, DamageTypes.FALLING_BLOCK)),
        forcedPart = Some(BodyPart.Head),
        priority = 70
      ),
      "fall" -> rule(
        "fall",
        damageTypes = Some(damageSet(DamageTypes.FALL)),
        priority = 60
      ),
      "prick" -> rule(
        "prick",
        damageTypes = Some(damageSet(DamageTypes.CACTUS, DamageTypes.SWEET_BERRY_BUSH)),
        priority = 60
      ),
      "sonic_boom" -> rule(
        "blunt",
        damageTypes = Some(damageSet(DamageTypes.SONIC_BOOM)),
        priority = 50
      ),
      "stalagmite" -> rule(
        "pierce",
        damageTypes = Some(damageSet(DamageTypes.STALAGMITE)),
        priority = 45
      ),
      "evoker_fangs" -> rule(
        "pierce",
        damageTypes = Some(damageSet(DamageTypes.INDIRECT_MAGIC)),
        predicate = Some(entityPredicate(EntityTypes.EVOKER_FANGS)),
        priority = 40
      ),
      "ender_pearl" -> rule(
        "prick",
        damageTypes = Some(damageSet(DamageTypes.ENDER_PEARL)),
        priority = 35
      ),
      "melee_sharp" -> rule(
        "cut",
        directLiving = Some(true),
        weapon = Some(sharpWeapon),
        priority = 30
      ),
      "melee_slam" -> rule(
        "blunt",
        predicate = Some(entityTagPredicate(CasualtiesBelowTags.BluntMeleeEntities)),
        directLiving = Some(true),
        priority = 20
      ),
      "melee_armed" -> rule(
        "blunt",
        directLiving = Some(true),
        armed = Some(true),
        priority = 10
      ),
      "melee_bare" -> rule(
        "bite",
        directLiving = Some(true),
        armed = Some(false),
        priority = 0
      )
    ).map((id, value) => entryId(id) -> value).toMap
  }

  val ArmorProtection: Map[Identifier, ArmorProtectionData] = {
    // (material, worn pieces, skin factor formula, muscle factor formula)
    List(
      (
        "leather",
        List(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS),
        "max(0.05, 1 - armor*0.05)",
        "1 - (1-skinFactor)*0.9"
      ),
      (
        "chainmail",
        List(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS),
        "max(0.05, 1 - armor*0.16)",
        "1 - (1-skinFactor)*0.1"
      ),
      (
        "iron",
        List(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS),
        "max(0.05, 1 - armor*0.1)",
        "1 - (1-skinFactor)*0.5"
      ),
      (
        "diamond",
        List(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS),
        "max(0.05, 1 - armor*0.11 - toughness*0.02)",
        "1 - (1-skinFactor)*0.55"
      ),
      (
        "netherite",
        List(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS),
        "max(0.05, 1 - armor*0.12 - toughness*0.03)",
        "1 - (1-skinFactor)*0.65"
      )
    ).map { (material, pieces, skin, muscle) =>
      entryId(material) -> ArmorProtectionData(
        HolderSet.direct(pieces.map(_.builtInRegistryHolder()).asJava),
        Optional.of(FormulaSource(skin, Seq("armor", "toughness"))),
        Optional.of(FormulaSource(muscle, Seq("armor", "toughness", "skinFactor"))),
        0
      )
    }.toMap
  }

  val Discomfort: Map[Identifier, DiscomfortData] = Map.empty

  val FallRules: Map[Identifier, FallRulesData] = Map(
    entryId("player") -> FallRulesData(
      PlayerEntities,
      primaryWeights = Map(
        BodyPart.Head -> 0.0,
        BodyPart.Torso -> 0.0,
        BodyPart.ArmLeft -> 0.0,
        BodyPart.ArmRight -> 0.0,
        BodyPart.LegLeft -> 0.5,
        BodyPart.LegRight -> 0.5
      ),
      pairedFraction = 1.0,
      secondaryThreshold = 8.0,
      secondaryFraction = 0.5
    )
  )

  val HitLocations: Map[Identifier, HitLocationData] = Map(
    entryId("player") -> HitLocationData(
      PlayerEntities,
      legsBelow = 0.35,
      headAbove = 1.0,
      fallbackWeights = Map(
        BodyPart.Torso -> 0.5,
        BodyPart.Head -> 0.1,
        BodyPart.ArmLeft -> 0.1,
        BodyPart.ArmRight -> 0.1,
        BodyPart.LegLeft -> 0.1,
        BodyPart.LegRight -> 0.1
      ),
      priority = 0
    )
  )

  /** All six player body parts share one entity set; the intrusive holder is the canonical
    * reference, no registry lookup needed.
    */
  private lazy val PlayerEntities: HolderSet[EntityType[?]] =
    HolderSet.direct(JList.of(EntityTypes.PLAYER.builtInRegistryHolder()))

  private def rule(
      profile: String,
      damageTypes: Option[HolderSet[DamageType]] = None,
      predicate: Option[DamageSourcePredicate] = None,
      directLiving: Option[Boolean] = None,
      armed: Option[Boolean] = None,
      weapon: Option[ItemPredicate] = None,
      scatter: Boolean = false,
      forcedPart: Option[BodyPart] = None,
      priority: Int = 0
  ): WoundRuleData = WoundRuleData(
    optional(damageTypes),
    optional(predicate),
    optionalBoxed(directLiving),
    optionalBoxed(armed),
    optional(weapon),
    entryId(profile),
    scatter,
    optional(forcedPart),
    priority
  )

  private def entryId(path: String): Identifier = CasualtiesBelow.ofIdentifier(path)

  private def optional[T](value: Option[T]): Optional[T] =
    value.fold(Optional.empty[T]())(Optional.of)

  private def optionalBoxed(value: Option[Boolean]): Optional[JBoolean] =
    value.fold(Optional.empty[JBoolean]())(v => Optional.of(JBoolean.valueOf(v)))
}
