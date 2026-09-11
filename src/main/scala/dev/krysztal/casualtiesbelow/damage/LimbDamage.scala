package dev.krysztal.casualtiesbelow.damage

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.event.TraumaStartedCallback
import dev.krysztal.casualtiesbelow.api.event.TraumaStartedContext
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores
import dev.krysztal.casualtiesbelow.internal.data.WoundProfiles
import dev.krysztal.casualtiesbelow.physiology.adrenaline.AdrenalinePain
import dev.krysztal.casualtiesbelow.physiology.adrenaline.AdrenalineRules
import dev.krysztal.casualtiesbelow.physiology.progression.StarvationProgression

/** Classifies incoming damage and delegates every matched rule to the central wound executor. */
object LimbDamage {

  def register(): Unit = {
    ServerLivingEntityEvents.AFTER_DAMAGE.register(afterDamage)
  }

  /** Invoked after a living entity takes damage, before armor/enchantment reduction, and only when
    * the entity survives. Server-side only (`LivingEntity.hurtServer`).
    */
  private val afterDamage: ServerLivingEntityEvents.AfterDamage =
    (entity, source, _, damageTaken, blocked) => onAfterDamage(entity, source, damageTaken, blocked)

  private def onAfterDamage(
      entity: LivingEntity,
      source: DamageSource,
      damageTaken: Float,
      blocked: Boolean
  ): Unit = {
    val player = entity match {
      case player: ServerPlayer => player
      case _                    => return
    }
    if (player.isCreative || player.isSpectator || !player.isAlive || damageTaken <= 0) return

    // NOTE: Capture one installed generation for the whole event so adrenaline, rule resolution,
    // hit location, and armor cannot observe different datapack generations.
    val gameplayData = GameplayDataStores.state(player.level().getServer)

    TraumaStartedCallback.EVENT
      .invoker()
      .onTraumaStarted(
        new TraumaStartedContext(player, source, damageTaken.toDouble, woundsAllowed = !blocked)
      )

    // Adrenaline is an event-level response, not a wound application: scatter, paired impacts, and
    // multiple wound contributions must never multiply it. A partial shield block may retain
    // positive damageTaken and still stimulates; a complete block reaches this event with zero.
    AdrenalineRules.grantFor(player, source, gameplayData.store)
    val painMultiplier =
      AdrenalinePain.currentMultiplier(CasualtiesBelowComponents.Vitals.get(player))

    // Preserve the existing wound/starvation contract, which excludes every blocked hit.
    if (blocked) return

    StarvationProgression.onAfterDamage(player, source, damageTaken)
    WoundProfiles
      .classify(player.level(), player, source, gameplayData.woundRules)
      .foreach(rule =>
        WoundApplications.executeWithPainMultiplier(
          player,
          source,
          damageTaken.toDouble,
          rule,
          painMultiplier,
          gameplayData.store
        )
      )
  }
}
