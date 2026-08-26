package dev.krysztal.casualtiesbelow.component

import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import net.minecraft.core.HolderLookup
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

import dev.krysztal.casualtiesbelow.api.body.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.pain.PainShock
import dev.krysztal.casualtiesbelow.progression.ConsciousnessProgression

final class VitalsComponentImpl(val player: Player) extends VitalsComponent {
  var immuneHealth: Double = CasualtiesBelowConfig.MaxImmuneHealth.get()
  private var consciousnessState: Double = VitalsComponent.MaxValue
  private var unconsciousState: Boolean = false
  private var painShockLoadState: Double = 0.0
  private var painShockStageState: PainShockStage = PainShockStage.Stable
  var bloodOxygen: Double = VitalsComponent.MaxBloodOxygen
  var bloodVolume: Double = CasualtiesBelowConfig.MaxBloodVolume.get()
  var sepsis: Double = 0.0
  var discomfort: Double = 0.0

  override def copyFrom(
      other: VitalsComponent,
      registryLookup: HolderLookup.Provider
  ): Unit = {
    immuneHealth = other.immuneHealth
    val (normalizedShockLoad, normalizedShockStage) =
      PainShock.normalizeStoredState(
        other.painShockLoad,
        other.painShockStage,
        Some(other.unconscious)
      )
    applyPainShockState(normalizedShockLoad, normalizedShockStage)
    val (normalizedConsciousness, normalizedUnconscious) =
      ConsciousnessProgression.normalizeStoredState(
        other.consciousness,
        Some(other.unconscious),
        CasualtiesBelowConfig.ConsciousnessFloor.get(),
        normalizedShockStage
      )
    applyConsciousnessState(normalizedConsciousness, normalizedUnconscious)
    bloodOxygen = other.bloodOxygen
    bloodVolume = other.bloodVolume
    sepsis = other.sepsis
    discomfort = other.discomfort
  }

  override def consciousness: Double = consciousnessState

  override def unconscious: Boolean = unconsciousState

  override private[casualtiesbelow] def applyConsciousnessState(
      consciousness: Double,
      unconscious: Boolean
  ): Unit = {
    consciousnessState = consciousness
    unconsciousState = unconscious
  }

  override def painShockLoad: Double = painShockLoadState

  override def painShockStage: PainShockStage = painShockStageState

  override private[casualtiesbelow] def applyPainShockState(
      load: Double,
      stage: PainShockStage
  ): Unit = {
    painShockLoadState = load
    painShockStageState = stage
  }

  override def shouldSyncWith(recipient: ServerPlayer): Boolean = recipient eq player

  override def writeData(out: ValueOutput): Unit = {
    out.putDouble(VitalsComponentImpl.ImmuneHealthKey, immuneHealth)
    out.putDouble(VitalsComponentImpl.ConsciousnessKey, consciousness)
    out.putBoolean(VitalsComponentImpl.UnconsciousKey, unconscious)
    out.putDouble(VitalsComponentImpl.PainShockLoadKey, painShockLoad)
    out.putString(VitalsComponentImpl.PainShockStageKey, painShockStage.id)
    out.putDouble(VitalsComponentImpl.BloodOxygenKey, bloodOxygen)
    out.putDouble(VitalsComponentImpl.BloodVolumeKey, bloodVolume)
    out.putDouble(VitalsComponentImpl.SepsisKey, sepsis)
    out.putDouble(VitalsComponentImpl.DiscomfortKey, discomfort)
  }

  override def readData(in: ValueInput): Unit = {
    immuneHealth = in.getDoubleOr(
      VitalsComponentImpl.ImmuneHealthKey,
      CasualtiesBelowConfig.MaxImmuneHealth.get()
    )
    val savedUnconscious =
      in.read(VitalsComponentImpl.UnconsciousKey, Codec.BOOL).toScala.map(_.booleanValue)
    val savedShockStage = PainShockStage
      .byId(in.getStringOr(VitalsComponentImpl.PainShockStageKey, PainShockStage.Stable.id))
      .getOrElse(PainShockStage.Stable)
    val (normalizedShockLoad, normalizedShockStage) =
      PainShock.normalizeStoredState(
        in.getDoubleOr(VitalsComponentImpl.PainShockLoadKey, 0.0),
        savedShockStage,
        savedUnconscious
      )
    applyPainShockState(normalizedShockLoad, normalizedShockStage)
    val (normalizedConsciousness, normalizedUnconscious) =
      ConsciousnessProgression.normalizeStoredState(
        in.getDoubleOr(VitalsComponentImpl.ConsciousnessKey, VitalsComponent.MaxValue),
        savedUnconscious,
        CasualtiesBelowConfig.ConsciousnessFloor.get(),
        normalizedShockStage
      )
    applyConsciousnessState(normalizedConsciousness, normalizedUnconscious)
    bloodOxygen = in.getDoubleOr(
      VitalsComponentImpl.BloodOxygenKey,
      VitalsComponent.MaxBloodOxygen
    )
    bloodVolume =
      in.getDoubleOr(VitalsComponentImpl.BloodVolumeKey, CasualtiesBelowConfig.MaxBloodVolume.get())
    sepsis = in.getDoubleOr(VitalsComponentImpl.SepsisKey, 0.0)
    discomfort = in.getDoubleOr(VitalsComponentImpl.DiscomfortKey, 0.0)
  }
}

object VitalsComponentImpl {

  // NBT keys (serialization implementation detail; not part of the public API)
  private val ImmuneHealthKey = "immune_health"
  private val ConsciousnessKey = "consciousness"
  private val UnconsciousKey = "unconscious"
  private val PainShockLoadKey = "pain_shock_load"
  private val PainShockStageKey = "pain_shock_stage"
  private val BloodOxygenKey = "blood_oxygen"
  private val BloodVolumeKey = "blood_volume"
  private val SepsisKey = "sepsis"
  private val DiscomfortKey = "discomfort"
}
