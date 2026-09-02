package dev.krysztal.casualtiesbelow

import net.minecraft.resources.Identifier

import net.fabricmc.api.ModInitializer

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks
import dev.krysztal.casualtiesbelow.block.entity.CasualtiesBelowBlockEntities
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.damage.LimbDamage
import dev.krysztal.casualtiesbelow.discomfort.Discomfort
import dev.krysztal.casualtiesbelow.immune.ZombieAttackImmuneDrain
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLoaders
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSync
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.item.PoppyProcessing
import dev.krysztal.casualtiesbelow.progression.InjuryProgression

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CasualtiesBelow extends ModInitializer {
  val ModId: String = CasualtiesBelowApi.ModId
  val Logger: Logger = LoggerFactory.getLogger(ModId)

  override def onInitialize(): Unit = {
    CasualtiesBelowDataComponents.register()
    CasualtiesBelowBlocks.register()
    CasualtiesBelowBlockEntities.register()
    CasualtiesBelowItems.register()
    GameplayDataLoaders.registerAll()
    CasualtiesBelowConfig.register()
    CasualtiesBelowCommands.register()
    LimbDamage.register()
    // InjuryProgression must run before the body's end-of-tick flush (registration order =
    // event order) so its dirty marks ship in the same tick.
    InjuryProgression.register()
    Unconsciousness.register()
    // The unconsciousness interaction gate must run before this block-use handler.
    PoppyProcessing.register()
    // Same tick-ordering constraint as InjuryProgression: before the body flush.
    Discomfort.register()
    BodyMutations.register()
    ZombieAttackImmuneDrain.register()
    GameplayDataSync.register()
    Logger.info("Casualties: Below initialized")
  }

  def ofIdentifier(path: String): Identifier =
    CasualtiesBelowApi.id(path)

}
