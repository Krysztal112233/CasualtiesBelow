package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Whole-player vitals grouped into read-only snapshots: pain shock, consciousness, circulation
  * (blood oxygen and blood volume), and infection, plus the standalone adrenaline reserve and
  * discomfort scalars. Immune health ranges from 0 to the configured maximum (`maxImmuneHealth`,
  * default 200); consciousness level, pain-shock load, and blood oxygen from 0 to their constants
  * below; adrenaline from 0 to its configured maximum; blood volume is in mL, up to the configured
  * maximum; discomfort runs from 0 to the configured maximum (`[discomfort] maxValue`, default
  * 100). Consciousness has a separate ordinary floor, oxygen-derived hard ceiling, knockout
  * threshold, and higher wake threshold; the `unconscious` latch is therefore not inferred from the
  * floor.
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
}

object VitalsComponent {
  val MaxValue: Double = 100.0
  val MaxBloodOxygen: Double = 100.0
  val MinimumWakeThreshold: Double = 1.0e-6
}
