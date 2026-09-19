package dev.krysztal.casualtiesbelow.api.body.vitals

import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Whole-player vitals grouped into read-only snapshots: pain shock, consciousness, circulation
  * (blood oxygen and blood volume), and infection, plus the standalone adrenaline, discomfort, and
  * opioid scalars. Immune health ranges from 0 to the configured maximum (`maxImmuneHealth`,
  * default 200); consciousness level, pain-shock load, and blood oxygen from 0 to their constants
  * below; adrenaline from 0 to its configured maximum; blood volume is in mL, up to the configured
  * maximum; discomfort runs from 0 to the configured maximum (`[discomfort] maxValue`, default
  * 100); dirtiness runs from 0 to the configured maximum (`[dirtiness] maxValue`, default 100);
  * acute opioid level runs from 0 to 200 and dependence from 0 to 100. Core body temperature runs
  * from 0 to [[VitalsComponent.MaxBodyTemperature]] in °C and starts at
  * [[VitalsComponent.NormalBodyTemperature]]; wetness runs from 0 (dry) to 1 (soaked).
  * Consciousness has a separate ordinary floor, oxygen- and opioid-derived hard ceilings, knockout
  * threshold, and higher wake threshold; the `unconscious` latch is therefore not inferred from the
  * floor. Opioid level is intentionally server-only and reads as zero on clients; dependence is
  * owner-synchronized.
  *
  * `Double` rather than `Float` preserves per-tick accumulation precision.
  */
trait VitalsComponent extends CardinalComponent {
  def shock: ShockSnapshot
  def consciousness: ConsciousnessSnapshot
  def circulation: CirculationSnapshot
  def infection: InfectionSnapshot
  def adrenaline: Double
  def discomfort: Double
  def dirtiness: Double
  def opioidLevel: Double
  def opioidDependence: Double
  def bodyTemperature: Double
  def wetness: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0
  val MaxBloodOxygen: Double = 100.0
  val MaxOpioidLevel: Double = 200.0
  val MaxOpioidDependence: Double = 100.0
  val MinimumWakeThreshold: Double = 1.0e-6
  val NormalBodyTemperature: Double = 37.0
  val MaxBodyTemperature: Double = 45.0
  val MaxWetness: Double = 1.0
}
