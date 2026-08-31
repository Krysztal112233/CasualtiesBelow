package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.component.ComponentAccess
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot

/** Circular state indicator shown while unconscious or in terminal hypoxia.
  *
  * The low-opacity track fades in over half a second; its bright clockwise arc advances from the
  * unconscious-entry threshold to the wake threshold. The ring tint sweeps between red and white
  * over a few ticks as consciousness trends down or up. Once the blood-oxygen reserve is empty, the
  * same geometry crossfades into a continuous countdown ring whose arc recedes smoothly and warms
  * from cyan to red as terminal exposure elapses, extrapolated between the sparse server sync
  * milestones by [[HypoxiaHudState]]. The world blackout remains independent in the post effect,
  * and F1 can hide this HUD element without revealing the world.
  */
@Environment(EnvType.CLIENT)
object UnconsciousOverlay {
  private val DiameterHeightRatio = 3.0f / 5.0f
  private val RasterPhysicalPixelSize = 2.0f
  private val InnerRadiusRatio = 17.0 / 18.0
  private val MinimumRasterDiameter = 3
  private val TwoPi = math.Pi * 2.0
  private val FadeDurationTicks = 10.0f
  private val TrackOpacity = 0.28
  private val Epsilon = 1.0e-6
  private val FallingRgb = 0x00ff4040
  private val RisingRgb = 0x00ffffff
  private val TerminalTrackRgb = 0x00374b54

  private final case class RingPixel(x: Int, completion: Double)

  private final case class RingRow(y: Int, pixels: Vector[RingPixel])

  private final case class RingGeometry(diameter: Int, rows: Vector[RingRow])

  private var cachedGeometry: Option[RingGeometry] = None
  private var trackedPlayer: Option[LocalPlayer] = None
  private var visibility = 0.0f

  def register(): Unit = {
    HudElementRegistry.addLast(
      CasualtiesBelow.ofIdentifier("unconscious"),
      (graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) =>
        extract(graphics, deltaTracker)
    )
  }

  private def extract(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker): Unit = {
    val minecraft = Minecraft.getInstance()
    // addLast intentionally has no inherited vanilla HUD condition, so apply the documented F1
    // behavior directly instead of depending on registry ordering or an implementation detail.
    if (minecraft.gui.hud.isHidden()) return

    Option(minecraft.player)
      .filter(player => !player.isCreative && !player.isSpectator && player.isAlive) match {
      case Some(player) =>
        if (!trackedPlayer.contains(player)) {
          resetAnimation()
          trackedPlayer = Some(player)
        }

        val vitals = ComponentAccess.vitals(player)
        val exposureTicks = vitals.hypoxiaExposureTicks
        val terminalActive = exposureTicks > 0
        val shouldShow = vitals.consciousness.unconscious || terminalActive
        if (!shouldShow && visibility <= 0.0f) {
          resetAnimation()
          return
        }

        val fadeStep = deltaTracker.getRealtimeDeltaTicks() / FadeDurationTicks
        if (shouldShow) {
          visibility = math.min(1.0f, visibility + fadeStep)
        } else {
          visibility = math.max(0.0f, visibility - fadeStep)
          if (visibility <= 0.0f) {
            resetAnimation()
            return
          }
        }

        val realtimeDelta = deltaTracker.getRealtimeDeltaTicks()
        HypoxiaHudState.advanceTerminalBlend(realtimeDelta, terminalActive)
        HypoxiaHudState.advanceDirectionBlend(realtimeDelta)
        val terminalBlend = HypoxiaHudState.terminalBlendValue.toDouble
        val gameplayData = GameplayDataSnapshot.current
        val guiScale = minecraft.getWindow.getGuiScale
        if (terminalBlend < 1.0) {
          val entryThreshold =
            if (vitals.shock.stage == PainShockStage.Recovering) 0.0
            else gameplayData.consciousnessKnockoutThreshold
          val progress = wakeProgress(
            vitals.consciousness.level,
            entryThreshold,
            gameplayData.unconsciousWakeThreshold
          )
          drawWakeRing(
            graphics,
            progress,
            guiScale,
            visibility.toDouble * (1.0 - terminalBlend)
          )
        }
        if (terminalBlend > 0.0) {
          drawTerminalRing(
            graphics,
            gameplayData.terminalHypoxiaDurationTicks,
            guiScale,
            deltaTracker.getGameTimeDeltaPartialTick(false),
            visibility.toDouble * terminalBlend
          )
        }
      case None =>
        trackedPlayer = None
        resetAnimation()
    }
  }

  private def wakeProgress(
      consciousness: Double,
      entryThreshold: Double,
      wakeThreshold: Double
  ): Double = {
    val span = wakeThreshold - entryThreshold
    if (span <= Epsilon) {
      if (consciousness >= wakeThreshold) 1.0 else 0.0
    } else {
      Mth.clamp((consciousness - entryThreshold) / span, 0.0, 1.0)
    }
  }

