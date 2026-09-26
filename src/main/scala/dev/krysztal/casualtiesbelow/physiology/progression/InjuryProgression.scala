package dev.krysztal.casualtiesbelow.physiology.progression

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.physiology.circulation.Circulation
import dev.krysztal.casualtiesbelow.physiology.consciousness.Consciousness
import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.physiology.infection.Infection
import dev.krysztal.casualtiesbelow.physiology.limb.Limb
import dev.krysztal.casualtiesbelow.physiology.nutrition.Nutrition
import dev.krysztal.casualtiesbelow.physiology.opioid.Opioid
import dev.krysztal.casualtiesbelow.physiology.pain.PainShock
import dev.krysztal.casualtiesbelow.physiology.temperature.Temperature

/** Time evolution of injuries: what heals, what worsens, and what kills when left alone.
  *
  * Per server tick and per player (skipped in creative/spectator):
  *
  *   - fractures count down their recovery time and heal when it runs out; walking on a fractured
  *     or dislocated leg strains it, granting pain scaled by the leg's tissue damage (muscle and
  *     skin) at the configured rate
  *   - bleeding drains the blood volume and clots linearly; the per-limb rate is capped
  *     proportionally to the skin damage (the rate is capped by skin damage); reaching zero blood
  *     is fatal ([[CasualtiesBelowDamageTypes.BloodLoss]]), while a successful death-protection
  *     rescue restores a bounded blood reserve and temporarily reduces actual drain without closing
  *     wounds (see [[Circulation]])
  *   - exhausted vanilla air gates blood-oxygen depletion; moderate blood loss retains full
  *     carrying capacity, then capacity falls linearly below its configured fraction; current
  *     oxygen sets a hard consciousness ceiling, while independent knockout/wake hysteresis owns
  *     the recoverable unconscious state (see [[Circulation]], [[Consciousness]])
  *   - wounds with meaningful skin damage can get infected; the immune system fights infections
  *     with its total capacity split across all infected limbs, against per-limb spread rates
  *     proportional to its complement; past a progress ramp an infection can also seed adjacent
  *     limbs (see [[Limb]]), and it scales skin regrowth (see [[Limb]])
  *   - immune health is a lifestyle stat decoupled from infection: a full stomach restores it,
  *     while hunger and active vanilla Poison drain it (see [[Infection]])
  *   - skin naturally regrows once a wound has clotted shut; vanilla Regeneration adds micro-repair
  *     even while bleeding and tightens the bleeding cap as the skin closes; muscle regrows
  *     regardless (slower)
  *   - pain decays linearly at the configured rate; whole-body pain above the shock threshold
  *     accumulates hidden load, which can force literal-zero unconsciousness before later
  *     permitting recovery (see [[PainShock]])
  *
  * Dislocations never self-heal — they need treatment (not yet implemented).
  *
  * Healing is not an injury: it bypasses [[dev.krysztal.casualtiesbelow.damage.LimbInjuryService]]
  * (no injury event and no jitter) and uses the internal body mutation authority directly. Sync
  * authority lives in the components: any actual vitals change queues the owner for the unified
  * end-tick sync, while body mutations flow through their own throttled dirty flush (continuous
  * changes once per [[SyncIntervalTicks]], discrete transitions immediately). Registration order
  * keeps this pipeline ahead of the components' end-of-tick dirty flushes, so its mutations ship in
  * the same tick.
  *
  * Technical balance constants live in [[dev.krysztal.casualtiesbelow.internal.Consts]]; only a few
  * player-facing gameplay choices remain configurable.
  */
object InjuryProgression {

  def register(): Unit = {
    // CCA's LOSSLESS_ONLY strategy copies injuries during lossless reconstruction (for example an
    // End return) and leaves ordinary death respawns at their fresh component defaults.
    ServerTickEvents.END_SERVER_TICK.register(tickServer)
  }

  private def tickServer(server: MinecraftServer): Unit = {
    ticks += 1
    val syncTick = ticks % SyncIntervalTicks == 0
    try {
      server.getPlayerList.getPlayers.forEach { player =>
        tickPlayer(player, syncTick)
      }
    } finally {
      // No player references are retained, and entries for players who disconnected during this
      // tick cannot leak into a later tick.
      Nutrition.discardRemaining()
    }
  }

  /** Advances an isolated GameTest player that is not registered in the server player list. */
  private[casualtiesbelow] def tickForGameTest(player: ServerPlayer): Unit =
    tickPlayer(player, syncTick = false)

  private def tickPlayer(player: ServerPlayer, syncTick: Boolean): Unit = {
    if (!player.isAlive) {
      Nutrition.discard(player)
      Temperature.discard(player)
      return
    }
    if (player.isCreative || player.isSpectator) {
      Nutrition.discard(player)
      Temperature.discard(player)
      // Physiology remains frozen in creative/spectator.
      return
    }

    val body = CasualtiesBelowComponents.Body.get(player)
    val vitals = player.vitals
    Opioid.tick(vitals)
    // One limb pass: per-limb evolution (fractures, clotting, infections, regrowth, pain) plus
    // septic seeding of adjacent limbs; its totals drive the circulation and infection stages.
    val limb = Limb.tick(player, body, vitals, syncTick)

    // A fresh AFTER_DAMAGE stimulus carries a one-tick sentinel, so aging here preserves its full
    // amount for this tick's shock decision. Later decay can contract the effective threshold and
    // collapse Deferred in this same pass.
    Adrenaline.tick(player, vitals)

    // Pain shock reads the fully updated per-limb pains and current adrenaline.
    PainShock.tick(player, body, vitals)
    Infection.tick(vitals, limb.infectionLoad, player)

    // One circulation pass: the sepsis-compressed blood cap, fed regeneration, starvation pulses,
    // bleeding drain scaled by totem hemostasis, and the zero-blood fatality check (which applies
    // its own damage and reports back so this pass can stop for the player).
    if (Circulation.tick(player, vitals, limb.totalBleeding)) {
      return
    }

    // Read vanilla's already-updated air supply after the blood changes above: blood volume sets
    // oxygen capacity, while fully exhausted air gates depletion. Consciousness progression then
    // consumes that reserve and owns both the scalar and the recoverable unconscious latch.
    val oxygen = Circulation.tickOxygen(player, vitals)
    Consciousness.tick(player, vitals)

    // Body temperature runs off the same per-player pass; its own module doc lays out the
    // approach/equilibrium recurrence and the heat contribution events.
    Temperature.tick(player, vitals)

    // Terminal exposure starts only after oxygen and consciousness consumed this tick's breathing
    // state. A successful death-protection hit restores physiology synchronously; either way this
    // player's progression returns immediately after the fatal call.
    val hypoxia = Circulation.tickHypoxia(vitals, oxygen.respirationFailed)
    if (hypoxia.fatal) {
      player.hurtServer(
        player.level(),
        CasualtiesBelowDamageTypes.hypoxia(player.level()),
        Float.MaxValue
      )
      return
    }

    PainShock.finishRecovery(player, vitals)
    Unconsciousness.tickMovementRestriction(player)
  }

  private var ticks = 0

  /** Ticks between throttled syncs of continuous changes (20 = once per second). */
  private val SyncIntervalTicks = 20

}
