package dev.krysztal.casualtiesbelow.internal.data

import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap

import net.fabricmc.fabric.api.resource.v1.DataResourceStore

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.data.schema.AdrenalineRuleData
import dev.krysztal.casualtiesbelow.data.schema.FixedTargetData
import dev.krysztal.casualtiesbelow.data.schema.LocalizedApplicationData
import dev.krysztal.casualtiesbelow.data.schema.WoundContributionData
import dev.krysztal.casualtiesbelow.data.schema.WoundMatchData
import dev.krysztal.casualtiesbelow.data.schema.WoundProfile
import dev.krysztal.casualtiesbelow.data.schema.WoundRuleData

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

final class GameplayDataStateTest {

  SharedConstants.tryDetectVersion()
  Bootstrap.bootStrap()

  @Test
  def candidatePublicationDoesNotReplaceInstalledGeneration(): Unit = {
    val installed = GameplayDataState.compile(gameplayData(10.0, includeWoundProfile = true))
    val candidate = GameplayDataState.compile(gameplayData(20.0, includeWoundProfile = false))
    val installedResources = new TestResourceStore
    val candidateResources = new TestResourceStore

    GameplayDataStores.publish(installedResources, installed)
    GameplayDataStores.publish(candidateResources, candidate)

    val stillInstalled = installedResources.getOrThrow(GameplayDataStores.StateKey)
    val preparedCandidate = candidateResources.getOrThrow(GameplayDataStores.StateKey)
    assertSame(installed, stillInstalled)
    assertSame(candidate, preparedCandidate)
    assertEquals(10.0, amount(stillInstalled), 1.0e-9)
    assertEquals(20.0, amount(preparedCandidate), 1.0e-9)
    assertEquals(1, stillInstalled.woundRules.size)
    assertEquals(0, preparedCandidate.woundRules.size)
  }

  private def gameplayData(amount: Double, includeWoundProfile: Boolean): GameplayDataStore = {
    val woundProfiles =
      if (includeWoundProfile) Map(ProfileId -> WoundProfile.linear(1.0, 2.0, 0.1, 3.0))
      else Map.empty
    GameplayDataStore.Empty.copy(
      woundProfiles = woundProfiles,
      woundRules = Map(
        RuleId -> WoundRuleData(
          WoundMatchData.Empty,
          List(
            LocalizedApplicationData(
              List(WoundContributionData(ProfileId, 1.0)),
              FixedTargetData(BodyPart.Torso)
            )
          ),
          0
        )
      ),
      adrenalineRules = Map(
        StimulusId -> AdrenalineRuleData(WoundMatchData.Empty, amount, 0)
      )
    )
  }

  private def amount(state: GameplayDataState): Double = {
    state.store.adrenalineRules.values.head.amount
  }

  private final class TestResourceStore extends DataResourceStore.Mutable {
    private var entry: Option[(DataResourceStore.Key[?], Any)] = None

    override def put[T](key: DataResourceStore.Key[T], data: T): Unit = {
      entry = Some((key, data))
    }

    override def getOrThrow[T](key: DataResourceStore.Key[T]): T = {
      entry
        .collect { case (storedKey, data) if storedKey eq key => data.asInstanceOf[T] }
        .getOrElse(throw IllegalStateException("missing test resource data"))
    }
  }

  private val ProfileId = Identifier.fromNamespaceAndPath("test", "profile")
  private val RuleId = Identifier.fromNamespaceAndPath("test", "rule")
  private val StimulusId = Identifier.fromNamespaceAndPath("test", "stimulus")
}
