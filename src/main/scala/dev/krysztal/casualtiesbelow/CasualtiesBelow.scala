package dev.krysztal.casualtiesbelow

import net.minecraft.resources.Identifier

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader

import dev.krysztal.casualtiesbelow.api.LimbInjuries
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.damage.ArmorProtectionOverrides
import dev.krysztal.casualtiesbelow.damage.LimbDamage
import dev.krysztal.casualtiesbelow.immune.ZombieAttackImmuneDrain
import dev.krysztal.casualtiesbelow.progression.InjuryProgression

import org.slf4j.LoggerFactory

object CasualtiesBelow extends ModInitializer {
  val ModId = "casualtiesbelow"
  val Logger = LoggerFactory.getLogger(ModId)

  override def onInitialize(): Unit = {
    CasualtiesBelowConfig.register()
    CasualtiesBelowCommands.register()
    LimbDamage.register()
    // InjuryProgression must run before LimbInjuries' end-of-tick flush (registration order =
    // event order) so its dirty marks ship in the same tick.
    InjuryProgression.register()
    LimbInjuries.register()
    ZombieAttackImmuneDrain.register()
    DataResourceLoader
      .get()
      .registerReloadListener(
        Identifier.fromNamespaceAndPath(ModId, "armor_protection"),
        ArmorProtectionOverrides
      )
    Logger.info("CasualtiesBelow initialized")
  }

  def ofIdentifier(path: String): Identifier =
    Identifier.fromNamespaceAndPath(ModId, path)

}
