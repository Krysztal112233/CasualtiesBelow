package dev.krysztal.casualtiesbelow.compat.jei

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemAttributeModifiers

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.data.schema.ArmorProtectionData
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.extension.ComponentExtensions.*
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot
import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.IRecipeRegistration

/** JEI integration: informational pages (the "uses" view) describing what this mod makes items do —
  * food discomfort, armor wound protection, and weapon wound profiles.
  *
  * Config-derived numbers come from [[GameplayDataSnapshot.current]] and per-object content comes
  * from the current client's synced gameplay-data store. Loaded only when JEI is present (the
  * `jei_mod_plugin` entrypoint is lazy), and regenerated on every JEI start.
  */
@JeiPlugin
object CasualtiesBelowJeiPlugin extends IModPlugin {

  override def getPluginUid: Identifier = CasualtiesBelow.ofIdentifier("jei_plugin")

  override def registerRecipes(registration: IRecipeRegistration): Unit = {
    given data: GameplayDataSnapshot = GameplayDataSnapshot.current
    given store: GameplayDataStore = data.gameplayData

    registerDiscomfort(registration)
    registerArmor(registration)
    registerSharpWeapons(registration)
  }

  // --- food discomfort -----------------------------------------------------

  private def registerDiscomfort(
      registration: IRecipeRegistration
  )(using data: GameplayDataSnapshot, store: GameplayDataStore): Unit = {
    val candidates = collectDiscomfortCandidates
    val stewOverridden = GameplayDataLookup
      .foodEffects(Items.SUSPICIOUS_STEW.builtInRegistryHolder(), store)
      .exists(e => e.discomfortTier.isPresent || e.discomfortMean.isPresent)

    // Group items that share one description into a single info entry.
    val groups = candidates.toList
      .filter(_ != Items.SUSPICIOUS_STEW || stewOverridden)
      .flatMap { item =>
        Discomfort
          .meanOfWithTier(new ItemStack(item), data, store)
          .map(mean => (mean, item))
      }
      .groupBy(_._1)

    groups.foreach { case ((mean, level), pairs) =>
      val headline = level match {
        case Some(tier) =>
          "jei.casualtiesbelow.discomfort.tier".translatable(tier, fmt(mean))
        case None =>
          "jei.casualtiesbelow.discomfort.override".translatable(fmt(mean))
      }
      registration.addItemStackInfo(
        pairs.map((_, item) => new ItemStack(item)).asJava,
        headline,
        "jei.casualtiesbelow.discomfort.thresholds".translatable(
          fmt(data.nauseaThreshold),
          fmt(data.refusalThreshold),
          fmt(data.vomitChanceThreshold)
        ),
        "jei.casualtiesbelow.discomfort.vomiting".translatable(
          fmt(data.vomitMinChancePerTick * 100.0),
          fmt(data.vomitMaxChancePerTick * 100.0),
          fmt(data.vomitRelief),
          fmt(data.vomitReliefSpreadFraction * 100.0)
        )
      )
    }

    if (!stewOverridden) {
      registration.addItemStackInfo(
        List(new ItemStack(Items.SUSPICIOUS_STEW)).asJava,
        "jei.casualtiesbelow.discomfort.stew".translatable(),
        "jei.casualtiesbelow.discomfort.thresholds".translatable(
          fmt(data.nauseaThreshold),
          fmt(data.refusalThreshold),
          fmt(data.vomitChanceThreshold)
        ),
        "jei.casualtiesbelow.discomfort.vomiting".translatable(
          fmt(data.vomitMinChancePerTick * 100.0),
          fmt(data.vomitMaxChancePerTick * 100.0),
          fmt(data.vomitRelief),
          fmt(data.vomitReliefSpreadFraction * 100.0)
        )
      )
    }
  }

