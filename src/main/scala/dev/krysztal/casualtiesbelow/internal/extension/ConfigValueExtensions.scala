package dev.krysztal.casualtiesbelow.internal.extension

import dev.krysztal.casualtiesbelow.physiology.discomfort.DiscomfortDistribution
import dev.krysztal.casualtiesbelow.physiology.pain.TotalPainStrategy

import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** Enrichments over [ConfigValue] so config reads read as properties.
  *
  * Concrete per-type overloads instead of one generic extension: under `-Yexplicit-nulls`, generic
  * inference anchors on the expected type (scala.Double) and clashes with the receiver's boxed Java
  * type (java.lang.Double), so a generic `extension [T](v: ConfigValue[T])` fails to construct at
  * use sites.
  */
private[casualtiesbelow] object ConfigValueExtensions {

  extension (v: ConfigValue[java.lang.Double]) {

    /** The current (cached) config value. */
    def value: java.lang.Double = v.get()
  }

  extension (v: ConfigValue[java.lang.Integer]) {
    def value: java.lang.Integer = v.get()
  }

  extension (v: ConfigValue[java.lang.Boolean]) {
    def value: java.lang.Boolean = v.get()
  }

  extension (v: ConfigValue[DiscomfortDistribution]) {
    def value: DiscomfortDistribution = v.get()
  }

  extension (v: ConfigValue[TotalPainStrategy]) {
    def value: TotalPainStrategy = v.get()
  }
}
