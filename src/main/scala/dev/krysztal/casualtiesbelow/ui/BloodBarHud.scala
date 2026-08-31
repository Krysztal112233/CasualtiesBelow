package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot

/** Single-row blood-volume bar replacing vanilla hearts in their original HUD slot, with the
  * current synchronized volume centered in mL.
  *
  * The fraction deliberately uses the healthy server-authored maximum rather than the
  * sepsis-limited effective capacity. This keeps reduced absolute circulating volume visible as a
  * smaller survival reserve; the MedicalPanel separately reports utilization of the current
  * effective capacity.
  */
@Environment(EnvType.CLIENT)
object BloodBarHud {
  // Mirrors vanilla Hud: ten 9 px hearts at 8 px spacing, starting 91 px left of center.
  private val Width = 81
  private val Height = 9
  private val StatusBarHeight = 10
  private val InnerWidth = Width - 2

  private val OutlineColor = 0xff000000
  private val BackgroundColor = 0xff260606
  private val FillColor = 0xffb0222d
  private val HighlightColor = 0xffdf4650
  private val TextColor = 0xffffffff
  private val TextScale = 0.75f

  def register(): Unit = {
    HudElementRegistry.replaceElement(
      VanillaHudElements.HEALTH_BAR,
      (_: HudElement) => (graphics: GuiGraphicsExtractor, _: DeltaTracker) => extract(graphics)
    )
    HudStatusBarHeightRegistry.addLeft(
      VanillaHudElements.HEALTH_BAR,
      (_: Player) => StatusBarHeight
    )
  }

  private def extract(graphics: GuiGraphicsExtractor): Unit = {
    val minecraft = Minecraft.getInstance()
    Option(minecraft.player).foreach { player =>
      val vitals = CasualtiesBelowComponents.Vitals.get(player)
      val maxBloodVolume = GameplayDataSnapshot.current.maxBloodVolume
      val fraction =
        if (maxBloodVolume > 0.0)
          Mth.clamp(vitals.circulation.bloodVolume / maxBloodVolume, 0.0, 1.0)
        else 0.0
      val fillWidth =
        if (fraction > 0.0) math.max(1, Math.round(InnerWidth * fraction).toInt)
        else 0
      val x = graphics.guiWidth() / 2 - 91
      val y =
        graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(
          VanillaHudElements.HEALTH_BAR
        )

      graphics.fill(x, y, x + Width, y + Height, OutlineColor)
      graphics.fill(x + 1, y + 1, x + Width - 1, y + Height - 1, BackgroundColor)
      if (fillWidth > 0) {
        graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + Height - 1, FillColor)
        graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2, HighlightColor)
      }

      val volumeLabel = s"${math.max(0L, Math.round(vitals.circulation.bloodVolume))} mL"
      val textWidth = minecraft.font.width(volumeLabel) * TextScale
      val textHeight = minecraft.font.lineHeight * TextScale
      val textX = x + (Width - textWidth) / 2.0f
      val textY = y + (Height - textHeight) / 2.0f
      val pose = graphics.pose()
      pose.pushMatrix()
      try {
        pose.translate(textX, textY)
        pose.scale(TextScale, TextScale)
        graphics.text(minecraft.font, volumeLabel, 0, 0, TextColor, true)
      } finally {
        pose.popMatrix()
      }
    }
  }
}
