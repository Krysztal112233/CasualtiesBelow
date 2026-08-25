package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.sync.GameplayDataSnapshot

/** Status text shown while the synced unconscious latch is active.
  *
  * The world blackout itself is handled independently by the post effect, while screens and chat
  * remain usable. F1 can hide this status text, but it cannot reveal the blacked-out world.
  */
@Environment(EnvType.CLIENT)
object UnconsciousOverlay {
  private val TitleColor = 0xffffffff
  private val ProgressColor = 0xffc0c0c0

  def register(): Unit = {
    HudElementRegistry.addLast(
      CasualtiesBelow.ofIdentifier("unconscious"),
      (graphics: GuiGraphicsExtractor, _: DeltaTracker) => extract(graphics)
    )
  }

  private def extract(graphics: GuiGraphicsExtractor): Unit = {
    val minecraft = Minecraft.getInstance()
    // addLast intentionally has no inherited vanilla HUD condition, so apply the documented F1
    // behavior directly instead of depending on registry ordering or an implementation detail.
    if (minecraft.gui.hud.isHidden()) return

    Option(minecraft.player)
      .filter(player => !player.isCreative && !player.isSpectator && player.isAlive)
      .map(CasualtiesBelowComponents.Vitals.get)
      .filter(_.unconscious)
      .foreach { vitals =>
        val wakeThreshold = GameplayDataSnapshot.current.unconsciousWakeThreshold
        val title = Component.translatable("hud.casualtiesbelow.unconscious")
        val progress = Component.translatable(
          "hud.casualtiesbelow.unconscious.progress",
          vitals.consciousness.toInt,
          wakeThreshold.toInt
        )
        val titleX = (graphics.guiWidth() - minecraft.font.width(title)) / 2
        val progressX = (graphics.guiWidth() - minecraft.font.width(progress)) / 2
        val titleY = graphics.guiHeight() * 2 / 3
        graphics.text(minecraft.font, title, titleX, titleY, TitleColor, true)
        graphics.text(minecraft.font, progress, progressX, titleY + 11, ProgressColor, true)
      }
  }
}
