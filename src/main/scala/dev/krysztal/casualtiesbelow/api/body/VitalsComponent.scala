package dev.krysztal.casualtiesbelow.api.body

import org.ladysnake.cca.api.v3.component.CopyableComponent
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent

/** Whole-player vitals: immune health, consciousness, hidden pain-shock load and phase, temporary
  * adrenaline, blood oxygen, blood volume, hidden terminal-hypoxia exposure and totem hemostasis,
  * sepsis, and discomfort. Immune health ranges from 0 to the configured maximum
  * (`maxImmuneHealth`, default 200); consciousness, pain-shock load, and blood oxygen from 0 to
  * their constants below; adrenaline from 0 to its configured maximum; blood volume is in mL, up to
  * the configured maximum; discomfort runs from 0 to the configured maximum (`[discomfort]
  * maxValue`, default 100). Blood oxygen is normalized oxygen availability relative to a healthy,
  * fully oxygenated player rather than a clinical saturation percentage: less blood lowers how much
  * oxygen the body can carry.
  *
  * `Double` rather than `Float` for the same reason as [[LimbStats]]: per-tick accumulation
  * precision.
  */
trait VitalsComponent extends CopyableComponent[VitalsComponent] with AutoSyncedComponent {
  var immuneHealth: Double

  /** Read-only outside the centralized consciousness authority; the scalar and latch always mutate
    * as one reconciled state.
    */
  def consciousness: Double
  def unconscious: Boolean

  /** Package-internal mutation hook used only by the centralized consciousness authority. The
    * scalar and latch always mutate as one reconciled state.
    */
  private[casualtiesbelow] def applyConsciousnessState(
      consciousness: Double,
      unconscious: Boolean
  ): Unit

  /** Hidden whole-body load and discrete phase owned by the pain-shock progression authority. */
  def painShockLoad: Double
  def painShockStage: PainShockStage

  private[casualtiesbelow] def applyPainShockState(
      load: Double,
      stage: PainShockStage
  ): Unit

  /** Temporary reserve used only to raise the pain-shock collapse threshold. The hidden grace timer
    * is kept package-internal so integrations cannot treat it as a second public vital.
    */
  def adrenaline: Double
  private[casualtiesbelow] def adrenalineGraceTicks: Int

  private[casualtiesbelow] def applyAdrenalineState(
      amount: Double,
      graceTicks: Int
  ): Unit

  var bloodOxygen: Double
  var bloodVolume: Double

  /** Consecutive terminal-exposure ticks while oxygen is zero and breathing remains blocked. */
  def hypoxiaExposureTicks: Int

  private[casualtiesbelow] def applyHypoxiaExposureTicks(ticks: Int): Unit

  /** Remaining ticks of the hidden, server-authoritative post-totem hemostasis window. */
  def totemHemostasisTicks: Int

  private[casualtiesbelow] def applyTotemHemostasisTicks(ticks: Int): Unit

  var sepsis: Double
  var discomfort: Double
}

object VitalsComponent {
  val MaxValue: Double = 100.0
  val MaxBloodOxygen: Double = 100.0
  val MinimumWakeThreshold: Double = 1.0e-6
}
