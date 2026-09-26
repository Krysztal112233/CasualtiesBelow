package dev.krysztal.casualtiesbelow.component

import java.util.Collections
import java.util.WeakHashMap

import net.minecraft.world.entity.player.Player

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.internal.TypeAlias.JBoolean
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*

private[casualtiesbelow] final case class BodyMutation(
    before: LimbSnapshot,
    after: LimbSnapshot,
    changed: Boolean
)

/** Single commit point for mutable limb state, derived movement attributes, and body syncs. */
object BodyMutations {
  private val DirtyPlayers =
    Collections.newSetFromMap(new WeakHashMap[Player, JBoolean]())

  def register(): Unit =
    ServerTickEvents.END_SERVER_TICK.register { _ => flushDirty() }

  private[casualtiesbelow] def mutate(
      player: Player,
      part: BodyPart,
      markDirty: Boolean = true
  )(operation: MutableLimbState => Unit): BodyMutation = {
    val body = player.body
    val current = body.mutableCopy(part)
    val before = current.snapshot
    val updated = current.copy()
    operation(updated)
    val normalized = MutableLimbState.normalize(updated)
    val changed = normalized != current
    if (changed) {
      body.replace(part, normalized)
      if (markDirty) DirtyPlayers.add(player)
    }
    BodyMutation(before, normalized.snapshot, changed)
  }

  private[casualtiesbelow] def replace(
      player: Player,
      part: BodyPart,
      state: MutableLimbState,
      markDirty: Boolean = true
  ): BodyMutation = {
    val body = player.body
    val current = body.mutableCopy(part)
    val normalized = MutableLimbState.normalize(state)
    val changed = normalized != current
    if (changed) {
      body.replace(part, normalized)
      if (markDirty) DirtyPlayers.add(player)
    }
    BodyMutation(current.snapshot, normalized.snapshot, changed)
  }

  private[casualtiesbelow] def markDirty(player: Player): Unit = DirtyPlayers.add(player)

  private[casualtiesbelow] def reset(player: Player): Unit = {
    val body = player.body
    BodyPart.values.foreach { part => body.replace(part, MutableLimbState()) }
    DirtyPlayers.add(player)
  }

  private[casualtiesbelow] def reconcileMovementModifiers(player: Player): Unit =
    player.body.reconcileMovementModifiers()

  private def flushDirty(): Unit = {
    if (DirtyPlayers.isEmpty) return
    DirtyPlayers.forEach { player =>
      if (!player.isRemoved) CasualtiesBelowComponents.Body.sync(player)
    }
    DirtyPlayers.clear()
  }
}
