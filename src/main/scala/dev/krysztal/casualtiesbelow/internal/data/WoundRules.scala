package dev.krysztal.casualtiesbelow.internal.data

import scala.jdk.OptionConverters.*

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.damage.DamageMatcher
import dev.krysztal.casualtiesbelow.data.schema.HemostasisData
import dev.krysztal.casualtiesbelow.data.schema.WoundApplicationData
import dev.krysztal.casualtiesbelow.data.schema.WoundProfile
import dev.krysztal.casualtiesbelow.data.schema.WoundRuleData

private[casualtiesbelow] final case class ResolvedWoundContribution(
    profileId: Identifier,
    profile: WoundProfile,
    severityMultiplier: Double,
    hemostasis: Option[HemostasisData]
)

private[casualtiesbelow] final case class ResolvedWoundApplication(
    data: WoundApplicationData,
    wounds: List[ResolvedWoundContribution]
)

private[casualtiesbelow] final case class ClassifiedWoundRule(
    ruleId: Identifier,
    applications: List[ResolvedWoundApplication]
)

/** Wound rules resolved against exactly one gameplay-data generation. */
private[casualtiesbelow] final class CompiledWoundRules private[internal] (
    private val rules: List[(Identifier, WoundRuleData, List[ResolvedWoundApplication])]
) {
  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Option[ClassifiedWoundRule] = {
    rules
      .find { case (_, rule, _) =>
        DamageMatcher.matches(rule.woundMatch, level, player, source)
      }
      .map { case (id, _, applications) => ClassifiedWoundRule(id, applications) }
  }

  private[internal] def size: Int = rules.size
}

/** Compiles and evaluates wound rules within one immutable gameplay-data generation. */
private[casualtiesbelow] object WoundProfiles {
  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource
  ): Option[ClassifiedWoundRule] = {
    classify(level, player, source, GameplayDataStores.state(level.getServer).woundRules)
  }

  def classify(
      level: ServerLevel,
      player: ServerPlayer,
      source: DamageSource,
      compiled: CompiledWoundRules
  ): Option[ClassifiedWoundRule] = compiled.classify(level, player, source)

  def compile(store: GameplayDataStore): CompiledWoundRules = {
    val rules = GameplayDataLookup
      .orderedEntries(store.woundRules)(_.priority.intValue())
      .flatMap { (ruleId, rule) => compileRule(ruleId, rule, store) }
    val compiled = new CompiledWoundRules(rules)
    CasualtiesBelow.Logger.info("Compiled {} wound rules", Int.box(compiled.size))
    compiled
  }

  private def compileRule(
      ruleId: Identifier,
      rule: WoundRuleData,
      store: GameplayDataStore
  ): Option[(Identifier, WoundRuleData, List[ResolvedWoundApplication])] = {
    val resolved =
      rule.applications.map(application => resolveApplication(ruleId, application, store))
    if (resolved.forall(_.isDefined)) Some((ruleId, rule, resolved.flatten))
    else None
  }

  private def resolveApplication(
      ruleId: Identifier,
      application: WoundApplicationData,
      store: GameplayDataStore
  ): Option[ResolvedWoundApplication] = {
    val resolved = application.wounds.map { wound =>
      resolveProfile(ruleId, wound.profile, store).map(profile =>
        ResolvedWoundContribution(
          wound.profile,
          profile,
          wound.severityMultiplier,
          wound.hemostasis.toScala
        )
      )
    }
    if (resolved.forall(_.isDefined)) Some(ResolvedWoundApplication(application, resolved.flatten))
    else None
  }

  private def resolveProfile(
      ruleId: Identifier,
      profileId: Identifier,
      store: GameplayDataStore
  ): Option[WoundProfile] = {
    GameplayDataLookup.entry(store.woundProfiles, profileId).orElse {
      CasualtiesBelow.Logger.warn(
        "Ignoring wound rule {} because profile {} is missing",
        ruleId,
        profileId
      )
      None
    }
  }
}