  private def drawWakeRing(
      graphics: GuiGraphicsExtractor,
      progress: Double,
      guiScale: Int,
      alphaScale: Double
  ): Unit = {
    val targetDiameter = graphics.guiHeight().toFloat * DiameterHeightRatio
    val rasterPixelSize = RasterPhysicalPixelSize / math.max(guiScale, 1).toFloat
    val geometry = ringGeometry(targetDiameter, rasterPixelSize)
    val scale = targetDiameter / geometry.diameter.toFloat
    val left = (graphics.guiWidth().toFloat - targetDiameter) / 2.0f
    val top = (graphics.guiHeight().toFloat - targetDiameter) / 2.0f
    val tint = HypoxiaVisuals.lerpRgb(
      FallingRgb,
      RisingRgb,
      Mth.smoothstep(HypoxiaHudState.directionBlendValue.toDouble)
    )
    val trackColor = withAlpha(tint, alphaScale * TrackOpacity)
    val progressColor = withAlpha(tint, alphaScale)
    val pose = graphics.pose()

    pose.pushMatrix()
    try {
      pose.translate(left, top)
      pose.scale(scale, scale)
      drawRows(graphics, geometry, 1.0, trackColor)
      drawRows(graphics, geometry, progress, progressColor)
    } finally {
      pose.popMatrix()
    }
  }

  private def drawTerminalRing(
      graphics: GuiGraphicsExtractor,
      durationTicks: Int,
      guiScale: Int,
      partialTick: Float,
      alphaScale: Double
  ): Unit = {
    val targetDiameter = graphics.guiHeight().toFloat * DiameterHeightRatio
    val rasterPixelSize = RasterPhysicalPixelSize / math.max(guiScale, 1).toFloat
    val geometry = ringGeometry(targetDiameter, rasterPixelSize)
    val scale = targetDiameter / geometry.diameter.toFloat
    val left = (graphics.guiWidth().toFloat - targetDiameter) / 2.0f
    val top = (graphics.guiHeight().toFloat - targetDiameter) / 2.0f
    val remaining = HypoxiaVisuals.terminalRemainingFraction(
      HypoxiaHudState.smoothedExposureTicks(partialTick),
      durationTicks
    )
    val rgb = HypoxiaVisuals.terminalColor(remaining)
    val pulse = HypoxiaVisuals.pulseOpacity(
      HypoxiaHudState.terminalPulsePhaseCycles(partialTick),
      HypoxiaVisuals.terminalPulseFloor(remaining)
    )
    val trackColor = withAlpha(TerminalTrackRgb, alphaScale * TrackOpacity)
    val progressColor = withAlpha(rgb, alphaScale * pulse)
    val pose = graphics.pose()

    pose.pushMatrix()
    try {
      pose.translate(left, top)
      pose.scale(scale, scale)
      drawRows(graphics, geometry, 1.0, trackColor)
      drawRows(graphics, geometry, remaining, progressColor)
    } finally {
      pose.popMatrix()
    }
  }

  private def ringGeometry(targetDiameter: Float, rasterPixelSize: Float): RingGeometry = {
    val rawDiameter =
      math.max(MinimumRasterDiameter, math.ceil(targetDiameter / rasterPixelSize).toInt)
    val diameter = if (rawDiameter % 2 == 0) rawDiameter + 1 else rawDiameter
    cachedGeometry match {
      case Some(geometry) if geometry.diameter == diameter => geometry
      case _                                               =>
        val geometry = createRingGeometry(diameter)
        cachedGeometry = Some(geometry)
        geometry
    }
  }

  private def createRingGeometry(diameter: Int): RingGeometry = {
    val outerRadius = diameter / 2
    val innerRadius = outerRadius.toDouble * InnerRadiusRatio
    val outerRadiusSquared = outerRadius * outerRadius
    val innerRadiusSquared = innerRadius * innerRadius
    val rows = (for {
      y <- 0 until diameter
      dy = y - outerRadius
      pixels = (for {
        x <- 0 until diameter
        dx = x - outerRadius
        distanceSquared = dx * dx + dy * dy
        if distanceSquared <= outerRadiusSquared
        if distanceSquared >= innerRadiusSquared
      } yield {
        val rawAngle = math.atan2(dx.toDouble, -dy.toDouble)
        val clockwiseAngle = if (rawAngle < 0.0) rawAngle + TwoPi else rawAngle
        RingPixel(x, clockwiseAngle / TwoPi)
      }).toVector
      if pixels.nonEmpty
    } yield RingRow(y, pixels)).toVector
    RingGeometry(diameter, rows)
  }

  private def drawRows(
      graphics: GuiGraphicsExtractor,
      geometry: RingGeometry,
      completion: Double,
      color: Int
  ): Unit = {
    val fullRing = completion >= 1.0
    geometry.rows.foreach { row =>
      var runStart = -1
      var runEnd = -1
      row.pixels.foreach { pixel =>
        val beforeCompletion = fullRing || pixel.completion < completion
        if (beforeCompletion) {
          if (runStart < 0) {
            runStart = pixel.x
            runEnd = pixel.x + 1
          } else if (pixel.x == runEnd) {
            runEnd = pixel.x + 1
          } else {
            graphics.fill(runStart, row.y, runEnd, row.y + 1, color)
            runStart = pixel.x
            runEnd = pixel.x + 1
          }
        } else if (runStart >= 0) {
          graphics.fill(runStart, row.y, runEnd, row.y + 1, color)
          runStart = -1
          runEnd = -1
        }
      }
      if (runStart >= 0) {
        graphics.fill(runStart, row.y, runEnd, row.y + 1, color)
      }
    }
  }

  private def withAlpha(rgb: Int, opacity: Double): Int = {
    val alpha = math.round(Mth.clamp(opacity, 0.0, 1.0) * 255.0).toInt
    (alpha << 24) | (rgb & 0x00ffffff)
  }

  private def resetAnimation(): Unit = {
    visibility = 0.0f
    HypoxiaHudState.resetOverlayTransitions()
  }
}
