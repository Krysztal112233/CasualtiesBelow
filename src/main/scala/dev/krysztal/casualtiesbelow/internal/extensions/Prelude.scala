package dev.krysztal.casualtiesbelow.internal.extensions

/** Crate-style prelude: one wildcard import brings every shared extension into scope.
  *
  * Extension receivers across the hub are disjoint types, so re-exporting everything together never
  * creates ambiguity at call sites.
  */
private[casualtiesbelow] object Prelude {
  export BiomeExtensions.*
  export ComponentExtensions.*
  export DoubleExtensions.*
  export FoodDataExtensions.*
  export ItemStackExtensions.*
  export LevelExtensions.*
  export MinecraftServerExtensions.*
  export PlayerExtensions.*
}
