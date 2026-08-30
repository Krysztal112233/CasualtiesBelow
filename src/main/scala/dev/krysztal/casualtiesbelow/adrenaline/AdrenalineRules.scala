package dev.krysztal.casualtiesbelow.adrenaline

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.damage.DamageMatcher
import dev.krysztal.casualtiesbelow.data.schema.AdrenalineRuleData
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLookup
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStore
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataStores

final case class ClassifiedAdrenalineRule(ruleId: Identifier, amount: Double)

/** Selects exactly one event-level stimulus by descending priority and ascending datapack id. Rules
  * are read from the current immutable gameplay-data store on every event, so `/reload` takes
  * effect without a second compiled cache.
  */
object AdrenalineRules {

  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Option[ClassifiedAdrenalineRule] = {
    classify(level, player, source, GameplayDataStores.server(level.getServer))
  }

  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource,
      store: GameplayDataStore
  ): Option[ClassifiedAdrenalineRule] = {
    select(store.adrenalineRules)(rule =>
      DamageMatcher.matches(rule.damageMatch, level, player, source)
    )
  }

  /** Grants at most once for one AFTER_DAMAGE event. Existing unconsciousness is never reversed or
    * banked into a later wake-up.
    */
  def grantFor(player: ServerPlayer, source: DamageSource): Boolean = {
    grantFor(player, source, GameplayDataStores.server(player.level().getServer))
  }

  private[casualtiesbelow] def grantFor(
      player: ServerPlayer,
      source: DamageSource,
      store: GameplayDataStore
  ): Boolean = {
    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    if (vitals.unconscious) return false

    classify(player.level(), player, source, store).exists(rule =>
      Adrenaline.grant(player, rule.amount, rule.ruleId)
    )
  }

  private[casualtiesbelow] def select(
      entries: Map[Identifier, AdrenalineRuleData]
  )(matches: AdrenalineRuleData => Boolean): Option[ClassifiedAdrenalineRule] = {
    GameplayDataLookup
      .orderedEntries(entries)(_.priority.intValue())
      .collectFirst {
        case (id, rule) if matches(rule) => ClassifiedAdrenalineRule(id, rule.amount)
      }
  }
}
