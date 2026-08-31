package dev.krysztal.casualtiesbelow.blood

import dev.krysztal.casualtiesbelow.api.body.CirculationSnapshot

/** Internal circulation storage: the public oxygen/volume pair plus the hidden terminal-hypoxia
  * exposure and totem-hemostasis timers that must not leak into the API.
  */
private[casualtiesbelow] final case class CirculationState(
    bloodOxygen: Double,
    bloodVolume: Double,
    hypoxiaExposureTicks: Int,
    totemHemostasisTicks: Int
) {

  /** Public read-only view exposed through `VitalsComponent.circulation`. */
  def snapshot: CirculationSnapshot = CirculationSnapshot(bloodOxygen, bloodVolume)
}
