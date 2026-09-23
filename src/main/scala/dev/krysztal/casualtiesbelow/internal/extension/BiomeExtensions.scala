package dev.krysztal.casualtiesbelow.internal.extension

import net.minecraft.core.BlockPos
import net.minecraft.world.level.biome.Biome

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.mixin.BiomeInvoker

/** Enrichments over [Biome] for this mod's climate reads. */
private[casualtiesbelow] object BiomeExtensions {

  extension (biome: Biome) {

    /** The biome's vanilla temperature at `pos` (coordinate-adjusted, sea-level compensated, as
      * vanilla precipitation uses it) passed through `temperature.biomeMappingFormula` — the value
      * this mod's apparent-temperature math starts from.
      */
    def mappedTemperature(pos: BlockPos, seaLevel: Int): Double = {
      val vanilla = biome
        .asInstanceOf[BiomeInvoker]
        .casualtiesbelow$invokeGetTemperature(pos, seaLevel)
      CasualtiesBelowConfig.temperature.biomeMappingFormula.evaluate(vanilla.toDouble)
    }
  }
}
