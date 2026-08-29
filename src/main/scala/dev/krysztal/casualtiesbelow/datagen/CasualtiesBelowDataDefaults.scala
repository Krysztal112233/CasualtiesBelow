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
import dev.krysztal.casualtiesbelow.api.data.FormulaSource
import dev.krysztal.casualtiesbelow.api.data.HitLocationData
import dev.krysztal.casualtiesbelow.api.data.WoundMatchData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData
import dev.krysztal.casualtiesbelow.api.wound.ConditionStepData
import dev.krysztal.casualtiesbelow.api.wound.DamageTypeSelector
import dev.krysztal.casualtiesbelow.api.wound.ExactDamageType
import dev.krysztal.casualtiesbelow.api.wound.FixedTargetData
import dev.krysztal.casualtiesbelow.api.wound.HemostasisData
import dev.krysztal.casualtiesbelow.api.wound.HitLocationTargetData
import dev.krysztal.casualtiesbelow.api.wound.LocalizedApplicationData
import dev.krysztal.casualtiesbelow.api.wound.PairedImpactApplicationData
import dev.krysztal.casualtiesbelow.api.wound.PairedImpactData
import dev.krysztal.casualtiesbelow.api.wound.ScatterApplicationData
import dev.krysztal.casualtiesbelow.api.wound.SpillImpactData
import dev.krysztal.casualtiesbelow.api.wound.TaggedDamageType
import dev.krysztal.casualtiesbelow.api.wound.WeightedTargetData
import dev.krysztal.casualtiesbelow.api.wound.WoundApplicationData
import dev.krysztal.casualtiesbelow.api.wound.WoundContributionData
import dev.krysztal.casualtiesbelow.api.wound.WoundProfile
import dev.krysztal.casualtiesbelow.api.wound.WoundSeverityPolicy

/** Built-in entries for the mod's keyed gameplay-data directories. */
object CasualtiesBelowDataDefaults {
  val WoundProfiles: Map[Identifier, WoundProfile] = List(
    "bite" -> WoundProfile(1.5, 2.0, 0.1, 4.0),
    "cut" -> WoundProfile(2.0, 3.0, 0.2, 4.0),
    "blunt" -> WoundProfile(0.0, 3.0, 0.0, 4.0),
    "pierce" -> WoundProfile(3.0, 2.0, 0.15, 5.0),
    "burn" -> WoundProfile(1.0, 0.2, 0.0, 2.0),
    "prick" -> WoundProfile(1.5, 0.0, 0.05, 1.0),
    "blast" -> WoundProfile(2.0, 2.0, 0.25, 6.0),
    "fall" -> WoundProfile(4.0, 4.0, 0.5, 6.0)
  ).map((id, value) => entryId(id) -> value).toMap

