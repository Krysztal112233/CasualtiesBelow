package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent

/** Contextual ten-bubble blood-oxygen reserve rendered in vanilla's air-bar slot.
  *
  * Vanilla breath bubbles stay untouched until blood oxygen actually falls; the mod bar then
  * crossfades in over them (and back out on recovery). All motion is continuous: the edge bubble
  * fills fractionally from the bottom, the fill color sweeps cyan → amber → red with the reserve,
  * and the urgency pulse staggers along the row over a slow idle wave instead of throbbing in
  * unison. Server oxygen arrives per tick and is eased plus frame-interpolated through
  * [[HypoxiaHudState]].
  */
@Environment(EnvType.CLIENT)
object OxygenReserveHud {
  private val BubbleSize = 9
  private val BubbleSpacing = 8
  private val RightOffset = 91
  private val InnerRowCount = 7
  private val OxygenEpsilon = 1.0e-6
  private val BobAmplitudePixels = 0.75
  private val TwoPi = math.Pi * 2.0

  private val OutlineColor = 0xff071116
  private val EmptyColor = 0xff14303a
  private val HighlightColor = 0xffd9fbff

  private val OuterRows = Vector(
    (0, 3, 6),
    (1, 1, 8),
    (2, 0, 9),
    (3, 0, 9),
    (4, 0, 9),
    (5, 0, 9),
    (6, 0, 9),
    (7, 1, 8),
    (8, 3, 6)
  )
  private val InnerRows = Vector(
    (1, 3, 6),
    (2, 2, 7),
    (3, 1, 8),
    (4, 1, 8),
    (5, 1, 8),
    (6, 2, 7),
    (7, 3, 6)
  )

  def register(): Unit = {
    HudElementRegistry.replaceElement(
      VanillaHudElements.AIR_BAR,
      (vanilla: HudElement) =>
        (graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) => {
          val minecraft = Minecraft.getInstance()
          Option(minecraft.player) match {
            case Some(player) if !player.isCreative && !player.isSpectator =>
              val engaged =
                CasualtiesBelowComponents.Vitals
                  .get(player)
                  .bloodOxygen < VitalsComponent.MaxBloodOxygen - OxygenEpsilon
              HypoxiaHudState.advanceOxygenVisibility(
                deltaTracker.getRealtimeDeltaTicks(),
                engaged
              )
              val visibility = HypoxiaHudState.oxygenVisibilityValue
              if (visibility <= 0.0f) {
                vanilla.extractRenderState(graphics, deltaTracker)
              } else {
                // Vanilla stays underneath until the mod bar is fully opaque, so the takeover
                // and the hand-back are true crossfades instead of same-frame swaps.
                if (visibility < 1.0f) {
                  vanilla.extractRenderState(graphics, deltaTracker)
                }
                extract(graphics, deltaTracker, visibility)
              }
            case _ => vanilla.extractRenderState(graphics, deltaTracker)
          }
        }
    )
  }

  private def extract(
      graphics: GuiGraphicsExtractor,
      deltaTracker: DeltaTracker,
      visibility: Float
  ): Unit = {
    val partialTick = deltaTracker.getGameTimeDeltaPartialTick(false)
    val fraction = HypoxiaHudState.smoothedOxygen(partialTick) / VitalsComponent.MaxBloodOxygen
    val fills = HypoxiaVisuals.bubbleFills(fraction)
    val fillRgb = HypoxiaVisuals.oxygenColor(fraction)
    val pulseFloor = HypoxiaVisuals.oxygenPulseFloor(fraction)
    val pulsePhase = HypoxiaHudState.oxygenPulsePhaseCycles(partialTick)
    val breathPhase = HypoxiaHudState.breathPhaseCycles(partialTick)
    val xRight = graphics.guiWidth() / 2 + RightOffset
    val y =
      graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR)

    fills.zipWithIndex.foreach { (fill, index) =>
      val stagger = HypoxiaVisuals.staggerOffset(index)
      val opacity = HypoxiaVisuals.pulseOpacity(pulsePhase + stagger, pulseFloor) * visibility
      val bob = (math.sin((breathPhase + stagger) * TwoPi) * BobAmplitudePixels).toFloat
      val x = xRight - index * BubbleSpacing - BubbleSize
      drawBubble(
        graphics,
        x,
        y,
        fill,
        withAlpha(fillRgb, opacity),
        withAlpha(HighlightColor, opacity),
        bob
      )
    }
  }

  private def drawBubble(
      graphics: GuiGraphicsExtractor,
      x: Int,
      y: Int,
      fill: Double,
      fillColor: Int,
      highlightColor: Int,
      bob: Float
  ): Unit = {
    // The edge bubble fills from the bottom row up; the outline shell always stays intact.
    val filledRows = math.round(fill * InnerRowCount).toInt
    val pose = graphics.pose()
    // Rows draw inside a translated pose so the idle wave offsets the whole bubble fractionally
    // without opening seams between its one-pixel fill runs.
    pose.pushMatrix()
    try {
      pose.translate(0.0f, bob)
      OuterRows.foreach { (row, start, end) =>
        graphics.fill(x + start, y + row, x + end, y + row + 1, OutlineColor)
      }
      InnerRows.foreach { (row, start, end) =>
        val color = if (row >= BubbleSize - 1 - filledRows) fillColor else EmptyColor
        graphics.fill(x + start, y + row, x + end, y + row + 1, color)
      }
      if (filledRows >= InnerRowCount - 1) {
        graphics.fill(x + 3, y + 2, x + 6, y + 3, highlightColor)
      }
    } finally {
      pose.popMatrix()
    }
  }

  private def withAlpha(rgb: Int, opacity: Double): Int = {
    val alpha = math.round(Mth.clamp(opacity, 0.0, 1.0) * 255.0).toInt
    (alpha << 24) | (rgb & 0x00ffffff)
  }
}
