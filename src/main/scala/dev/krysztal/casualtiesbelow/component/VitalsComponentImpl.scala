package dev.krysztal.casualtiesbelow.component

import scala.jdk.OptionConverters.*

import com.mojang.serialization.Codec
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.body.vitals.CirculationSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.ConsciousnessSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.InfectionSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage
import dev.krysztal.casualtiesbelow.api.body.vitals.ShockSnapshot
import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.physiology.adrenaline.AdrenalineState
import dev.krysztal.casualtiesbelow.physiology.circulation.CirculationState
import dev.krysztal.casualtiesbelow.physiology.circulation.HypoxiaProgression
import dev.krysztal.casualtiesbelow.physiology.circulation.TotemHemostasis
import dev.krysztal.casualtiesbelow.physiology.consciousness.Consciousness
import dev.krysztal.casualtiesbelow.physiology.opioid.OpioidState
import dev.krysztal.casualtiesbelow.physiology.pain.PainShock

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

final class VitalsComponentImpl(val player: Player)
    extends VitalsComponent
    with CopyableComponent[VitalsComponent]
    with AutoSyncedComponent {
  private var infectionState: InfectionSnapshot =
    InfectionSnapshot(Consts.Immune.DefaultImmuneHealth, 0.0)
  private var consciousnessState: ConsciousnessSnapshot =
    ConsciousnessSnapshot(VitalsComponent.MaxValue, unconscious = false)
  private var shockState: ShockSnapshot = ShockSnapshot(0.0, PainShockStage.Stable)
  private var adrenalineState: AdrenalineState = AdrenalineState.Empty
  private var circulationState: CirculationState = CirculationState(
    VitalsComponent.MaxBloodOxygen,
    Consts.Vitals.MaxBloodVolume,
    hypoxiaExposureTicks = 0,
    totemHemostasisTicks = 0
  )
  private var discomfortState: Double = 0.0
  private var dirtinessState: Double = 0.0
  private var opioidState: OpioidState = OpioidState(0.0, 0.0)
  private var bodyTemperatureState: Double = VitalsComponent.NormalBodyTemperature
  private var wetnessState: Double = 0.0

  /** Single commit point for client-visible vitals writes: assigns only on an actual change and
    * queues the owner for the tick-end sync. The component is the sync authority — producers never
    * do sync bookkeeping of their own.
    */
  private def commit[A](current: A, next: A)(assign: A => Unit): Unit = {
    if (next != current) {
      assign(next)
      VitalsMutations.markDirty(player)
    }
  }

  /** Commit without a sync request, for values with no client consumer (adrenaline reserve, the
    * totem hemostasis timer, both opioid axes): they ride along whenever some visible field syncs,
    * and persistence always writes the live value regardless.
    */
  private def commitSilent[A](current: A, next: A)(assign: A => Unit): Unit = {
    if (next != current) assign(next)
  }

  /** The one authority-dependent normalize decision, shared by `copyFrom` (death clone) and
    * `readData` (disk load / sync receipt): data authored by the authoritative server is trusted
    * as-is (synced path), data from disk is re-derived against the local config (stored path).
    */
  private def clientSide: Boolean = player.level().isClientSide()

  private def normalizeAdrenaline(amount: Double, graceTicks: Int): AdrenalineState = {
    if (clientSide) Adrenaline.normalizeSyncedState(amount, graceTicks)
    else Adrenaline.normalizeStoredState(amount, graceTicks)
  }

  private def normalizeShock(
      load: Double,
      stage: PainShockStage,
      unconscious: Option[Boolean],
      adrenaline: Double
  ): ShockSnapshot = {
    if (clientSide) PainShock.normalizeSyncedState(load, stage)
    else PainShock.normalizeStoredState(load, stage, unconscious, adrenaline)
  }

  private def normalizeConsciousness(
      level: Double,
      unconscious: Option[Boolean],
      stage: PainShockStage
  ): ConsciousnessSnapshot = {
    if (clientSide) Consciousness.normalizeSyncedState(level, unconscious, stage)
    else {
      Consciousness.normalizeStoredState(
        level,
        unconscious,
        Consts.Vitals.ConsciousnessFloor,
        Consts.Vitals.ConsciousnessKnockoutThreshold,
        stage
      )
    }
  }

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
      normalizeAdrenaline(source.adrenaline, source.adrenalineGraceTicks)
    applyAdrenalineState(normalizedAdrenaline)
    val normalizedShock = normalizeShock(
      other.shock.load,
      other.shock.stage,
      Some(source.consciousness.unconscious),
      normalizedAdrenaline.amount
    )
    applyShockState(normalizedShock)
    applyConsciousnessState(
      normalizeConsciousness(
        source.consciousness.level,
        Some(source.consciousness.unconscious),
        normalizedShock.stage
      )
    )
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
    setDirtiness(source.dirtiness)
    setOpioidLevel(source.opioidLevel)
    setOpioidDependence(source.opioidDependence)
    setBodyTemperature(source.bodyTemperature)
    setWetness(source.wetness)
  }

  override def infection: InfectionSnapshot = infectionState

  private[casualtiesbelow] def setImmuneHealth(value: Double): Unit = {
    commit(
      infectionState,
      infectionState.copy(immuneHealth = value.bounded(Consts.Vitals.MaxImmuneHealth))
    ) { infectionState = _ }
  }

  override def consciousness: ConsciousnessSnapshot = consciousnessState

  private[casualtiesbelow] def applyConsciousnessState(state: ConsciousnessSnapshot): Unit = {
    commit(consciousnessState, state) { consciousnessState = _ }
  }

  override def shock: ShockSnapshot = shockState

  private[casualtiesbelow] def applyShockState(state: ShockSnapshot): Unit = {
    commit(shockState, state) { shockState = _ }
  }

  override def adrenaline: Double = adrenalineState.amount

  private[casualtiesbelow] def adrenalineGraceTicks: Int = adrenalineState.graceTicks

  private[casualtiesbelow] def adrenalineReserve: AdrenalineState = adrenalineState

  private[casualtiesbelow] def applyAdrenalineState(state: AdrenalineState): Unit = {
    commitSilent(adrenalineState, state) { adrenalineState = _ }
  }

  private[casualtiesbelow] def hypoxiaExposureTicks: Int = circulationState.hypoxiaExposureTicks

  private[casualtiesbelow] def applyHypoxiaExposureTicks(ticks: Int): Unit = {
    commit(circulationState, circulationState.copy(hypoxiaExposureTicks = ticks)) {
      circulationState = _
    }
  }

  private[casualtiesbelow] def totemHemostasisTicks: Int = circulationState.totemHemostasisTicks

  private[casualtiesbelow] def applyTotemHemostasisTicks(ticks: Int): Unit = {
    commitSilent(circulationState, circulationState.copy(totemHemostasisTicks = ticks)) {
      circulationState = _
    }
  }

  override def circulation: CirculationSnapshot = circulationState.snapshot

  private[casualtiesbelow] def setBloodOxygen(value: Double): Unit = {
    commit(
      circulationState,
      circulationState.copy(bloodOxygen = value.bounded(VitalsComponent.MaxBloodOxygen))
    ) { circulationState = _ }
  }

  private[casualtiesbelow] def setBloodVolume(value: Double): Unit = {
    commit(
      circulationState,
      circulationState.copy(bloodVolume = value.bounded(Consts.Vitals.MaxBloodVolume))
    ) { circulationState = _ }
  }

  private[casualtiesbelow] def setSepsis(value: Double): Unit = {
    commit(infectionState, infectionState.copy(sepsis = value.bounded(Consts.Sepsis.MaxSepsis))) {
      infectionState = _
    }
  }

  override def discomfort: Double = discomfortState

  private[casualtiesbelow] def setDiscomfort(value: Double): Unit = {
    commit(discomfortState, value.bounded(Consts.Discomfort.MaxValue)) { discomfortState = _ }
  }

  override def dirtiness: Double = dirtinessState

  private[casualtiesbelow] def setDirtiness(value: Double): Unit = {
    commit(dirtinessState, value.bounded(Consts.Dirtiness.MaxValue)) { dirtinessState = _ }
  }

  override def opioidLevel: Double = opioidState.level

  private[casualtiesbelow] def setOpioidLevel(value: Double): Unit = {
    commitSilent(
      opioidState,
      opioidState.copy(level = value.bounded(VitalsComponent.MaxOpioidLevel))
    ) { opioidState = _ }
  }

  override def opioidDependence: Double = opioidState.dependence

  private[casualtiesbelow] def setOpioidDependence(value: Double): Unit = {
    commitSilent(
      opioidState,
      opioidState.copy(dependence = value.bounded(VitalsComponent.MaxOpioidDependence))
    ) { opioidState = _ }
  }

  private[casualtiesbelow] def applyOpioidState(state: OpioidState): Unit = {
    setOpioidLevel(state.level)
    setOpioidDependence(state.dependence)
  }

  override def bodyTemperature: Double = bodyTemperatureState

  private[casualtiesbelow] def setBodyTemperature(value: Double): Unit = {
    commit(bodyTemperatureState, value.bounded(VitalsComponent.MaxBodyTemperature)) {
      bodyTemperatureState = _
    }
  }

  override def wetness: Double = wetnessState

  private[casualtiesbelow] def setWetness(value: Double): Unit = {
    commit(wetnessState, value.bounded(VitalsComponent.MaxWetness)) { wetnessState = _ }
  }

  override def shouldSyncWith(recipient: ServerPlayer): Boolean = recipient eq player

  override def writeSyncPacket(buf: RegistryFriendlyByteBuf, recipient: ServerPlayer): Unit = {
    val reporter = new ProblemReporter.ScopedCollector(CasualtiesBelow.Logger)
    try {
      val out = TagValueOutput.createWithContext(reporter, buf.registryAccess())
      writeData(out)
      buf.writeNbt(out.buildResult())
    } finally {
      reporter.close()
    }
  }

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
    out.putDouble(VitalsComponentImpl.DirtinessKey, dirtiness)
    out.putDouble(VitalsComponentImpl.OpioidLevelKey, opioidLevel)
    out.putDouble(VitalsComponentImpl.OpioidDependenceKey, opioidDependence)
    out.putDouble(VitalsComponentImpl.BodyTemperatureKey, bodyTemperature)
    out.putDouble(VitalsComponentImpl.WetnessKey, wetness)
  }

  override def readData(in: ValueInput): Unit = {
    setImmuneHealth(
      in.getDoubleOr(
        VitalsComponentImpl.ImmuneHealthKey,
        Consts.Immune.DefaultImmuneHealth
      )
    )
    val savedUnconscious =
      in.read(VitalsComponentImpl.UnconsciousKey, Codec.BOOL).toScala.map(_.booleanValue)
    val normalizedAdrenaline = normalizeAdrenaline(
      in.getDoubleOr(VitalsComponentImpl.AdrenalineKey, 0.0),
      in.getIntOr(VitalsComponentImpl.AdrenalineGraceTicksKey, 0)
    )
    applyAdrenalineState(normalizedAdrenaline)
    val savedShockStage = PainShockStage
      .fromId(in.getStringOr(VitalsComponentImpl.PainShockStageKey, PainShockStage.Stable.id))
      .toScala
      .getOrElse(PainShockStage.Stable)
    val normalizedShock = normalizeShock(
      in.getDoubleOr(VitalsComponentImpl.PainShockLoadKey, 0.0),
      savedShockStage,
      savedUnconscious,
      normalizedAdrenaline.amount
    )
    applyShockState(normalizedShock)
    applyConsciousnessState(
      normalizeConsciousness(
        in.getDoubleOr(VitalsComponentImpl.ConsciousnessKey, VitalsComponent.MaxValue),
        savedUnconscious,
        normalizedShock.stage
      )
    )
    setBloodOxygen(
      in.getDoubleOr(
        VitalsComponentImpl.BloodOxygenKey,
        VitalsComponent.MaxBloodOxygen
      )
    )
    setBloodVolume(
      in.getDoubleOr(
        VitalsComponentImpl.BloodVolumeKey,
        Consts.Vitals.MaxBloodVolume
      )
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
    setDirtiness(in.getDoubleOr(VitalsComponentImpl.DirtinessKey, 0.0))
    setOpioidLevel(in.getDoubleOr(VitalsComponentImpl.OpioidLevelKey, 0.0))
    setOpioidDependence(in.getDoubleOr(VitalsComponentImpl.OpioidDependenceKey, 0.0))
    setBodyTemperature(
      in.getDoubleOr(
        VitalsComponentImpl.BodyTemperatureKey,
        VitalsComponent.NormalBodyTemperature
      )
    )
    setWetness(in.getDoubleOr(VitalsComponentImpl.WetnessKey, 0.0))
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
  private val DirtinessKey = "dirtiness"
  private val OpioidLevelKey = "opioid_level"
  private val OpioidDependenceKey = "opioid_dependence"
  private val BodyTemperatureKey = "body_temperature"
  private val WetnessKey = "wetness"
}
