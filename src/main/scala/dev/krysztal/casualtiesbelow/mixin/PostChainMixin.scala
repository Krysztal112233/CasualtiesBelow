package dev.krysztal.casualtiesbelow.mixin

import scala.annotation.static

import net.minecraft.client.renderer.PostChain
import net.minecraft.resources.Identifier

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Lets a post-effect JSON texture input reference an arbitrary texture by absolute path. Vanilla
  * pins TextureInput locations under `textures/effect/`, so the frost overlay could not reuse
  * vanilla's own powder-snow outline texture (`textures/misc/powder_snow_outline.png`) without
  * shipping a copy of a Mojang asset.
  *
  * Convention: a location whose path already starts with `textures/` is taken verbatim — the
  * vanilla prefixing is skipped. Anything else behaves exactly like vanilla.
  */
@Mixin(value = Array(classOf[PostChain]), remap = false)
abstract class PostChainMixin

object PostChainMixin {

  @static
  @ModifyExpressionValue(
    method = Array("createPass"),
    at = Array(
      new At(
        value = "INVOKE",
        target =
          "Lnet/minecraft/resources/Identifier;withPath(Ljava/util/function/UnaryOperator;)Lnet/minecraft/resources/Identifier;"
      )
    ),
    remap = false
  )
  private def casualtiesbelow$absoluteTextureEffectPath(original: Identifier): Identifier = {
    // withPath has already applied "textures/effect/" + path + ".png"; an absolute reference
    // written as "textures/..." in the JSON surfaces here with that double prefix.
    val absoluteMarker = "textures/effect/textures/"
    if (original.getPath.startsWith(absoluteMarker)) {
      Identifier.fromNamespaceAndPath(
        original.getNamespace,
        original.getPath.substring("textures/effect/".length)
      )
    } else {
      original
    }
  }
}
