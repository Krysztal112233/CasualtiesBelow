package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Per-limb body condition (muscle health, skin integrity, fracture, infection, dislocation and
  * external bleeding) attached to every player.
  *
  * Both accessors are defensive: [[stats]] returns a copy and [[setStats]] stores a copy, so
  * retaining the returned or the passed instance never mutates internal state. The
  * read-modify-write idiom is:
  *
  * {{{
  * val s = body.stats(part)
  * s.pain += amount
  * body.setStats(part, s)
  * }}}
  *
  * [[setStats]] is the only write path: it reconciles derived state (movement modifiers) and
  * requires an explicit sync afterwards; mutating a previously obtained [[LimbStats]] instance has
  * no effect on the component.
  */
trait BodyComponent extends CopyableComponent[BodyComponent] with AutoSyncedComponent {
  def stats(part: BodyPart): LimbStats

  def setStats(part: BodyPart, stats: LimbStats): Unit
}
