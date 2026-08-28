package dev.krysztal.casualtiesbelow

import net.minecraft.resources.Identifier

import net.fabricmc.api.ModInitializer

import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.api.data.GameplayDataLoaders
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.damage.LimbDamage
import dev.krysztal.casualtiesbelow.discomfort.Discomfort
import dev.krysztal.casualtiesbelow.immune.ZombieAttackImmuneDrain
import dev.krysztal.casualtiesbelow.progression.InjuryProgression
import dev.krysztal.casualtiesbelow.sync.GameplayDataSync

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CasualtiesBelow extends ModInitializer {
  val ModId = "casualtiesbelow"
  val Logger: Logger = LoggerFactory.getLogger(ModId)

  override def onInitialize(): Unit = {
    GameplayDataLoaders.registerAll()
    CasualtiesBelowConfig.register()
    CasualtiesBelowCommands.register()
    LimbDamage.register()
    // InjuryProgression must run before LimbInjuries' end-of-tick flush (registration order =
    // event order) so its dirty marks ship in the same tick.
    InjuryProgression.register()
    Unconsciousness.register()
    // Same tick-ordering constraint as InjuryProgression: before LimbInjuries' flush.
    Discomfort.register()
    LimbInjuries.register()
    ZombieAttackImmuneDrain.register()
    GameplayDataSync.register()
    Logger.info("Casualties: Below initialized")
  }

  def ofIdentifier(path: String): Identifier =
    Identifier.fromNamespaceAndPath(ModId, path)

}
