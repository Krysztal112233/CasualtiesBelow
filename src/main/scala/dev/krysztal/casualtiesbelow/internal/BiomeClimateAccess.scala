package dev.krysztal.casualtiesbelow.internal

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

import net.minecraft.world.level.biome.Biome

/** Reads biome climate values that vanilla keeps on the private `Biome.ClimateSettings` record.
  *
  * The record type cannot be named from mod code (it is a private nested class), so a Mixin
  * accessor cannot declare it as a return type and Mixin's type-sensitive field matching rejects
  * `Object`/duck-interface declarations. `MethodHandles` sidesteps naming entirely: handles are
  * resolved once against the runtime class and cached, so per-tick reads are near-direct.
  *
  * Resolution is lazy but fail-fast: the first read throws with a clear message if the field or
  * accessor cannot be found, rather than silently degrading.
  */
object BiomeClimateAccess {

  private lazy val (climateSettingsGetter, downfallGetter) = {
    try {
      val field = classOf[Biome].getDeclaredField("climateSettings")
      val biomeLookup = MethodHandles.privateLookupIn(classOf[Biome], MethodHandles.lookup())
      val fieldGetter = biomeLookup.unreflectGetter(field)

      // The record's public component accessor; resolved on the runtime (private) record class so
      // its type never needs to be named.
      val settingsClass = field.getType
      val settingsLookup = MethodHandles.privateLookupIn(settingsClass, MethodHandles.lookup())
      val methodGetter =
        settingsLookup.findVirtual(settingsClass, "downfall", MethodType.methodType(classOf[Float]))

      (fieldGetter, methodGetter)
    } catch {
      case e: Throwable =>
        val err = new ExceptionInInitializerError(
          "Failed to resolve biome downfall handles on Biome.ClimateSettings"
        )
        err.initCause(e)
        throw err
    }
  }

  /** The biome's humidity value (0..1); the body-temperature system derives air dryness as
    * `1 - downfall`.
    */
  def downfall(biome: Biome): Float = {
    try {
      val settings = climateSettingsGetter.invoke(biome)
      downfallGetter.invoke(settings).asInstanceOf[Float]
    } catch {
      case e: Throwable =>
        throw new IllegalStateException(
          "Failed to read biome downfall via Biome.ClimateSettings",
          e
        )
    }
  }
}
