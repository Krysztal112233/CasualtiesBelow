package dev.krysztal.casualtiesbelow.physiology.adrenaline

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

import dev.krysztal.casualtiesbelow.api.body.vitals.VitalsComponent
import dev.krysztal.casualtiesbelow.api.event.AdrenalineChangedCallback
import dev.krysztal.casualtiesbelow.api.event.AdrenalineChangedContext
import dev.krysztal.casualtiesbelow.api.event.PhysiologyChangeCause
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.Prelude.*

/** Server authority for the temporary adrenaline reserve and its post-stimulus grace window.
  *
  * A fresh stimulus stores one extra grace tick. The same server-tick progression pass consumes
  * that sentinel without reducing the reserve, so even a zero-tick grace would let a hit protect
  * against pain shock and reduce acute injury pain in the tick in which it landed. Later ticks
  * consume the fixed grace and then decay the reserve linearly.
  */
object Adrenaline {

  /** Adds one configured rule amount and refreshes grace. Returns whether either server state value
    * changed. The reserve is hidden server-side state: it has no client consumer, so it never
    * requests a sync of its own.
    */
  def grant(player: ServerPlayer, amount: Double, cause: Identifier): Boolean = {
    if (!CasualtiesBelowConfig.injurySurvival.adrenalineEnabled.get()) return false
    val vitals = player.vitals
    val previous = storedState(vitals)
    val next = grantState(
      previous,
      amount,
      Consts.Adrenaline.MaxValue,
      Consts.Adrenaline.CombatGraceTicks
    )
    applyState(vitals, next)
    if (next.amount != previous.amount) {
      emitAmountChange(player, previous.amount, next.amount, cause)
    }
    next != previous
  }

  /** Ages grace/reserve before pain shock evaluates this tick. Fresh grants are protected by the
    * sentinel described in the class documentation.
    */
  def tick(player: ServerPlayer, vitals: VitalsComponentImpl): Unit = {
    val previous = storedState(vitals)
    val next = advanceState(
      previous,
      Consts.Adrenaline.MaxValue,
      Consts.Adrenaline.DecayPerTick
    )
    applyState(vitals, next)
    if (next.amount != previous.amount) {
      emitAmountChange(player, previous.amount, next.amount, PhysiologyChangeCause.AdrenalineDecay)
    }
  }

  /** Debug/admin edit. A positive value represents a fresh stimulus and receives a full grace
    * window; zero clears both fields. The caller owns immediate shock reconciliation.
    */
  def applyAuthoritativeEdit(
      player: ServerPlayer,
      vitals: VitalsComponentImpl,
      requestedAmount: Double
  ): Boolean = {
    val previous = storedState(vitals)
    val amount = requestedAmount.bounded(Consts.Adrenaline.MaxValue)
    val next =
      if (amount > 0.0) {
        AdrenalineState(
          amount,
          freshGraceTicks(Consts.Adrenaline.CombatGraceTicks)
        )
      } else AdrenalineState.Empty
    applyState(vitals, next)
    if (next.amount != previous.amount) {
      emitAmountChange(player, previous.amount, next.amount, PhysiologyChangeCause.AdminEdit)
    }
    next != previous
  }

  def reset(player: ServerPlayer, vitals: VitalsComponentImpl): Unit = {
    val previous = storedState(vitals)
    applyState(vitals, AdrenalineState.Empty)
    if (previous.amount != 0.0) {
      emitAmountChange(player, previous.amount, 0.0, PhysiologyChangeCause.Reset)
    }
  }

  /** Finite, fixed-balance bounded server value used by the pain-shock threshold calculation. */
  def currentAmount(vitals: VitalsComponent): Double =
    vitals.adrenaline.bounded(Consts.Adrenaline.MaxValue)

  /** Server-side save/copy normalization. */
  private[casualtiesbelow] def normalizeStoredState(
      amount: Double,
      graceTicks: Int,
      maximum: Double = Consts.Adrenaline.MaxValue,
      combatGraceTicks: Int = Consts.Adrenaline.CombatGraceTicks
  ): AdrenalineState = {
    val normalized = normalizeState(amount, graceTicks, maximum)
    normalized.copy(
      graceTicks = normalized.graceTicks.min(freshGraceTicks(combatGraceTicks))
    )
  }

  /** Client-side component transport normalization. The receiving client trusts the server's fixed
    * server bound and only rejects intrinsically invalid wire values.
    */
  private[casualtiesbelow] def normalizeSyncedState(
      amount: Double,
      graceTicks: Int
  ): AdrenalineState = {
    val normalizedAmount =
      if (amount.isFinite) amount.max(0.0)
      else 0.0
    AdrenalineState(normalizedAmount, normalizeGraceTicks(graceTicks))
  }

  private[casualtiesbelow] def normalizeState(
      amount: Double,
      graceTicks: Int,
      maximum: Double
  ): AdrenalineState = {
    val normalizedAmount = amount.bounded(maximum)
    if (normalizedAmount > 0.0) {
      AdrenalineState(normalizedAmount, normalizeGraceTicks(graceTicks))
    } else AdrenalineState.Empty
  }

  private[casualtiesbelow] def grantState(
      state: AdrenalineState,
      grant: Double,
      maximum: Double,
      combatGraceTicks: Int
  ): AdrenalineState = {
    val current = normalizeState(state.amount, state.graceTicks, maximum)
    val normalizedGrant =
      if (grant.isFinite) grant.max(0.0)
      else 0.0
    val limit = maximum.nonNegative
    if (normalizedGrant <= 0.0 || limit <= 0.0) return current

    val sum = current.amount + normalizedGrant
    val amount = if (sum.isFinite) sum.min(limit) else limit
    AdrenalineState(amount, math.max(current.graceTicks, freshGraceTicks(combatGraceTicks)))
  }

  private[casualtiesbelow] def advanceState(
      state: AdrenalineState,
      maximum: Double,
      decayPerTick: Double
  ): AdrenalineState = {
    val current = normalizeState(state.amount, state.graceTicks, maximum)
    if (current.amount <= 0.0) return AdrenalineState.Empty
    if (current.graceTicks > 0) {
      return current.copy(graceTicks = current.graceTicks - 1)
    }

    val decay =
      if (decayPerTick.isFinite) decayPerTick.max(0.0)
      else 0.0
    val amount = (current.amount - decay).max(0.0)
    if (amount > 0.0) AdrenalineState(amount, 0) else AdrenalineState.Empty
  }

  private def storedState(vitals: VitalsComponentImpl): AdrenalineState =
    vitals.adrenalineReserve

  private def applyState(vitals: VitalsComponentImpl, state: AdrenalineState): Unit = {
    if (vitals.adrenalineReserve != state) {
      vitals.applyAdrenalineState(state)
    }
  }

  private def emitAmountChange(
      player: ServerPlayer,
      previousAmount: Double,
      amount: Double,
      cause: Identifier
  ): Unit = {
    AdrenalineChangedCallback.EVENT
      .invoker()
      .onAdrenalineChanged(
        new AdrenalineChangedContext(player, previousAmount, amount, cause)
      )
  }

  private def normalizeGraceTicks(ticks: Int): Int = ticks.max(0)

  private def freshGraceTicks(configuredTicks: Int): Int = {
    val grace = normalizeGraceTicks(configuredTicks)
    if (grace == Int.MaxValue) grace else grace + 1
  }
}

private[casualtiesbelow] final case class AdrenalineState(amount: Double, graceTicks: Int)

private[casualtiesbelow] object AdrenalineState {
  val Empty: AdrenalineState = AdrenalineState(0.0, 0)
}
