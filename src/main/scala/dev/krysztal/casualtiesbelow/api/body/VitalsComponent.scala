package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Whole-player vitals: immune health, consciousness, hidden pain-shock load and phase, temporary
  * adrenaline, blood oxygen, blood volume, hidden terminal-hypoxia exposure and totem hemostasis,
  * sepsis, and discomfort. Immune health ranges from 0 to the configured maximum
  * (`maxImmuneHealth`, default 200); consciousness, pain-shock load, and blood oxygen from 0 to
  * their constants below; adrenaline from 0 to its configured maximum; blood volume is in mL, up to
  * the configured maximum; discomfort runs from 0 to the configured maximum (`[discomfort]
  * maxValue`, default 100). Blood oxygen is normalized oxygen availability relative to a healthy,
  * fully oxygenated player rather than a clinical saturation percentage: moderate blood loss
  * retains full carrying capacity, then capacity falls linearly below the configured blood
  * fraction. Consciousness has a separate ordinary floor, oxygen-derived hard ceiling, knockout
  * threshold, and higher wake threshold; the `unconscious` latch is therefore not inferred from the
  * floor.
  *
  * `Double` rather than `Float` preserves per-tick accumulation precision.
  */
trait VitalsComponent extends CardinalComponent {
  def immuneHealth: Double
  def consciousness: Double
  def unconscious: Boolean
  def painShockLoad: Double
  def painShockStage: PainShockStage
  def adrenaline: Double
  def bloodOxygen: Double
  def bloodVolume: Double
  def sepsis: Double
  def discomfort: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0
  val MaxBloodOxygen: Double = 100.0
  val MinimumWakeThreshold: Double = 1.0e-6
}
