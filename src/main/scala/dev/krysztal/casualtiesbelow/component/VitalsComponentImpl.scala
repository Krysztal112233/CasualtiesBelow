package dev.krysztal.casualtiesbelow.component

import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import net.minecraft.core.HolderLookup
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

import dev.krysztal.casualtiesbelow.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.api.body.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.bleeding.TotemHemostasis
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
  private var immuneHealthState: Double = CasualtiesBelowConfig.MaxImmuneHealth.get()
  private var consciousnessState: Double = VitalsComponent.MaxValue
  private var unconsciousState: Boolean = false
  private var painShockLoadState: Double = 0.0
  private var painShockStageState: PainShockStage = PainShockStage.Stable
  private var adrenalineState: Double = 0.0
  private var adrenalineGraceTicksState: Int = 0
  private var bloodOxygenState: Double = VitalsComponent.MaxBloodOxygen
  private var bloodVolumeState: Double = CasualtiesBelowConfig.MaxBloodVolume.get()
  private var hypoxiaExposureTicksState: Int = 0
  private var totemHemostasisTicksState: Int = 0
  private var sepsisState: Double = 0.0
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
    setImmuneHealth(source.immuneHealth)
    val normalizedAdrenaline =
      if (player.level().isClientSide()) {
        Adrenaline.normalizeSyncedState(source.adrenaline, source.adrenalineGraceTicks)
      } else {
        Adrenaline.normalizeStoredState(source.adrenaline, source.adrenalineGraceTicks)
      }
    applyAdrenalineState(normalizedAdrenaline.amount, normalizedAdrenaline.graceTicks)
    val (normalizedShockLoad, normalizedShockStage) =
      if (player.level().isClientSide()) {
        PainShock.normalizeSyncedState(other.painShockLoad, other.painShockStage)
      } else {
        PainShock.normalizeStoredState(
          other.painShockLoad,
          other.painShockStage,
          Some(source.unconscious),
          normalizedAdrenaline.amount
        )
      }
    applyPainShockState(normalizedShockLoad, normalizedShockStage)
    val (normalizedConsciousness, normalizedUnconscious) =
      ConsciousnessProgression.normalizeStoredState(
        source.consciousness,
        Some(source.unconscious),
        if (player.level().isClientSide()) 0.0
        else CasualtiesBelowConfig.ConsciousnessFloor.get(),
        normalizedShockStage
      )
    applyConsciousnessState(normalizedConsciousness, normalizedUnconscious)
    setBloodOxygen(source.bloodOxygen)
    setBloodVolume(source.bloodVolume)
    applyHypoxiaExposureTicks(
      HypoxiaProgression.normalizeExposureTicks(source.hypoxiaExposureTicks)
    )
    applyTotemHemostasisTicks(
      TotemHemostasis.normalizeRemainingTicks(source.totemHemostasisTicks)
    )
    setSepsis(source.sepsis)
    setDiscomfort(source.discomfort)
  }

  override def immuneHealth: Double = immuneHealthState

  private[casualtiesbelow] def setImmuneHealth(value: Double): Unit = {
    immuneHealthState = bounded(value, CasualtiesBelowConfig.MaxImmuneHealth.get())
  }

  override def consciousness: Double = consciousnessState

  override def unconscious: Boolean = unconsciousState

  private[casualtiesbelow] def applyConsciousnessState(
      consciousness: Double,
      unconscious: Boolean
  ): Unit = {
    consciousnessState = consciousness
    unconsciousState = unconscious
  }

  override def painShockLoad: Double = painShockLoadState

  override def painShockStage: PainShockStage = painShockStageState

  private[casualtiesbelow] def applyPainShockState(
      load: Double,
      stage: PainShockStage
  ): Unit = {
    painShockLoadState = load
    painShockStageState = stage
  }

  override def adrenaline: Double = adrenalineState

  private[casualtiesbelow] def adrenalineGraceTicks: Int =
    adrenalineGraceTicksState

  private[casualtiesbelow] def applyAdrenalineState(
      amount: Double,
      graceTicks: Int
  ): Unit = {
    adrenalineState = amount
    adrenalineGraceTicksState = graceTicks
  }

  private[casualtiesbelow] def hypoxiaExposureTicks: Int = hypoxiaExposureTicksState

  private[casualtiesbelow] def applyHypoxiaExposureTicks(ticks: Int): Unit = {
    hypoxiaExposureTicksState = ticks
  }

  private[casualtiesbelow] def totemHemostasisTicks: Int = totemHemostasisTicksState

  private[casualtiesbelow] def applyTotemHemostasisTicks(ticks: Int): Unit = {
    totemHemostasisTicksState = ticks
  }

  override def bloodOxygen: Double = bloodOxygenState

  private[casualtiesbelow] def setBloodOxygen(value: Double): Unit = {
    bloodOxygenState = bounded(value, VitalsComponent.MaxBloodOxygen)
  }

  override def bloodVolume: Double = bloodVolumeState

  private[casualtiesbelow] def setBloodVolume(value: Double): Unit = {
    bloodVolumeState = bounded(value, CasualtiesBelowConfig.MaxBloodVolume.get())
  }

  override def sepsis: Double = sepsisState

  private[casualtiesbelow] def setSepsis(value: Double): Unit = {
    sepsisState = bounded(value, CasualtiesBelowConfig.MaxSepsis.get())
  }

  override def discomfort: Double = discomfortState

  private[casualtiesbelow] def setDiscomfort(value: Double): Unit = {
    discomfortState = bounded(value, CasualtiesBelowConfig.MaxDiscomfort.get())
  }

  override def shouldSyncWith(recipient: ServerPlayer): Boolean = recipient eq player

  override def writeData(out: ValueOutput): Unit = {
    out.putDouble(VitalsComponentImpl.ImmuneHealthKey, immuneHealth)
    out.putDouble(VitalsComponentImpl.ConsciousnessKey, consciousness)
    out.putBoolean(VitalsComponentImpl.UnconsciousKey, unconscious)
    out.putDouble(VitalsComponentImpl.AdrenalineKey, adrenaline)
    out.putInt(VitalsComponentImpl.AdrenalineGraceTicksKey, adrenalineGraceTicks)
    out.putDouble(VitalsComponentImpl.PainShockLoadKey, painShockLoad)
    out.putString(VitalsComponentImpl.PainShockStageKey, painShockStage.id)
    out.putDouble(VitalsComponentImpl.BloodOxygenKey, bloodOxygen)
    out.putDouble(VitalsComponentImpl.BloodVolumeKey, bloodVolume)
    out.putInt(VitalsComponentImpl.HypoxiaExposureTicksKey, hypoxiaExposureTicks)
    out.putInt(VitalsComponentImpl.TotemHemostasisTicksKey, totemHemostasisTicks)
    out.putDouble(VitalsComponentImpl.SepsisKey, sepsis)
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
    applyAdrenalineState(normalizedAdrenaline.amount, normalizedAdrenaline.graceTicks)
    val savedShockStage = PainShockStage
      .fromId(in.getStringOr(VitalsComponentImpl.PainShockStageKey, PainShockStage.Stable.id))
      .toScala
      .getOrElse(PainShockStage.Stable)
    val (normalizedShockLoad, normalizedShockStage) =
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
    applyPainShockState(normalizedShockLoad, normalizedShockStage)
    val (normalizedConsciousness, normalizedUnconscious) =
      ConsciousnessProgression.normalizeStoredState(
        in.getDoubleOr(VitalsComponentImpl.ConsciousnessKey, VitalsComponent.MaxValue),
        savedUnconscious,
        if (player.level().isClientSide()) 0.0
        else CasualtiesBelowConfig.ConsciousnessFloor.get(),
        normalizedShockStage
      )
    applyConsciousnessState(normalizedConsciousness, normalizedUnconscious)
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
