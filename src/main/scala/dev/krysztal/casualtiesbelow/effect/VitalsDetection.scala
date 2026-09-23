package dev.krysztal.casualtiesbelow.effect

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
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

private[effect] val painShockAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  painShockAmplifierFromStage(player.vitals.shock.stage)

private[effect] val alertnessAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierAboveThreshold(player.vitals.adrenaline, 0.0)

private[effect] val wetnessAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierAboveThreshold(player.vitals.wetness, 0.0)

private[effect] val dirtinessAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierAtOrAboveThreshold(
    player.vitals.dirtiness,
    CasualtiesBelowConfig.dirtiness.bandGrimy.get()
  )

private[effect] val hypoxiaAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierBelowThreshold(
    player.vitals.circulation.bloodOxygen,
    CasualtiesBelowConfig.vitals.bloodOxygenHypoxiaThreshold.get()
  )

/** Activates alongside the existing blood-desaturation warning, normalized to healthy volume. */
private[effect] val bloodLossAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  bloodLossAmplifier(
    player.vitals.circulation.bloodVolume,
    CasualtiesBelowConfig.vitals.maxBloodVolume.get(),
    CasualtiesBelowConfig.vitals.bloodDesaturationStartFraction.get()
  )

private[effect] val sepsisAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierAboveThreshold(player.vitals.infection.sepsis, 0.0)

private[effect] val hypothermiaAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierBelowThreshold(
    player.vitals.bodyTemperature,
    CasualtiesBelowConfig.temperature.penaltyBandLowCelsius.get()
  )

private[effect] val hyperthermiaAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierAboveThreshold(
    player.vitals.bodyTemperature,
    CasualtiesBelowConfig.temperature.penaltyBandHighCelsius.get()
  )

private[effect] val unconsciousnessAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierWhen(player.vitals.consciousness.unconscious)

private[effect] def painShockAmplifierFromStage(stage: PainShockStage): Option[Int] =
  amplifierWhen(stage != PainShockStage.Stable)

private[effect] def amplifierWhen(active: Boolean): Option[Int] = {
  if (active) Some(0) else None
}

private[effect] def amplifierBelowThreshold(value: Double, threshold: Double): Option[Int] = {
  if (!value.isFinite || !threshold.isFinite) None
  else amplifierWhen(value < threshold)
}

private[effect] def amplifierAboveThreshold(value: Double, threshold: Double): Option[Int] = {
  if (!value.isFinite || !threshold.isFinite) None
  else amplifierWhen(value > threshold)
}

private[effect] def amplifierAtOrAboveThreshold(value: Double, threshold: Double): Option[Int] = {
  if (!value.isFinite || !threshold.isFinite) None
  else amplifierWhen(value >= threshold)
}

private[effect] def bloodLossAmplifier(
    bloodVolume: Double,
    maximumBloodVolume: Double,
    thresholdFraction: Double
): Option[Int] = {
  if (
    !bloodVolume.isFinite ||
    !maximumBloodVolume.isFinite ||
    maximumBloodVolume <= 0.0 ||
    !thresholdFraction.isFinite
  ) {
    None
  } else {
    val normalizedVolume = bloodVolume.max(0.0).min(maximumBloodVolume)
    amplifierBelowThreshold(normalizedVolume / maximumBloodVolume, thresholdFraction)
  }
}

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
