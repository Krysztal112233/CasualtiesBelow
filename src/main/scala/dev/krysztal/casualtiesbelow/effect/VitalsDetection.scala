package dev.krysztal.casualtiesbelow.effect

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*

private[effect] trait VitalsEffectSynchronizer {
  def synchronizeFromVitals(player: ServerPlayer): Unit
}

private[effect] type MobEffectAmplifierResolver = ServerPlayer => Option[Int]

private[effect] val opioidAnalgesiaAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierForValue(player.vitals.opioidLevel, VitalsComponent.MaxOpioidLevel, tierCount = 5)

private[effect] val opioidDependenceAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierForValue(
    player.vitals.opioidDependence,
    VitalsComponent.MaxOpioidDependence,
    tierCount = 3
  )

/** Returns a zero-based vanilla amplifier for an equal-width band, or `None` when inactive. */
private[effect] def amplifierForValue(
    value: Double,
    maximum: Double,
    tierCount: Int
): Option[Int] = {
  if (!value.isFinite || value <= 0.0 || !maximum.isFinite || maximum <= 0.0 || tierCount <= 0) {
    return None
  }

  val boundedValue = value.min(maximum)
  val zeroBasedTier = math.ceil(boundedValue / maximum * tierCount).toInt - 1

  Some(zeroBasedTier.max(0).min(tierCount - 1))
}
