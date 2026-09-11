package dev.krysztal.casualtiesbelow.physiology.opioid

/** Internal opioid storage: the hidden acute level plus the synced long-term dependence axis.
  * Exposed individually through `VitalsComponent.opioidLevel` / `opioidDependence`; level is
  * deliberately excluded from sync.
  */
private[casualtiesbelow] final case class OpioidState(level: Double, dependence: Double)
