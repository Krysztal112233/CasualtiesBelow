package dev.krysztal.casualtiesbelow

import net.minecraft.resources.Identifier

import net.fabricmc.api.ModInitializer

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.block.CasualtiesBelowBlocks
import dev.krysztal.casualtiesbelow.block.entity.CasualtiesBelowBlockEntities
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.damage.LimbDamage
import dev.krysztal.casualtiesbelow.fluid.PoppyFluids
import dev.krysztal.casualtiesbelow.internal.data.GameplayDataLoaders
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSync
import dev.krysztal.casualtiesbelow.internal.sync.InjectionSync
import dev.krysztal.casualtiesbelow.item.AmpouleFilling
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.item.PoppyFluidItemStorages
import dev.krysztal.casualtiesbelow.item.PoppyPotions
import dev.krysztal.casualtiesbelow.item.PoppyProcessing
import dev.krysztal.casualtiesbelow.item.PoppyRefining
import dev.krysztal.casualtiesbelow.physiology.consciousness.Unconsciousness
import dev.krysztal.casualtiesbelow.physiology.discomfort.Discomfort
import dev.krysztal.casualtiesbelow.physiology.hygiene.Dirtiness
import dev.krysztal.casualtiesbelow.physiology.hygiene.DirtinessSources
import dev.krysztal.casualtiesbelow.physiology.immune.ZombieAttackImmuneDrain
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CasualtiesBelow extends ModInitializer {
  val ModId: String = CasualtiesBelowApi.ModId
  val Logger: Logger = LoggerFactory.getLogger(ModId)

  override def onInitialize(): Unit = {
    // Fluids must exist before the fluid blocks (constructor argument) and the buckets.
    PoppyFluids.register()
    CasualtiesBelowDataComponents.register()
    CasualtiesBelowBlocks.register()
    CasualtiesBelowBlockEntities.register()
    // The brewing carrier potion must be registered before the liquid items bind its holder.
    PoppyPotions.register()
    CasualtiesBelowItems.register()
    PoppyRefining.register()
    GameplayDataLoaders.registerAll()
    CasualtiesBelowConfig.register()
    CasualtiesBelowCommands.register()
    LimbDamage.register()
    // InjuryProgression must run before the body's end-of-tick flush (registration order =
    // event order) so its dirty marks ship in the same tick.
    InjuryProgression.register()
    Unconsciousness.register()
    // The unconsciousness interaction gate must run before these use handlers.
    PoppyProcessing.register()
    AmpouleFilling.register()
    PoppyFluidItemStorages.register()
    // Same tick-ordering constraint as InjuryProgression: before the body flush.
    Discomfort.register()
    // Hygiene progression shares discomfort's owner-sync cadence; vitals-only, no body flush.
    Dirtiness.register()
    BodyMutations.register()
    ZombieAttackImmuneDrain.register()
    DirtinessSources.register()
    GameplayDataSync.register()
    InjectionSync.register()
    Logger.info("Casualties: Below initialized")
  }

  def ofIdentifier(path: String): Identifier =
    CasualtiesBelowApi.id(path)

}
