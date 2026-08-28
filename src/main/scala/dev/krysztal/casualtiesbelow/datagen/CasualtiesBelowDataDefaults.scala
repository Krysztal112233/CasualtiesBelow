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
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.DamageTypeTags
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Items

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.data.ArmorProtectionData
import dev.krysztal.casualtiesbelow.api.data.CasualtiesBelowRegistries
import dev.krysztal.casualtiesbelow.api.data.FallRulesData
import dev.krysztal.casualtiesbelow.api.data.FormulaSource
import dev.krysztal.casualtiesbelow.api.data.HitLocationData
import dev.krysztal.casualtiesbelow.api.data.WoundProfileData
import dev.krysztal.casualtiesbelow.api.data.WoundRuleData

/** Built-in entries for the mod's datapack registries. These values deliberately duplicate the
  * compiled consumer fallbacks so deleting an entry remains safe without changing defaults.
  */
object CasualtiesBelowDataDefaults {
  private val WoundProfiles = List(
    ("bite", WoundProfileData(1.5, 2.0, 0.1, 4.0)),
    ("cut", WoundProfileData(2.0, 3.0, 0.2, 4.0)),
    ("blunt", WoundProfileData(0.0, 3.0, 0.0, 4.0)),
    ("pierce", WoundProfileData(3.0, 2.0, 0.15, 5.0)),
    ("burn", WoundProfileData(1.0, 0.2, 0.0, 2.0)),
    ("prick", WoundProfileData(1.5, 0.0, 0.05, 1.0)),
    ("blast", WoundProfileData(2.0, 2.0, 0.25, 6.0))
  )

  def bootstrapWoundProfiles(context: BootstrapContext[WoundProfileData]): Unit = {
    WoundProfiles.foreach { (id, profile) => context.register(woundProfileKey(id), profile) }
  }

  def bootstrapWoundRules(context: BootstrapContext[WoundRuleData]): Unit = {
    val damageTypes = context.lookup(Registries.DAMAGE_TYPE)
    val entityTypes = context.lookup(Registries.ENTITY_TYPE)
    val items = context.lookup(Registries.ITEM)

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

    val rules = List(
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
      "evoker_fangs" -> rule(
        "pierce",
        damageTypes = Some(damageSet(DamageTypes.INDIRECT_MAGIC)),
        predicate = Some(entityPredicate(EntityTypes.EVOKER_FANGS)),
        priority = 40
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
    )

    rules.foreach { (id, value) => context.register(woundRuleKey(id), value) }
  }

  def bootstrapArmorProtection(context: BootstrapContext[ArmorProtectionData]): Unit = {
    // (material, worn pieces, skin factor formula, muscle factor formula)
    val sets = List(
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
    )
    sets.foreach { (material, pieces, skin, muscle) =>
      context.register(
        CasualtiesBelowRegistries.entryKey(CasualtiesBelowRegistries.ArmorProtection, material),
        ArmorProtectionData(
          HolderSet.direct(pieces.map(_.builtInRegistryHolder()).asJava),
          Optional.of(FormulaSource(skin, Seq("armor", "toughness"))),
          Optional.of(FormulaSource(muscle, Seq("armor", "toughness", "skinFactor"))),
          0
        )
      )
    }
  }

  def bootstrapDiscomfort(
      context: BootstrapContext[dev.krysztal.casualtiesbelow.api.data.DiscomfortData]
  ): Unit = ()

  def bootstrapFallRules(context: BootstrapContext[FallRulesData]): Unit = {
    context.register(
      CasualtiesBelowRegistries.entryKey(CasualtiesBelowRegistries.FallRules, "player"),
      FallRulesData(PlayerEntities)
    )
  }

  def bootstrapHitLocation(context: BootstrapContext[HitLocationData]): Unit = {
    context.register(
      CasualtiesBelowRegistries.entryKey(CasualtiesBelowRegistries.HitLocation, "player"),
      HitLocationData(
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
  }

  /** All six player body parts share one entity set; the intrusive holder is the canonical
    * reference, no bootstrap lookup needed.
    */
  private val PlayerEntities: HolderSet[EntityType[?]] =
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
    woundProfileKey(profile),
    scatter,
    optional(forcedPart),
    priority
  )

  private def woundProfileKey(id: String): ResourceKey[WoundProfileData] =
    CasualtiesBelowRegistries.entryKey(CasualtiesBelowRegistries.WoundProfile, id)

  private def woundRuleKey(id: String): ResourceKey[WoundRuleData] =
    CasualtiesBelowRegistries.entryKey(CasualtiesBelowRegistries.WoundRule, id)

  private def optional[T](value: Option[T]): Optional[T] =
    value.fold(Optional.empty[T]())(Optional.of)

  private def optionalBoxed(value: Option[Boolean]): Optional[JBoolean] =
    value.fold(Optional.empty[JBoolean]())(v => Optional.of(JBoolean.valueOf(v)))
}
