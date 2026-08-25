package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: immune health, consciousness, blood oxygen, blood volume, sepsis, and
  * discomfort. Immune health ranges from 0 to the configured maximum (`maxImmuneHealth`, default
  * 200); consciousness and blood oxygen from 0 to their constants below; blood volume is in mL, up
  * to the configured maximum; discomfort runs from 0 to the configured maximum (`[discomfort]
  * maxValue`, default 100). Blood oxygen is normalized oxygen availability relative to a healthy,
  * fully oxygenated player rather than a clinical saturation percentage: less blood lowers how much
  * oxygen the body can carry.
  *
  * `Double` rather than `Float` for the same reason as [[LimbStats]]: per-tick accumulation
  * precision.
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var immuneHealth: Double

  /** Read-only outside the centralized consciousness authority; the scalar and latch always mutate
    * as one reconciled state.
    */
  def consciousness: Double
  def unconscious: Boolean

  var bloodOxygen: Double
  var bloodVolume: Double
  var sepsis: Double
  var discomfort: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0
  val MaxBloodOxygen: Double = 100.0
  val MinimumWakeThreshold: Double = 1.0e-6
}
