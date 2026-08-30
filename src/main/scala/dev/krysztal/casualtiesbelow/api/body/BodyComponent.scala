package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v8.component.CardinalComponent

/** Read-only CCA view of every tracked limb. Mutations are server-authoritative domain operations;
  * callers cannot bypass normalization, derived attributes, synchronization, or events.
  */
trait BodyComponent extends CardinalComponent {
  def stats(part: BodyPart): LimbSnapshot
}
