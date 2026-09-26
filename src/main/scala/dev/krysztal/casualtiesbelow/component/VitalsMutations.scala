package dev.krysztal.casualtiesbelow.component

import java.util.Collections
import java.util.WeakHashMap

import net.minecraft.world.entity.player.Player

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.internal.TypeAlias.JBoolean

/** Tick-coalesced owner sync for vitals. Setters on [[VitalsComponentImpl]] queue the player here
  * on any actual change; the flush sends one packet per dirty player per tick at most, no matter
  * how many producers fired. Producers never do sync bookkeeping of their own.
  */
object VitalsMutations {
  private val DirtyPlayers =
    Collections.newSetFromMap(new WeakHashMap[Player, JBoolean]())

  def register(): Unit =
    ServerTickEvents.END_SERVER_TICK.register { _ => flushDirty() }

  /** Queues a vitals sync for the next tick end. No-op on the client, where writes happen while
    * applying the server's packet and must not echo back.
    */
  private[casualtiesbelow] def markDirty(player: Player): Unit = {
    if (!player.level().isClientSide) DirtyPlayers.add(player)
  }

  private def flushDirty(): Unit = {
    if (DirtyPlayers.isEmpty) return
    DirtyPlayers.forEach { player =>
      if (!player.isRemoved) CasualtiesBelowComponents.Vitals.sync(player)
    }
    DirtyPlayers.clear()
  }
}
