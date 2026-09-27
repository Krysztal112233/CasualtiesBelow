package dev.krysztal.casualtiesbelow.internal

private[casualtiesbelow] object TypeAlias {
  type JBoolean = java.lang.Boolean
  type JDouble = java.lang.Double
  type JInteger = java.lang.Integer

  type MojCodec[A] = com.mojang.serialization.Codec[A]
}
