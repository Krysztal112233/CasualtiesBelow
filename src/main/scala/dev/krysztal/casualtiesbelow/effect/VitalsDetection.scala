package dev.krysztal.casualtiesbelow.effect

import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.circulation.TotemHemostasis

private[effect] trait VitalsEffectSynchronizer {
  def synchronizeFromVitals(player: ServerPlayer): Unit
}

private[effect] type MobEffectAmplifierResolver = ServerPlayer => Option[Int]

private val TicksPerMinute = 1200.0

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
    CasualtiesBelowConfig.visuals.dirtinessBandGrimy.get()
  )

private[effect] val hypoxiaAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  amplifierBelowThreshold(
    player.vitals.circulation.bloodOxygen,
    CasualtiesBelowConfig.vitals.bloodOxygenHypoxiaThreshold.get()
  )

private[effect] val hypovolemiaAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  hypovolemiaAmplifier(
    player.vitals.circulation.bloodVolume,
    CasualtiesBelowConfig.vitals.maxBloodVolume.get()
  )

private[effect] val bloodLossAmplifierFromVitals: MobEffectAmplifierResolver = player =>
  if (player.isCreative || player.isSpectator) None
  else {
    bloodLossAmplifier(
      CasualtiesBelowConfig.vitals.maxBloodVolume.get(),
      currentEffectiveBleedingRate(player)
    )
  }

/** Sums the active limb rates with the same temporary hemostasis modifier used by circulation. */
private[effect] def currentEffectiveBleedingRate(player: ServerPlayer): Double = {
  val externalRate = BodyPart.values.iterator
    .map(part => player.body.stats(part).externalBleedingRate)
    .sum
  externalRate * TotemHemostasis.bleedingMultiplier(player.vitals)
}

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

/** Maps 10%, 20%, 30%, and 50% loss of healthy blood volume to amplifiers I through IV. */
private[effect] def hypovolemiaAmplifier(
    bloodVolume: Double,
    maximumBloodVolume: Double
): Option[Int] = {
  if (!bloodVolume.isFinite || !maximumBloodVolume.isFinite || maximumBloodVolume <= 0.0) {
    None
  } else {
    val volumeFraction = bloodVolume.max(0.0).min(maximumBloodVolume) / maximumBloodVolume
    if (volumeFraction > 0.9) None
    else if (volumeFraction > 0.8) Some(0)
    else if (volumeFraction > 0.7) Some(1)
    else if (volumeFraction > 0.5) Some(2)
    else Some(3)
  }
}

/** Maps the current external bleed rate to a constant-rate estimate from full to 20% blood volume.
  */
private[effect] def bloodLossAmplifier(
    maximumBloodVolume: Double,
    effectiveBleedingRatePerTick: Double
): Option[Int] = {
  if (
    !maximumBloodVolume.isFinite ||
    maximumBloodVolume <= 0.0 ||
    !effectiveBleedingRatePerTick.isFinite ||
    effectiveBleedingRatePerTick <= 0.0
  ) {
    None
  } else {
    val minutesToTwentyPercent =
      maximumBloodVolume * 0.8 / (effectiveBleedingRatePerTick * TicksPerMinute)
    if (!minutesToTwentyPercent.isFinite || minutesToTwentyPercent > 15.0) None
    else if (minutesToTwentyPercent >= 8.0) Some(0)
    else if (minutesToTwentyPercent >= 5.0) Some(1)
    else if (minutesToTwentyPercent >= 2.0) Some(2)
    else Some(3)
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
