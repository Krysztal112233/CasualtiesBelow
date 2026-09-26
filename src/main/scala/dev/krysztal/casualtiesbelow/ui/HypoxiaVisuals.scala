package dev.krysztal.casualtiesbelow.ui

import net.minecraft.util.Mth

import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

/** Pure presentation rules shared by the blood-oxygen reserve and the terminal-hypoxia countdown.
  *
  * Every rule here is continuous: colors sweep through smoothstep gradients, bubbles fill
  * fractionally, and pulse frequency/depth ramp with severity instead of switching at thresholds.
  * Stateful callers integrate the phases these curves parameterize (see [[HypoxiaHudState]]), so
  * urgency transitions accelerate smoothly rather than snapping.
  */
private[casualtiesbelow] object HypoxiaVisuals {
  val OxygenSegmentCount = 10

  /** Phase stagger in cycles between adjacent bubbles; shared by the urgency pulse and the idle
    * wave so both travel along the row in the fill direction.
    */
  val StaggerCyclesPerBubble = 0.06

  private val FullOxygenRgb = 0x0058d8e8
  private val HalfOxygenRgb = 0x00ffb347
  private val QuarterOxygenRgb = 0x00ff4f55
  private val EmptyOxygenRgb = 0x00ff2e3f

  private val TerminalFullRgb = 0x0058d8e8
  private val TerminalWarningRgb = 0x00ffb347
  private val TerminalEmptyRgb = 0x00ff4f55

  private val TwoPi = math.Pi * 2.0

  private val OxygenColorAnchors = Vector(
    1.0 -> FullOxygenRgb,
    0.5 -> HalfOxygenRgb,
    0.25 -> QuarterOxygenRgb,
    0.0 -> EmptyOxygenRgb
  )
  private val TerminalColorAnchors = Vector(
    1.0 -> TerminalFullRgb,
    0.25 -> TerminalWarningRgb,
    0.0 -> TerminalEmptyRgb
  )

  /** Continuous reserve color: cyan when full, amber at half, red at a quarter, crimson at zero. */
  def oxygenColor(fraction: Double): Int = {
    anchorGradient(fraction.boundedFraction, OxygenColorAnchors)
  }

  /** Continuous countdown color: cyan with time to spare, warming through amber to red at zero. */
  def terminalColor(remainingFraction: Double): Int = {
    anchorGradient(remainingFraction.boundedFraction, TerminalColorAnchors)
  }

  /** Fractional fill per bubble, rightmost (index 0) filling first; entries sum to
    * `fraction × OxygenSegmentCount` and are non-increasing along the row.
    */
  def bubbleFills(fraction: Double): Vector[Double] = {
    val total = fraction.boundedFraction * OxygenSegmentCount.toDouble
    Vector.tabulate(OxygenSegmentCount)(index => Mth.clamp(total - index.toDouble, 0.0, 1.0))
  }

  /** Phase offset in cycles staggering pulse and idle motion across the bubble row. */
  def staggerOffset(index: Int): Double = {
    positiveModulo(index.toDouble, OxygenSegmentCount.toDouble) * StaggerCyclesPerBubble
  }

  /** Urgency pulse rate in hertz: silent at or above half reserve, ramping to 2.25 Hz at empty. */
  def oxygenPulseFrequency(fraction: Double): Double = {
    val oxygen = fraction.boundedFraction
    if (oxygen >= 0.5) 0.0
    else if (oxygen >= 0.25) Mth.lerp(smooth01((0.5 - oxygen) / 0.25), 0.0, 1.5)
    else Mth.lerp(smooth01((0.25 - oxygen) / 0.25), 1.5, 2.25)
  }

  /** Pulse opacity floor: no dip above half reserve, deepening to 0.5 when empty. */
  def oxygenPulseFloor(fraction: Double): Double = {
    val oxygen = fraction.boundedFraction
    if (oxygen >= 0.5) 1.0
    else if (oxygen >= 0.25) Mth.lerp(smooth01((0.5 - oxygen) / 0.25), 1.0, 0.72)
    else Mth.lerp(smooth01((0.25 - oxygen) / 0.25), 0.72, 0.5)
  }

  /** Countdown pulse rate in hertz: silent at or above a quarter remaining, 2.5 Hz at zero. */
  def terminalPulseFrequency(remainingFraction: Double): Double = {
    val remaining = remainingFraction.boundedFraction
    if (remaining >= 0.25) 0.0
    else Mth.lerp(smooth01((0.25 - remaining) / 0.25), 0.0, 2.5)
  }

  /** Countdown pulse opacity floor: no dip above a quarter remaining, deepening to 0.46 at zero. */
  def terminalPulseFloor(remainingFraction: Double): Double = {
    val remaining = remainingFraction.boundedFraction
    if (remaining >= 0.25) 1.0
    else Mth.lerp(smooth01((0.25 - remaining) / 0.25), 1.0, 0.46)
  }

  /** Sine pulse evaluated at an accumulated phase in cycles; the result stays inside [floor, 1]. */
  def pulseOpacity(phaseCycles: Double, floor: Double): Double = {
    val clampedFloor = Mth.clamp(floor, 0.0, 1.0)
    val wave = (math.sin(phaseCycles * TwoPi) + 1.0) / 2.0
    clampedFloor + (1.0 - clampedFloor) * wave
  }

  /** Remaining countdown fraction; accepts the client's continuously extrapolated exposure. */
  def terminalRemainingFraction(exposureTicks: Double, durationTicks: Int): Double = {
    val duration = durationTicks.max(1).toDouble
    val exposure = if (exposureTicks.isNaN) 0.0 else exposureTicks.max(0.0).min(duration)
    (duration - exposure) / duration
  }

  /** Interpolates packed RGB channels; used for the wake ring's direction tint. */
  def lerpRgb(from: Int, to: Int, factor: Double): Int = {
    val t = Mth.clamp(factor, 0.0, 1.0)
    val r = lerpChannel((from >> 16) & 0xff, (to >> 16) & 0xff, t)
    val g = lerpChannel((from >> 8) & 0xff, (to >> 8) & 0xff, t)
    val b = lerpChannel(from & 0xff, to & 0xff, t)
    (r << 16) | (g << 8) | b
  }

  private def anchorGradient(value: Double, anchors: Vector[(Double, Int)]): Int = {
    anchors
      .sliding(2)
      .collectFirst {
        case Vector((upperValue, upperColor), (lowerValue, lowerColor)) if value >= lowerValue =>
          val local = smooth01((upperValue - value) / (upperValue - lowerValue))
          lerpRgb(upperColor, lowerColor, local)
      }
      .getOrElse(anchors.last._2)
  }

  private def lerpChannel(from: Int, to: Int, factor: Double): Int = {
    math.round(Mth.lerp(factor, from.toDouble, to.toDouble)).toInt
  }

  private def smooth01(value: Double): Double = {
    Mth.smoothstep(Mth.clamp(value, 0.0, 1.0))
  }

  private def positiveModulo(value: Double, modulus: Double): Double = {
    val remainder = value % modulus
    if (remainder < 0.0) remainder + modulus else remainder
  }
}
