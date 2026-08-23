package dev.krysztal.casualtiesbelow.compat.jei

import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemAttributeModifiers

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.CasualtiesBelowTags
import dev.krysztal.casualtiesbelow.config.FormulaConfigValue
import dev.krysztal.casualtiesbelow.sync.ArmorOverrideData
import dev.krysztal.casualtiesbelow.sync.DiscomfortOverrideData
import dev.krysztal.casualtiesbelow.sync.GameplayDataSnapshot

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.IRecipeRegistration

/** JEI integration: informational pages (the "uses" view) describing what this mod makes items do —
  * food discomfort, armor wound protection, and weapon wound profiles.
  *
  * All numbers come from [[GameplayDataSnapshot.current]], i.e. the server's effective
  * configuration when connected. Loaded only when JEI is present (the `jei_mod_plugin` entrypoint
  * is lazy), and regenerated on every JEI start.
  */
@JeiPlugin
object CasualtiesBelowJeiPlugin extends IModPlugin {

  override def getPluginUid: Identifier = CasualtiesBelow.ofIdentifier("jei_plugin")

  override def registerRecipes(registration: IRecipeRegistration): Unit = {
    val data = GameplayDataSnapshot.current
    registerDiscomfort(registration, data)
    registerArmor(registration, data)
    registerSharpWeapons(registration, data)
  }

  // --- food discomfort -----------------------------------------------------

  private def registerDiscomfort(
      registration: IRecipeRegistration,
      data: GameplayDataSnapshot
  ): Unit = {
    val candidates = collectDiscomfortCandidates(data)
    val stewOverridden = matchesAny(new ItemStack(Items.SUSPICIOUS_STEW), data.discomfortOverrides)

    // Group items that share one description into a single info entry.
    val groups = candidates.toList
      .filter(item => item != Items.SUSPICIOUS_STEW || stewOverridden)
      .flatMap { item =>
        resolveMean(new ItemStack(item), data).map(mean => (mean, item))
      }
      .groupBy(_._1)

    groups.foreach { case ((mean, level), pairs) =>
      val headline = level match {
        case Some(tier) =>
          Component.translatable("jei.casualtiesbelow.discomfort.tier", tier, fmt(mean))
        case None =>
          Component.translatable("jei.casualtiesbelow.discomfort.override", fmt(mean))
      }
      registration.addItemStackInfo(
        pairs.map((_, item) => new ItemStack(item)).asJava,
        headline,
        Component.translatable(
          "jei.casualtiesbelow.discomfort.thresholds",
          fmt(data.nauseaThreshold),
          fmt(data.refusalThreshold),
          fmt(data.vomitThreshold)
        )
      )
    }

    if (!stewOverridden) {
      registration.addItemStackInfo(
        List(new ItemStack(Items.SUSPICIOUS_STEW)).asJava,
        Component.translatable("jei.casualtiesbelow.discomfort.stew"),
        Component.translatable(
          "jei.casualtiesbelow.discomfort.thresholds",
          fmt(data.nauseaThreshold),
          fmt(data.refusalThreshold),
          fmt(data.vomitThreshold)
        )
      )
    }
  }

