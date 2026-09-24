package dev.krysztal.casualtiesbelow.progression

import java.util.WeakHashMap

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowDamageTypes
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidEffects

/** Server-side firing points for the mod's advancement triggers ([[CasualtiesBelowTriggers]]).
  *
  *   - opioid overdose is judged at death: the fatal damage must be terminal hypoxia while the
  *     player's opioid level was already forcing respiratory failure — drowning with opioids in the
  *     blood still counts, since the respiratory failure alone makes oxygen net-negative
  *   - consciousness minimum and the "Not Today" hemostasis episode are polled per player tick
  *     after physiology has run (registration order = event order), reading current-tick vitals
  *   - food-borne maximum discomfort is fired directly by `Discomfort.onFoodEaten`, the only place
  *     that knows eating caused the rise (see [[onFoodDiscomfortSettled]])
  *
  * Creative and spectator players are excluded everywhere, matching physiology.
  */
object AchievementHooks {

  /** Per-player "Not Today" episode state, keyed weakly: respawned players arrive as fresh
    * instances with fresh (zero) bleeding, so episodes never carry over a death.
    */
  private val hemostasisStates = new WeakHashMap[ServerPlayer, HemostasisEpisode.State]()

  def register(): Unit = {
    ServerLivingEntityEvents.AFTER_DEATH.register { (entity, source) =>
      entity match {
        case player: ServerPlayer => onPlayerDeath(player, source)
        case _                    => ()
      }
    }
    ServerTickEvents.END_SERVER_TICK.register { server =>
      server.getPlayerList.getPlayers.forEach(tickPlayer)
    }
  }

  private def onPlayerDeath(player: ServerPlayer, source: DamageSource): Unit = {
    if (player.isCreative || player.isSpectator) return
    if (!source.`is`(CasualtiesBelowDamageTypes.Hypoxia)) return
    val vitals = player.vitals
    val efficiency =
      OpioidEffects.respiratoryEfficiency(vitals.opioidLevel, vitals.opioidDependence)
    if (OpioidEffects.causesRespiratoryFailure(efficiency)) {
      CasualtiesBelowTriggers.OpioidOverdoseDeath.trigger(player)
    }
  }

  private def tickPlayer(player: ServerPlayer): Unit = {
    if (player.isCreative || player.isSpectator || !player.isAlive) {
      // Dying or switching mode mid-episode abandons it: "Not Today" demands a live stop.
      hemostasisStates.remove(player)
      return
    }
    if (player.vitals.consciousness.level <= 0.0) {
      CasualtiesBelowTriggers.ConsciousnessMinimum.trigger(player)
    }
    val body = CasualtiesBelowComponents.Body.get(player)
    val totalBleeding = BodyPart.values.map(part => body.stats(part).externalBleedingRate).sum
    if (totalBleeding > 0.0) {
      CasualtiesBelowTriggers.FirstBleeding.trigger(player)
    }
    tickHemostasis(player, totalBleeding)
  }

  private def tickHemostasis(player: ServerPlayer, totalBleeding: Double): Unit = {
    val nearMaxRate = CasualtiesBelowConfig.bleeding.maxExternalBleedingRate.get() *
      CasualtiesBelowConfig.bleeding.notTodayNearMaxBleedingFraction.get()
    val previous =
      Option(hemostasisStates.get(player)).getOrElse(HemostasisEpisode.State.Idle)
    val (next, completed) = HemostasisEpisode.next(previous, totalBleeding, nearMaxRate)
    if (next != previous) {
      hemostasisStates.put(player, next)
    }
    if (completed) {
      CasualtiesBelowTriggers.Hemostasis.trigger(player)
    }
  }

  /** Fires [[CasualtiesBelowTriggers.MaxDiscomfortFood]] when a just-eaten food has pushed
    * discomfort to the configured maximum. Called from `Discomfort.onFoodEaten`, after the dose
    * lands — the achievement is specifically about *eating* something revolting.
    */
  private[casualtiesbelow] def onFoodDiscomfortSettled(player: ServerPlayer): Unit = {
    if (player.vitals.discomfort >= CasualtiesBelowConfig.discomfort.maxValue.get()) {
      CasualtiesBelowTriggers.MaxDiscomfortFood.trigger(player)
    }
  }

  /** Advances an isolated GameTest player that is not registered in the server player list. */
  private[casualtiesbelow] def tickForGameTest(player: ServerPlayer): Unit = tickPlayer(player)
}
