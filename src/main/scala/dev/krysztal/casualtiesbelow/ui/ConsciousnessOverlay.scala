package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.ARGB
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Screen dimming at low consciousness: below the dim threshold the view closes in (a dark vignette
  * over a light full-screen haze), and at or below the blackout threshold the dimming is strongest
  * and slowly pulses, like drifting in and out of focus.
  *
  * Purely presentational: the value comes from the synced vitals component (authoritative on the
  * server), and the thresholds and strength are display tuning in the `[vitals]` config section.
  * Layered right above vanilla's misc overlays (which include the world-border vignette), so it
  * sits under the crosshair and status bars and hides with F1 like any other HUD element.
  */
@Environment(EnvType.CLIENT)
object ConsciousnessOverlay {

  def register(): Unit = {
    HudElementRegistry.attachElementAfter(
      VanillaHudElements.MISC_OVERLAYS,
      CasualtiesBelow.ofIdentifier("consciousness_dim"),
      render
    )
  }

  private val render: HudElement = new HudElement {
    override def extractRenderState(
        graphics: GuiGraphicsExtractor,
        deltaTracker: DeltaTracker
    ): Unit = {
      val player = Minecraft.getInstance().player
      if (player == null || player.isSpectator || !player.isAlive) return
      val vitals = CasualtiesBelowComponents.Vitals.get(player)
      if (vitals == null) return

      val maxOpacity = CasualtiesBelowConfig.ConsciousnessMaxDimOpacity.get().toFloat
      if (maxOpacity <= 0.0f) return

      val dim = CasualtiesBelowConfig.ConsciousnessDimThreshold.get()
      val blackout = math.min(CasualtiesBelowConfig.ConsciousnessBlackoutThreshold.get(), dim)
      val consciousness = vitals.consciousness
      if (consciousness >= dim) return

      // 0 at the dim threshold → 1 at the blackout threshold.
      var strength = ((dim - consciousness) / (dim - blackout).max(1.0e-6)).toFloat
      strength = Mth.clamp(strength, 0.0f, 1.0f)
      if (consciousness <= blackout) {
        val time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(true)
        strength *= 0.85f + 0.15f * Mth.sin(time * PulseSpeed)
      }

      val alpha = maxOpacity * strength
      val width = graphics.guiWidth()
      val height = graphics.guiHeight()
      graphics.fill(
        0,
        0,
        width,
        height,
        ARGB.colorFromFloat(alpha * HazeFraction, 0.0f, 0.0f, 0.0f)
      )
      graphics.blit(
        RenderPipelines.VIGNETTE,
        VignetteTexture,
        0,
        0,
        0.0f,
        0.0f,
        width,
        height,
        width,
        height,
        ARGB.colorFromFloat(alpha, 0.0f, 0.0f, 0.0f)
      )
    }
  }

  private val VignetteTexture =
    net.minecraft.resources.Identifier.withDefaultNamespace("textures/misc/vignette.png")

  /** The full-screen haze is kept weaker than the edge vignette so the view dims from the outside
    * in instead of washing out.
    */
  private val HazeFraction = 0.35f

  /** Blackout pulse angular speed (radians per tick): one breath-like cycle every ~2 seconds. */
  private val PulseSpeed = (2.0 * Math.PI / 40.0).toFloat
}
