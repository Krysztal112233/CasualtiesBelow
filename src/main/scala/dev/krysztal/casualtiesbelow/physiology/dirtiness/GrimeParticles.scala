package dev.krysztal.casualtiesbelow.physiology.dirtiness

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.world.entity.player.Player

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig

/** Client-side grime motes for squalid players.
  *
  * Purely presentational, mirroring the bleeding droplets: every client tick, each loaded player's
  * *synced* vitals are read and a player past the squalid band occasionally sheds a brown dust mote
  * that drifts down past the head. Dirtiness is owner-synced, so in practice only the local player
  * ever displays motes. No server involvement.
  */
@Environment(EnvType.CLIENT)
object GrimeParticles {

  /** Registers the client tick hook. Called once from the client initializer. */
  def register(): Unit =
    ClientTickEvents.END_CLIENT_TICK.register { client =>
      Option(client.level).foreach { level =>
        level.players().forEach { player =>
          if (player.isAlive && !player.isSpectator) {
            spawnForPlayer(level, player)
          }
        }
      }
    }

  private def spawnForPlayer(level: ClientLevel, player: Player): Unit = {
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    spawnWashOff(level, player, vitals.dirtiness)
    if (vitals.dirtiness < CasualtiesBelowConfig.dirtiness.bandSqualid.get()) return
    if (player.getRandom.nextFloat() >= SpawnChancePerTick) return

    val random = player.getRandom
    level.addParticle(
      GrimeDust,
      player.getX + (random.nextDouble() - 0.5) * Spread,
      player.getY + player.getBbHeight + 0.1,
      player.getZ + (random.nextDouble() - 0.5) * Spread,
      (random.nextDouble() - 0.5) * 0.02,
      FallSpeed,
      (random.nextDouble() - 0.5) * 0.02
    )
  }

  /** Dirt shedding off a wet player: the visible confirmation that washing is working. */
  private def spawnWashOff(level: ClientLevel, player: Player, dirtiness: Double): Unit = {
    if (dirtiness <= 0.0 || !player.isInWater) return
    val random = player.getRandom
    if (random.nextFloat() >= WashOffChancePerTick) return

    level.addParticle(
      GrimeDust,
      player.getX + (random.nextDouble() - 0.5) * Spread,
      player.getY + random.nextDouble() * player.getBbHeight,
      player.getZ + (random.nextDouble() - 0.5) * Spread,
      (random.nextDouble() - 0.5) * 0.02,
      FallSpeed,
      (random.nextDouble() - 0.5) * 0.02
    )
  }

  /** Per-tick chance of a wash-off mote while a dirty player is in water (~4/s). */
  private val WashOffChancePerTick = 0.2f

  /** Per-tick spawn chance past the squalid band (~6 motes/s: clearly noticeable). */
  private val SpawnChancePerTick = 0.3f

  /** Murky brown dust: reads as grime, distinct from the deep-red blood droplets. */
  private val GrimeDust = new DustParticleOptions(0x4a3621, 1.0f)

  /** Horizontal scatter around the player axis, in blocks. */
  private val Spread = 0.5

  /** Slow downward drift, as if shaken loose and settling. */
  private val FallSpeed = -0.03
}
