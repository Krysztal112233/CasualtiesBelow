package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Per-limb body condition (muscle health, skin integrity, fracture, infection, dislocation and
  * external bleeding) attached to every player.
  */
trait BodyComponent extends CopyableComponent[BodyComponent] with AutoSyncedComponent {
  def stats(part: BodyPart): LimbStats

  def setStats(part: BodyPart, stats: LimbStats): Unit
}