  def woundRules(registries: HolderLookup.Provider): Map[Identifier, WoundRuleData] = {
    val entityTypes = registries.lookupOrThrow(Registries.ENTITY_TYPE)
    val items = registries.lookupOrThrow(Registries.ITEM)

    def exact(keys: net.minecraft.resources.ResourceKey[DamageType]*): DamageTypeSelector =
      DamageTypeSelector(keys.map(ExactDamageType.apply).toList)

    def tagged(tag: TagKey[DamageType]): DamageTypeSelector =
      DamageTypeSelector(List(TaggedDamageType(tag)))

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
        localized("burn", hemostasis = Some(HemostasisData(0.2, 0.25))),
        damageTypes = Some(tagged(DamageTypeTags.IS_FIRE)),
        priority = 100
      ),
      "explosion" -> rule(
        scatter("blast"),
        damageTypes = Some(tagged(CasualtiesBelowTags.BlastSources)),
        priority = 90
      ),
      "piercing_projectile" -> rule(
        localized("pierce"),
        damageTypes =
          Some(exact(DamageTypes.ARROW, DamageTypes.TRIDENT, DamageTypes.MOB_PROJECTILE)),
        priority = 80
      ),
      "falling_pierce" -> rule(
        localized("pierce", FixedTargetData(BodyPart.Head)),
        damageTypes = Some(exact(DamageTypes.FALLING_STALACTITE)),
        priority = 70
      ),
      "falling_crush" -> rule(
        localized("blunt", FixedTargetData(BodyPart.Head)),
        damageTypes = Some(exact(DamageTypes.FALLING_ANVIL, DamageTypes.FALLING_BLOCK)),
        priority = 70
      ),
      "fall" -> rule(
        fallImpact,
        damageTypes = Some(tagged(CasualtiesBelowTags.FallImpacts)),
        victims = Some(PlayerEntities),
        priority = 60
      ),
      "prick" -> rule(
        localized("prick"),
        damageTypes = Some(exact(DamageTypes.CACTUS, DamageTypes.SWEET_BERRY_BUSH)),
        priority = 60
      ),
      "sonic_boom" -> rule(
        localized("blunt"),
        damageTypes = Some(exact(DamageTypes.SONIC_BOOM)),
        priority = 50
      ),
      "stalagmite" -> rule(
        localized("pierce"),
        damageTypes = Some(exact(DamageTypes.STALAGMITE)),
        priority = 45
      ),
      "evoker_fangs" -> rule(
        localized("pierce"),
        damageTypes = Some(exact(DamageTypes.INDIRECT_MAGIC)),
        predicate = Some(entityPredicate(EntityTypes.EVOKER_FANGS)),
        priority = 40
      ),
      "ender_pearl" -> rule(
        localized("prick"),
        damageTypes = Some(exact(DamageTypes.ENDER_PEARL)),
        priority = 35
      ),
      "melee_sharp" -> rule(
        localized("cut"),
        directLiving = Some(true),
        weapon = Some(sharpWeapon),
        priority = 30
      ),
      "melee_slam" -> rule(
        localized("blunt"),
        predicate = Some(entityTagPredicate(CasualtiesBelowTags.BluntMeleeEntities)),
        directLiving = Some(true),
        priority = 20
      ),
      "melee_armed" -> rule(
        localized("blunt"),
        directLiving = Some(true),
        armed = Some(true),
        priority = 10
      ),
      "melee_bare" -> rule(
        localized("bite"),
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

  val HitLocations: Map[Identifier, HitLocationData] = Map(
    entryId("player") -> HitLocationData(
      PlayerEntities,
      legsBelow = HitLocationData.DefaultLegsBelow,
      headAbove = HitLocationData.DefaultHeadAbove,
      priority = 0
    )
  )

  /** All six player body parts share one entity set; the intrusive holder is the canonical
    * reference, no registry lookup needed.
    */
  private lazy val PlayerEntities: HolderSet[EntityType[?]] =
    HolderSet.direct(JList.of(EntityTypes.PLAYER.builtInRegistryHolder()))

  private def rule(
      application: WoundApplicationData,
      damageTypes: Option[DamageTypeSelector] = None,
      excludedDamageTypes: Option[DamageTypeSelector] = None,
      victims: Option[HolderSet[EntityType[?]]] = None,
      predicate: Option[DamageSourcePredicate] = None,
      directLiving: Option[Boolean] = None,
      armed: Option[Boolean] = None,
      weapon: Option[ItemPredicate] = None,
      priority: Int = 0
  ): WoundRuleData = WoundRuleData(
    WoundMatchData(
      optional(damageTypes),
      optional(excludedDamageTypes),
      optional(victims),
      optional(predicate),
      optionalBoxed(directLiving),
      optionalBoxed(armed),
      optional(weapon)
    ),
    List(application),
    priority
  )

  private def localized(
      profile: String,
      target: dev.krysztal.casualtiesbelow.api.wound.WoundTargetData = HitLocationTargetData(
        WoundRuleData.DefaultWeights
      ),
      hemostasis: Option[HemostasisData] = None
  ): WoundApplicationData =
    LocalizedApplicationData(List(wound(profile, hemostasis)), target)

  private def scatter(profile: String): WoundApplicationData =
    ScatterApplicationData(List(wound(profile)), minCount = 2, maxCount = 3)

  private def fallImpact: WoundApplicationData = PairedImpactApplicationData(
    List(wound("fall")),
    WoundSeverityPolicy.FallImpact,
    WeightedTargetData(WoundRuleData.DefaultFallWeights),
    Optional.of(PairedImpactData(1.0)),
    Optional.of(SpillImpactData(above = 8.0, count = 1, fraction = 0.5)),
    List(
      ConditionStepData(
        dev.krysztal.casualtiesbelow.api.body.LimbCondition.Fracture,
        atLeast = 10.0,
        pain = 50.0,
        Optional.of(Integer.valueOf(24000))
      ),
      ConditionStepData(
        dev.krysztal.casualtiesbelow.api.body.LimbCondition.Dislocation,
        atLeast = 8.0,
        pain = 30.0,
        Optional.empty()
      )
    )
  )

  private def wound(
      profile: String,
      hemostasis: Option[HemostasisData] = None
  ): WoundContributionData =
    WoundContributionData(
      entryId(profile),
      severityMultiplier = 1.0,
      hemostasis = optional(hemostasis)
    )

  private def entryId(path: String): Identifier = CasualtiesBelow.ofIdentifier(path)

  private def optional[T](value: Option[T]): Optional[T] =
    value.fold(Optional.empty[T]())(Optional.of)

  private def optionalBoxed(value: Option[Boolean]): Optional[JBoolean] =
    value.fold(Optional.empty[JBoolean]())(v => Optional.of(JBoolean.valueOf(v)))
}
