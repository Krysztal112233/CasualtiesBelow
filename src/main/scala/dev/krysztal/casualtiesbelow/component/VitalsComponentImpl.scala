package dev.krysztal.casualtiesbelow.component

import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import net.minecraft.core.HolderLookup
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

import dev.krysztal.casualtiesbelow.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.adrenaline.AdrenalineState
import dev.krysztal.casualtiesbelow.api.body.vitals.CirculationSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.InfectionSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.ShockSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.bleeding.TotemHemostasis
import dev.krysztal.casualtiesbelow.blood.CirculationState
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.pain.PainShock
import dev.krysztal.casualtiesbelow.progression.ConsciousnessProgression
import dev.krysztal.casualtiesbelow.progression.HypoxiaProgression

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

final class VitalsComponentImpl(val player: Player)
    extends VitalsComponent
    with CopyableComponent[VitalsComponent]
    with AutoSyncedComponent {
  private var infectionState: InfectionSnapshot =
    InfectionSnapshot(CasualtiesBelowConfig.MaxImmuneHealth.get(), 0.0)
  private var consciousnessState: ConsciousnessSnapshot =
    ConsciousnessSnapshot(VitalsComponent.MaxValue, unconscious = false)
  private var shockState: ShockSnapshot = ShockSnapshot(0.0, PainShockStage.Stable)
  private var adrenalineState: AdrenalineState = AdrenalineState.Empty
  private var circulationState: CirculationState = CirculationState(
    VitalsComponent.MaxBloodOxygen,
    CasualtiesBelowConfig.MaxBloodVolume.get(),
    hypoxiaExposureTicks = 0,
    totemHemostasisTicks = 0
  )
  private var discomfortState: Double = 0.0

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    val source = other match {
      case component: VitalsComponentImpl => component
      case component                      =>
        throw IllegalArgumentException(
          s"Unexpected vitals component implementation: ${component.getClass.getName}"
        )
    }
    setImmuneHealth(source.infection.immuneHealth)
    val normalizedAdrenaline =
      if (player.level().isClientSide()) {
        Adrenaline.normalizeSyncedState(source.adrenaline, source.adrenalineGraceTicks)
      } else {
        Adrenaline.normalizeStoredState(source.adrenaline, source.adrenalineGraceTicks)
      }
    applyAdrenalineState(normalizedAdrenaline)
    val normalizedShock =
      if (player.level().isClientSide()) {
        PainShock.normalizeSyncedState(other.shock.load, other.shock.stage)
      } else {
        PainShock.normalizeStoredState(
          other.shock.load,
          other.shock.stage,
          Some(source.consciousness.unconscious),
          normalizedAdrenaline.amount
        )
      }
    applyShockState(normalizedShock)
    val normalizedConsciousness =
      if (player.level().isClientSide()) {
        ConsciousnessProgression.normalizeSyncedState(
          source.consciousness.level,
          Some(source.consciousness.unconscious),
          normalizedShock.stage
        )
      } else {
        ConsciousnessProgression.normalizeStoredState(
          source.consciousness.level,
          Some(source.consciousness.unconscious),
          CasualtiesBelowConfig.effectiveConsciousnessFloor,
          CasualtiesBelowConfig.effectiveConsciousnessKnockoutThreshold,
          normalizedShock.stage
        )
      }
    applyConsciousnessState(normalizedConsciousness)
    setBloodOxygen(source.circulation.bloodOxygen)
    setBloodVolume(source.circulation.bloodVolume)
    applyHypoxiaExposureTicks(
      HypoxiaProgression.normalizeExposureTicks(source.hypoxiaExposureTicks)
    )
    applyTotemHemostasisTicks(
      TotemHemostasis.normalizeRemainingTicks(source.totemHemostasisTicks)
    )
    setSepsis(source.infection.sepsis)
    setDiscomfort(source.discomfort)
  }

  override def infection: InfectionSnapshot = infectionState

  private[casualtiesbelow] def setImmuneHealth(value: Double): Unit = {
    infectionState = infectionState.copy(
      immuneHealth = bounded(value, CasualtiesBelowConfig.MaxImmuneHealth.get())
    )
  }

  override def consciousness: ConsciousnessSnapshot = consciousnessState

  private[casualtiesbelow] def applyConsciousnessState(state: ConsciousnessSnapshot): Unit = {
    consciousnessState = state
  }

  override def shock: ShockSnapshot = shockState

  private[casualtiesbelow] def applyShockState(state: ShockSnapshot): Unit = {
    shockState = state
  }

  override def adrenaline: Double = adrenalineState.amount

  private[casualtiesbelow] def adrenalineGraceTicks: Int = adrenalineState.graceTicks

  private[casualtiesbelow] def adrenalineReserve: AdrenalineState = adrenalineState

  private[casualtiesbelow] def applyAdrenalineState(state: AdrenalineState): Unit = {
    adrenalineState = state
  }

  private[casualtiesbelow] def hypoxiaExposureTicks: Int = circulationState.hypoxiaExposureTicks

  private[casualtiesbelow] def applyHypoxiaExposureTicks(ticks: Int): Unit = {
    circulationState = circulationState.copy(hypoxiaExposureTicks = ticks)
  }

  private[casualtiesbelow] def totemHemostasisTicks: Int = circulationState.totemHemostasisTicks

  private[casualtiesbelow] def applyTotemHemostasisTicks(ticks: Int): Unit = {
    circulationState = circulationState.copy(totemHemostasisTicks = ticks)
  }

  override def circulation: CirculationSnapshot = circulationState.snapshot

  private[casualtiesbelow] def setBloodOxygen(value: Double): Unit = {
    circulationState =
      circulationState.copy(bloodOxygen = bounded(value, VitalsComponent.MaxBloodOxygen))
  }

  private[casualtiesbelow] def setBloodVolume(value: Double): Unit = {
    circulationState = circulationState.copy(
      bloodVolume = bounded(value, CasualtiesBelowConfig.MaxBloodVolume.get())
    )
  }

  private[casualtiesbelow] def setSepsis(value: Double): Unit = {
    infectionState =
      infectionState.copy(sepsis = bounded(value, CasualtiesBelowConfig.MaxSepsis.get()))
  }

  override def discomfort: Double = discomfortState

  private[casualtiesbelow] def setDiscomfort(value: Double): Unit = {
    discomfortState = bounded(value, CasualtiesBelowConfig.MaxDiscomfort.get())
  }

  override def shouldSyncWith(recipient: ServerPlayer): Boolean = recipient eq player

  override def writeData(out: ValueOutput): Unit = {
    out.putDouble(VitalsComponentImpl.ImmuneHealthKey, infectionState.immuneHealth)
    out.putDouble(VitalsComponentImpl.ConsciousnessKey, consciousnessState.level)
    out.putBoolean(VitalsComponentImpl.UnconsciousKey, consciousnessState.unconscious)
    out.putDouble(VitalsComponentImpl.AdrenalineKey, adrenalineState.amount)
    out.putInt(VitalsComponentImpl.AdrenalineGraceTicksKey, adrenalineState.graceTicks)
    out.putDouble(VitalsComponentImpl.PainShockLoadKey, shockState.load)
    out.putString(VitalsComponentImpl.PainShockStageKey, shockState.stage.id)
    out.putDouble(VitalsComponentImpl.BloodOxygenKey, circulationState.bloodOxygen)
    out.putDouble(VitalsComponentImpl.BloodVolumeKey, circulationState.bloodVolume)
    out.putInt(VitalsComponentImpl.HypoxiaExposureTicksKey, circulationState.hypoxiaExposureTicks)
    out.putInt(VitalsComponentImpl.TotemHemostasisTicksKey, circulationState.totemHemostasisTicks)
    out.putDouble(VitalsComponentImpl.SepsisKey, infectionState.sepsis)
    out.putDouble(VitalsComponentImpl.DiscomfortKey, discomfort)
  }

  override def readData(in: ValueInput): Unit = {
    setImmuneHealth(
      in.getDoubleOr(
        VitalsComponentImpl.ImmuneHealthKey,
        CasualtiesBelowConfig.MaxImmuneHealth.get()
      )
    )
    val savedUnconscious =
      in.read(VitalsComponentImpl.UnconsciousKey, Codec.BOOL).toScala.map(_.booleanValue)
    val normalizedAdrenaline =
      if (player.level().isClientSide()) {
        Adrenaline.normalizeSyncedState(
          in.getDoubleOr(VitalsComponentImpl.AdrenalineKey, 0.0),
          in.getIntOr(VitalsComponentImpl.AdrenalineGraceTicksKey, 0)
        )
      } else {
        Adrenaline.normalizeStoredState(
          in.getDoubleOr(VitalsComponentImpl.AdrenalineKey, 0.0),
          in.getIntOr(VitalsComponentImpl.AdrenalineGraceTicksKey, 0)
        )
      }
    applyAdrenalineState(normalizedAdrenaline)
    val savedShockStage = PainShockStage
      .fromId(in.getStringOr(VitalsComponentImpl.PainShockStageKey, PainShockStage.Stable.id))
      .toScala
      .getOrElse(PainShockStage.Stable)
    val normalizedShock =
      if (player.level().isClientSide()) {
        PainShock.normalizeSyncedState(
          in.getDoubleOr(VitalsComponentImpl.PainShockLoadKey, 0.0),
          savedShockStage
        )
      } else {
        PainShock.normalizeStoredState(
          in.getDoubleOr(VitalsComponentImpl.PainShockLoadKey, 0.0),
          savedShockStage,
          savedUnconscious,
          normalizedAdrenaline.amount
        )
      }
    applyShockState(normalizedShock)
    val savedConsciousness =
      in.getDoubleOr(VitalsComponentImpl.ConsciousnessKey, VitalsComponent.MaxValue)
    val normalizedConsciousness =
      if (player.level().isClientSide()) {
        ConsciousnessProgression.normalizeSyncedState(
          savedConsciousness,
          savedUnconscious,
          normalizedShock.stage
        )
      } else {
        ConsciousnessProgression.normalizeStoredState(
          savedConsciousness,
          savedUnconscious,
          CasualtiesBelowConfig.effectiveConsciousnessFloor,
          CasualtiesBelowConfig.effectiveConsciousnessKnockoutThreshold,
          normalizedShock.stage
        )
      }
    applyConsciousnessState(normalizedConsciousness)
    setBloodOxygen(
      in.getDoubleOr(
        VitalsComponentImpl.BloodOxygenKey,
        VitalsComponent.MaxBloodOxygen
      )
    )
    setBloodVolume(
      in.getDoubleOr(VitalsComponentImpl.BloodVolumeKey, CasualtiesBelowConfig.MaxBloodVolume.get())
    )
    applyHypoxiaExposureTicks(
      HypoxiaProgression.normalizeExposureTicks(
        in.getIntOr(VitalsComponentImpl.HypoxiaExposureTicksKey, 0)
      )
    )
    applyTotemHemostasisTicks(
      TotemHemostasis.normalizeRemainingTicks(
        in.getIntOr(VitalsComponentImpl.TotemHemostasisTicksKey, 0)
      )
    )
    setSepsis(in.getDoubleOr(VitalsComponentImpl.SepsisKey, 0.0))
    setDiscomfort(in.getDoubleOr(VitalsComponentImpl.DiscomfortKey, 0.0))
  }

  private def bounded(value: Double, maximum: Double): Double = {
    val limit = if (maximum.isFinite) maximum.max(0.0) else Double.MaxValue
    if (value == Double.PositiveInfinity) limit
    else if (value.isFinite) value.max(0.0).min(limit)
    else 0.0
  }
}

object VitalsComponentImpl {

  // NBT keys (serialization implementation detail; not part of the public API)
  private val ImmuneHealthKey = "immune_health"
  private val ConsciousnessKey = "consciousness"
  private val UnconsciousKey = "unconscious"
  private val AdrenalineKey = "adrenaline"
  private val AdrenalineGraceTicksKey = "adrenaline_grace_ticks"
  private val PainShockLoadKey = "pain_shock_load"
  private val PainShockStageKey = "pain_shock_stage"
  private val BloodOxygenKey = "blood_oxygen"
  private val BloodVolumeKey = "blood_volume"
  private val HypoxiaExposureTicksKey = "hypoxia_exposure_ticks"
  private val TotemHemostasisTicksKey = "totem_hemostasis_ticks"
  private val SepsisKey = "sepsis"
  private val DiscomfortKey = "discomfort"
}
