package dev.krysztal.casualtiesbelow.datagen

import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementType
import net.minecraft.advancements.triggers.InventoryChangeTrigger
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import net.minecraft.world.level.ItemLike

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider

import dev.krysztal.casualtiesbelow.internal.extension.ComponentExtensions.*
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.progression.CasualtiesBelowTriggers
import dev.krysztal.casualtiesbelow.progression.PlayerEventTrigger

/** Generates the advancement tree under `data/casualtiesbelow/advancement/`. Item acquisitions ride
  * the vanilla `inventory_changed` trigger; physiological conditions use the mod's own
  * conditionless triggers ([[CasualtiesBelowTriggers]]), fired from
  * [[progression.AchievementHooks]].
  */
final class AdvancementProvider(
    output: FabricPackOutput,
    registries: CompletableFuture[HolderLookup.Provider]
) extends FabricAdvancementProvider(output, registries) {

  override def generateAdvancement(
      registries: HolderLookup.Provider,
      consumer: Consumer[AdvancementHolder]
  ): Unit = {
    val root = Advancement.Builder
      .advancement()
      .display(
        CasualtiesBelowItems.BasicBandage,
        "advancements.casualtiesbelow.root.title".translatable(),
        "advancements.casualtiesbelow.root.description".translatable(),
        // 26.2 convention: ResourceTexture expands this to textures/<path>.png itself.
        Identifier.withDefaultNamespace("gui/advancements/backgrounds/adventure"),
        AdvancementType.TASK,
        true,
        false,
        false
      )
      .addCriterion(
        "first_bleeding",
        CasualtiesBelowTriggers.FirstBleeding.createCriterion(new PlayerEventTrigger.Instance)
      )
      .save(consumer, "casualtiesbelow:root")

    itemObtainment(
      consumer,
      root,
      "bandage",
      CasualtiesBelowItems.BasicBandage,
      AdvancementType.TASK
    )
    itemObtainment(
      consumer,
      root,
      "refined_extract",
      CasualtiesBelowItems.RefinedPoppyExtract,
      AdvancementType.TASK
    )
    // Challenge deaths and rescues stay hidden until earned: the surprise is the point.
    playerEvent(
      consumer,
      root,
      "opioid_overdose_death",
      CasualtiesBelowItems.CalibratedSyringe,
      AdvancementType.CHALLENGE,
      CasualtiesBelowTriggers.OpioidOverdoseDeath,
      hidden = true
    )
    playerEvent(
      consumer,
      root,
      "max_discomfort_food",
      Items.ROTTEN_FLESH,
      AdvancementType.TASK,
      CasualtiesBelowTriggers.MaxDiscomfortFood
    )
    playerEvent(
      consumer,
      root,
      "consciousness_minimum",
      Items.SOUL_LANTERN,
      AdvancementType.TASK,
      CasualtiesBelowTriggers.ConsciousnessMinimum
    )
    playerEvent(
      consumer,
      root,
      "hemostasis",
      Items.SHIELD,
      AdvancementType.CHALLENGE,
      CasualtiesBelowTriggers.Hemostasis,
      hidden = true
    )
  }

  private def itemObtainment(
      consumer: Consumer[AdvancementHolder],
      root: AdvancementHolder,
      path: String,
      item: ItemLike,
      frame: AdvancementType
  ): Unit = {
    Advancement.Builder
      .advancement()
      .parent(root)
      .display(
        item,
        s"advancements.casualtiesbelow.$path.title".translatable(),
        s"advancements.casualtiesbelow.$path.description".translatable(),
        null,
        frame,
        true,
        true,
        false
      )
      .addCriterion(s"has_$path", InventoryChangeTrigger.TriggerInstance.hasItems(item))
      .save(consumer, s"casualtiesbelow:$path")
  }

  private def playerEvent(
      consumer: Consumer[AdvancementHolder],
      root: AdvancementHolder,
      path: String,
      icon: ItemLike,
      frame: AdvancementType,
      trigger: PlayerEventTrigger,
      hidden: Boolean = false
  ): Unit = {
    Advancement.Builder
      .advancement()
      .parent(root)
      .display(
        icon,
        s"advancements.casualtiesbelow.$path.title".translatable(),
        s"advancements.casualtiesbelow.$path.description".translatable(),
        null,
        frame,
        true,
        true,
        hidden
      )
      .addCriterion("event", trigger.createCriterion(new PlayerEventTrigger.Instance))
      .save(consumer, s"casualtiesbelow:$path")
  }
}
