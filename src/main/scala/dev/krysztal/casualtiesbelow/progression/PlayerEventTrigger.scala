package dev.krysztal.casualtiesbelow.progression

import java.util.Optional

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.advancements.predicates.ContextAwarePredicate
import net.minecraft.advancements.triggers.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer

/** A conditionless player trigger registered under one of the mod's own ids: firing it awards every
  * listening criterion unconditionally, because the firing event itself is the whole condition.
  * Vanilla reuses `PlayerTrigger` the same way for `tick`, `slept_in_bed` and friends; a dedicated
  * class keeps the mod's trigger semantics self-documenting.
  */
final class PlayerEventTrigger extends SimpleCriterionTrigger[PlayerEventTrigger.Instance] {
  override def codec(): Codec[PlayerEventTrigger.Instance] = PlayerEventTrigger.InstanceCodec

  /** Awards every listening criterion unconditionally. */
  def trigger(player: ServerPlayer): Unit = {
    trigger(player, (_: PlayerEventTrigger.Instance) => true)
  }
}

object PlayerEventTrigger {

  /** Empty conditions: the firing event carries all meaning, so the advancement JSON keeps an empty
    * `conditions` object.
    */
  final class Instance extends SimpleCriterionTrigger.SimpleInstance {
    override def player(): Optional[ContextAwarePredicate] = Optional.empty()
  }

  /** Encodes as an empty JSON object and decodes to a fresh instance. */
  private val InstanceCodec: Codec[Instance] = MapCodec.unitCodec(new Instance)
}