  private def collectDiscomfortCandidates(using store: GameplayDataStore): Set[Item] = {
    val items = scala.collection.mutable.LinkedHashSet.empty[Item]
    List(
      CasualtiesBelowTags.Discomfort1Items,
      CasualtiesBelowTags.Discomfort2Items,
      CasualtiesBelowTags.Discomfort3Items
    ).foreach { tag =>
      BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h => items += h.value())
    }
    store.foodEffects.foreach { (id, entry) =>
      if (entry.discomfortTier.isPresent || entry.discomfortMean.isPresent) {
        BuiltInRegistries.ITEM.get(id).toScala.foreach(holder => items += holder.value())
      }
    }
    items += Items.SUSPICIOUS_STEW // always documented: its tier is component-derived
    items.toSet
  }

  // --- armor wound protection ----------------------------------------------

  private def registerArmor(
      registration: IRecipeRegistration
  )(using data: GameplayDataSnapshot, store: GameplayDataStore): Unit = {
    val groups = scala.collection.mutable.LinkedHashMap
      .empty[(EquipmentSlot, Double, Double, Boolean), List[Item]]
    val covered = scala.collection.mutable.Set.empty[(Item, EquipmentSlot)]

    BuiltInRegistries.ITEM.forEach { item =>
      val stack = new ItemStack(item)
      val modifiers =
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
      val ovr = GameplayDataLookup.armorProtection(stack, store)

      Consts.ArmorSlots.foreach { slot =>
        val armor = modifiers.compute(Attributes.ARMOR, 0.0, slot)
        val toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot)
        if (armor > 0.0 || toughness > 0.0) {
          factors(stack, armor, toughness, ovr).foreach { case (skin, muscle) =>
            covered += (item -> slot)
            val key = (slot, skin, muscle, ovr.isDefined)
            groups(key) = groups.getOrElse(key, List.empty) :+ item
          }
        }
      }
    }

    // Override-targeted pieces not covered above: items without a positive armor attribute
    // (elytra and similar) protect nothing by default, but a datapack override can give them
    // factors, which the runtime honors too. Attributes are slot-specific, so coverage is
    // tracked per (item, slot) and the real per-slot attributes are evaluated (usually zero).
    BuiltInRegistries.ITEM.forEach { item =>
      val stack = new ItemStack(item)
      Option(stack.get(DataComponents.EQUIPPABLE))
        .filter(equippable => Consts.ArmorSlots.contains(equippable.slot()))
        .foreach { equippable =>
          val slot = equippable.slot()
          if (!covered.contains(item -> slot)) {
            GameplayDataLookup.armorProtection(stack, store).foreach { ovr =>
              val modifiers = stack
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
              val armor = modifiers.compute(Attributes.ARMOR, 0.0, slot)
              val toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot)
              factors(stack, armor, toughness, Some(ovr)).foreach { case (skin, muscle) =>
                if (skin < 1.0 || muscle < 1.0) {
                  val key = (slot, skin, muscle, true)
                  groups(key) = groups.getOrElse(key, List.empty) :+ item
                }
              }
            }
          }
        }
    }

    groups.foreach { case ((slot, skin, muscle, isOverride), items) =>
      registration.addItemStackInfo(
        items.map(new ItemStack(_)).asJava,
        "jei.casualtiesbelow.armor.factors".translatable(
          s"jei.casualtiesbelow.armor.slot.${slot.getSerializedName}".translatable(),
          fmt(skin),
          fmt(muscle)
        ),
        Component.translatable(
          if (isOverride) "jei.casualtiesbelow.armor.override"
          else "jei.casualtiesbelow.armor.formula"
        ),
        "jei.casualtiesbelow.armor.bypass".translatable()
      )
    }
  }

  /** Resolves the (skin, muscle) factors exactly like `ArmorProtection.mitigate` does: for each
    * factor independently, the datapack override formula is tried first and the config formula is
    * the fallback when the override is absent or fails to evaluate (a broken override degrades to
    * the fallback instead of dropping the page); the muscle formula sees the resolved skin factor.
    * Results are clamped to [0, 1].
    */
  private def factors(
      stack: ItemStack,
      armor: Double,
      toughness: Double,
      ovr: Option[ArmorProtectionData]
  )(using data: GameplayDataSnapshot): Option[(Double, Double)] = {
    val skin = ovr
      .flatMap(_.skinFactor.toScala)
      .map(_.source)
      .flatMap(evaluate(_, List("armor", "toughness"), List(armor, toughness)))
      .orElse(evaluate(data.armorSkinFormula, List("armor", "toughness"), List(armor, toughness)))
      .map(v => v.max(0.0).min(1.0))
    skin.flatMap { s =>
      ovr
        .flatMap(_.muscleFactor.toScala)
        .map(_.source)
        .flatMap(
          evaluate(_, List("armor", "toughness", "skinFactor"), List(armor, toughness, s))
        )
        .orElse(
          evaluate(
            data.armorMuscleFormula,
            List("armor", "toughness", "skinFactor"),
            List(armor, toughness, s)
          )
        )
        .map(v => (s, v.max(0.0).min(1.0)))
    }
  }

  /** Compiles and evaluates one formula source on the calling thread (expressions are not
    * thread-safe; each call here builds a fresh expression).
    */
  private def evaluate(
      source: String,
      variables: List[String],
      values: List[Double]
  ): Option[Double] = {
    Try {
      val expression = FormulaConfigValue.compile(source, variables)
      variables.lazyZip(values).foreach { (name, value) =>
        expression.`with`(name, value)
      }
      expression.evaluate().getNumberValue.doubleValue()
    }.toOption
  }

  // --- weapon wound profiles ------------------------------------------------

  private def registerSharpWeapons(
      registration: IRecipeRegistration
  )(using store: GameplayDataStore): Unit = {
    val profileId = CasualtiesBelow.ofIdentifier("cut")
    GameplayDataLookup
      .entry(store.woundProfiles, profileId)
      .foreach { profile =>
        val items = BuiltInRegistries.ITEM
          .getTagOrEmpty(CasualtiesBelowTags.SharpMeleeItems)
          .asScala
          .map(h => new ItemStack(h.value()))
          .toList
        if (items.nonEmpty) {
          registration.addItemStackInfo(
            items.asJava,
            Component.translatable(
              Util.makeDescriptionId("wound_profile", profileId),
              fmt(profile.skinPerPoint),
              fmt(profile.musclePerPoint),
              fmt(profile.bleedRatePerWound),
              fmt(profile.painPerPoint)
            ),
            "jei.casualtiesbelow.weapon.note".translatable()
          )
        }
      }
  }

  // --- helpers ---------------------------------------------------------------

  private def fmt(value: Double): String = {
    if (value == value.floor) value.toInt.toString
    else f"$value%.2f"
  }
}
