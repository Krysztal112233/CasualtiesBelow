package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: immune health value, consciousness, blood volume, and discomfort. Immune
  * health ranges from 0 to the configured maximum (`maxImmuneHealth`, default 200), consciousness
  * from 0 to [[VitalsComponent.MaxValue]]; blood volume is in mL, up to the configured maximum;
  * discomfort runs from 0 to the configured maximum (`[discomfort] maxValue`, default 100).
  * `Double` rather than `Float` for the same reason as [[LimbStats]]: per-tick accumulation
  * precision.
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var immuneHealth: Double
  var consciousness: Double
  var bloodVolume: Double
  var sepsis: Double
  var discomfort: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0
}
