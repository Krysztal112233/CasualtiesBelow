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
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.PainShockStage
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot

/** Circular wake-progress indicator shown while the synced unconscious latch is active.
  *
  * The low-opacity track fades in over half a second; its bright clockwise arc advances from the
  * unconscious-entry threshold to the wake threshold. Falling consciousness colors the ring red,
  * while recovery colors it white. The world blackout remains independent in the post effect, and
  * F1 can hide this HUD element without revealing the world.
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
  private val DirectionEpsilon = 1.0e-6
  private val FallingRgb = 0x00ff4040
  private val RisingRgb = 0x00ffffff

  private enum Direction {
    case Falling, Rising
  }

  private final case class RingPixel(x: Int, completion: Double)

  private final case class RingRow(y: Int, pixels: Vector[RingPixel])

  private final case class RingGeometry(diameter: Int, rows: Vector[RingRow])

  private var cachedGeometry: Option[RingGeometry] = None
  private var trackedPlayer: Option[LocalPlayer] = None
  private var lastConsciousness: Option[Double] = None
  private var direction = Direction.Falling
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

        val vitals = CasualtiesBelowComponents.Vitals.get(player)
        if (!vitals.unconscious && visibility <= 0.0f) {
          resetAnimation()
          return
        }

        updateDirection(vitals.consciousness)
        val fadeStep = deltaTracker.getRealtimeDeltaTicks() / FadeDurationTicks
        if (vitals.unconscious) {
          visibility = math.min(1.0f, visibility + fadeStep)
        } else {
          visibility = math.max(0.0f, visibility - fadeStep)
          if (visibility <= 0.0f) {
            resetAnimation()
            return
          }
        }

        val entryThreshold =
          if (vitals.painShockStage == PainShockStage.Recovering) 0.0
          else CasualtiesBelowConfig.ConsciousnessFloor.get().doubleValue
        val wakeThreshold = GameplayDataSnapshot.current.unconsciousWakeThreshold
        val progress = wakeProgress(vitals.consciousness, entryThreshold, wakeThreshold)
        drawRing(graphics, progress, minecraft.getWindow.getGuiScale)
      case None =>
        trackedPlayer = None
        resetAnimation()
    }
  }

  private def updateDirection(consciousness: Double): Unit = {
    lastConsciousness.foreach { previous =>
      if (consciousness > previous + DirectionEpsilon) {
        direction = Direction.Rising
      } else if (consciousness < previous - DirectionEpsilon) {
        direction = Direction.Falling
      }
    }
    lastConsciousness = Some(consciousness)
  }

  private def wakeProgress(
      consciousness: Double,
      entryThreshold: Double,
      wakeThreshold: Double
  ): Double = {
    val span = wakeThreshold - entryThreshold
    if (span <= DirectionEpsilon) {
      if (consciousness >= wakeThreshold) 1.0 else 0.0
    } else {
      Mth.clamp((consciousness - entryThreshold) / span, 0.0, 1.0)
    }
  }

  private def drawRing(
      graphics: GuiGraphicsExtractor,
      progress: Double,
      guiScale: Int
  ): Unit = {
    val targetDiameter = graphics.guiHeight().toFloat * DiameterHeightRatio
    val rasterPixelSize = RasterPhysicalPixelSize / math.max(guiScale, 1).toFloat
    val geometry = ringGeometry(targetDiameter, rasterPixelSize)
    val scale = targetDiameter / geometry.diameter.toFloat
    val left = (graphics.guiWidth().toFloat - targetDiameter) / 2.0f
    val top = (graphics.guiHeight().toFloat - targetDiameter) / 2.0f
    val rgb = direction match {
      case Direction.Falling => FallingRgb
      case Direction.Rising  => RisingRgb
    }
    val trackColor = withAlpha(rgb, visibility * TrackOpacity)
    val progressColor = withAlpha(rgb, visibility)
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
        if (fullRing || pixel.completion < completion) {
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
    lastConsciousness = None
    direction = Direction.Falling
    visibility = 0.0f
  }
}
