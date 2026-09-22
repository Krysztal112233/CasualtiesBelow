package dev.krysztal.casualtiesbelow.physiology.limb

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart

/** Client-side blood droplets for actively bleeding limbs.
  *
  * Purely presentational: every client tick, each loaded player's *synced* body component is read
  * and every limb with a positive external bleeding rate emits deep-red dust particles at the
  * limb's approximate position, with a per-tick spawn probability proportional to the rate (heavier
  * bleeding drips faster). No server involvement — the server stays authoritative and this only
  * visualizes state the client already has.
  */
@Environment(EnvType.CLIENT)
object BleedingParticles {

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
    val body = CasualtiesBelowComponents.Body.get(player)
    val random = player.getRandom
    BodyPart.values.foreach { part =>
      val rate = body.stats(part).externalBleedingRate
      if (rate > 0.0 && random.nextFloat() < (rate * SpawnChancePerRate).min(1.0)) {
        spawnDroplet(level, player, part)
      }
    }
  }

  /** Emits one droplet at the limb's approximate position: a height fraction of the bounding box
    * plus a sideways offset for arms/legs, rotated into the body yaw so the particles track the
    * turned body rather than the view.
    */
  private def spawnDroplet(level: ClientLevel, player: Player, part: BodyPart): Unit = {
    val random = player.getRandom
    val (heightFraction, sideways) = part match {
      case BodyPart.Head     => (0.92, 0.0)
      case BodyPart.Torso    => (0.65, 0.0)
      case BodyPart.ArmLeft  => (0.72, 0.3)
      case BodyPart.ArmRight => (0.72, -0.3)
      case BodyPart.LegLeft  => (0.35, 0.12)
      case BodyPart.LegRight => (0.35, -0.12)
    }
    // The body's anatomical left in world space for the current yaw: up × forward.
    val yawRad = player.yBodyRot * Mth.DEG_TO_RAD
    val x = player.getX + sideways * Mth.cos(yawRad) + (random.nextDouble() - 0.5) * Spread
    val y = player.getY + player.getBbHeight * heightFraction
    val z = player.getZ + sideways * Mth.sin(yawRad) + (random.nextDouble() - 0.5) * Spread
    level.addParticle(
      BloodDust,
      x,
      y,
      z,
      (random.nextDouble() - 0.5) * 0.04,
      FallSpeed,
      (random.nextDouble() - 0.5) * 0.04
    )
  }

  /** Per-tick spawn chance per 1.0 mL/tick of bleeding rate (1.0 → a fully bleeding limb drips ~20
    * particles/s, a fresh sword cut ~4/s); dust particles are short-lived and small, so the rate
    * needs to be dense to read at a distance.
    */
  private val SpawnChancePerRate = 1.0

  /** Deep blood red dust, slightly larger than vanilla redstone dust for readability. */
  private val BloodDust = new DustParticleOptions(0x8a0f0f, 1.3f)

  /** Horizontal scatter around the limb anchor, in blocks. */
  private val Spread = 0.2

  /** Slow downward drift so droplets read as dripping rather than floating. */
  private val FallSpeed = -0.05
}
