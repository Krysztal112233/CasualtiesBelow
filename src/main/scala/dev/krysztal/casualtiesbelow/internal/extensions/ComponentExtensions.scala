package dev.krysztal.casualtiesbelow.internal.extensions

import net.minecraft.network.chat.Component

/** Enrichments over [String] for literal text component construction.
  *
  * Use `"key".translatable()` and `"text".literal` for compile-time string literals; keep
  * `Component.translatable(key)` / `Component.literal(text)` for dynamic keys and computed text so
  * the static factory signals runtime-built input.
  */
private[casualtiesbelow] object ComponentExtensions {

  extension (key: String) {

    /** A translatable component for this literal translation key, with optional format args. */
    def translatable(args: Any*): Component = Component.translatable(key, args*)

    /** A literal component for this literal text. */
    def literal: Component = Component.literal(key)
  }
}
