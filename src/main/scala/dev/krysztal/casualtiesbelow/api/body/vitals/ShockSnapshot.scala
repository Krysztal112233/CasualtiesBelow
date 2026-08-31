package dev.krysztal.casualtiesbelow.api.body.vitals

/** Immutable, language-neutral view of one pain-shock episode: the hidden accumulated load and the
  * discrete phase it has escalated to.
  */
final case class ShockSnapshot(load: Double, stage: PainShockStage)