  private def collectDiscomfortCandidates(data: GameplayDataSnapshot): Set[Item] = {
    val items = scala.collection.mutable.LinkedHashSet.empty[Item]
    List(
      CasualtiesBelowTags.Discomfort1Items,
      CasualtiesBelowTags.Discomfort2Items,
      CasualtiesBelowTags.Discomfort3Items
    ).foreach { tag =>
      BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h => items += h.value())
    }
    data.discomfortOverrides.foreach { o =>
      o.items.foreach { id =>
        Try(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))).toOption.foreach(items += _)
      }
      o.tag.foreach { tag =>
        BuiltInRegistries.ITEM.getTagOrEmpty(itemTag(tag)).forEach(h => items += h.value())
      }
    }
    items += Items.SUSPICIOUS_STEW // always documented: its tier is component-derived
    items.toSet
  }

  /** Mirrors `Discomfort.meanOf`: explicit overrides first, then tier tags (most severe wins). */
  private def resolveMean(
      stack: ItemStack,
      data: GameplayDataSnapshot
  ): Option[(Double, Option[Int])] = {
    data.discomfortOverrides.find(_.appliesTo(stack)) match {
      case Some(entry) =>
        entry.level
          .map(l =>
            (
              data.discomfortLevelMeans
                .applyOrElse(l - 1, (_: Int) => data.discomfortLevelMeans.last),
              Some(l)
            )
          )
          .orElse(entry.mean.map(m => (m, None)))
      case None =>
        if (stack.is(CasualtiesBelowTags.Discomfort3Items)) {
          Some((data.discomfortLevelMeans(2), Some(3)))
        } else if (stack.is(CasualtiesBelowTags.Discomfort2Items)) {
          Some((data.discomfortLevelMeans(1), Some(2)))
        } else if (stack.is(CasualtiesBelowTags.Discomfort1Items)) {
          Some((data.discomfortLevelMeans(0), Some(1)))
        } else None
    }
  }

  // --- armor wound protection ----------------------------------------------

  private val ArmorSlots =
    List(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

  private def registerArmor(
      registration: IRecipeRegistration,
      data: GameplayDataSnapshot
  ): Unit = {
    val groups = scala.collection.mutable.LinkedHashMap
      .empty[(EquipmentSlot, Double, Double, Boolean), List[Item]]

    BuiltInRegistries.ITEM.forEach { item =>
      val stack = new ItemStack(item)
      val modifiers =
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
      val ovr = data.armorOverrides.find(_.appliesTo(stack))

      ArmorSlots.foreach { slot =>
        val armor = modifiers.compute(Attributes.ARMOR, 0.0, slot)
        val toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot)
        if (armor > 0.0 || toughness > 0.0) {
          factors(stack, armor, toughness, ovr, data).foreach { (skin, muscle) =>
            val key = (slot, skin, muscle, ovr.isDefined)
            groups(key) = groups.getOrElse(key, List.empty) :+ item
          }
        }
      }
    }

    groups.foreach { case ((slot, skin, muscle, isOverride), items) =>
      registration.addItemStackInfo(
        items.map(new ItemStack(_)).asJava,
        Component.translatable(
          "jei.casualtiesbelow.armor.factors",
          Component.translatable(s"jei.casualtiesbelow.armor.slot.${slot.getSerializedName}"),
          fmt(skin),
          fmt(muscle)
        ),
        Component.translatable(
          if (isOverride) "jei.casualtiesbelow.armor.override"
          else "jei.casualtiesbelow.armor.formula"
        ),
        Component.translatable("jei.casualtiesbelow.armor.bypass")
      )
    }
  }

  /** Resolves the (skin, muscle) factors exactly like `ArmorProtection` does: datapack override
    * first, config formula as fallback, clamped to [0, 1].
    */
  private def factors(
      stack: ItemStack,
      armor: Double,
      toughness: Double,
      ovr: Option[ArmorOverrideData],
      data: GameplayDataSnapshot
  ): Option[(Double, Double)] = {
    val skinSource = ovr.flatMap(_.skin).getOrElse(data.armorSkinFormula)
    val skin = evaluate(skinSource, List("armor", "toughness"), List(armor, toughness))
      .map(v => v.max(0.0).min(1.0))
    skin.flatMap { s =>
      val muscleSource = ovr.flatMap(_.muscle).getOrElse(data.armorMuscleFormula)
      evaluate(
        muscleSource,
        List("armor", "toughness", "skinFactor"),
        List(armor, toughness, s)
      ).map(v => (s, v.max(0.0).min(1.0)))
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
      registration: IRecipeRegistration,
      data: GameplayDataSnapshot
  ): Unit = {
    data.wounds.get("cut").foreach { (skin, muscle, bleed, pain) =>
      val items = BuiltInRegistries.ITEM
        .getTagOrEmpty(CasualtiesBelowTags.SharpMeleeItems)
        .asScala
        .map(h => new ItemStack(h.value()))
        .toList
      if (items.nonEmpty) {
        registration.addItemStackInfo(
          items.asJava,
          Component.translatable(
            "jei.casualtiesbelow.weapon.cut",
            fmt(skin),
            fmt(muscle),
            fmt(bleed),
            fmt(pain)
          ),
          Component.translatable("jei.casualtiesbelow.weapon.note")
        )
      }
    }
  }

  // --- helpers ---------------------------------------------------------------

  private def itemTag(id: String): TagKey[Item] =
    TagKey.create(Registries.ITEM, Identifier.parse(id))

  private def matchesAny(stack: ItemStack, entries: List[DiscomfortOverrideData]): Boolean = {
    entries.exists(_.appliesTo(stack))
  }

  private def fmt(value: Double): String = {
    if (value == value.floor) value.toInt.toString
    else f"$value%.2f"
  }

  extension (o: DiscomfortOverrideData) {
    def appliesTo(stack: ItemStack): Boolean = {
      o.items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem).toString) ||
      o.tag.exists(t => stack.is(itemTag(t)))
    }
  }

  extension (o: ArmorOverrideData) {
    def appliesTo(stack: ItemStack): Boolean = {
      o.items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem).toString) ||
      o.tag.exists(t => stack.is(itemTag(t)))
    }
  }
}
