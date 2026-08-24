package dev.krysztal.casualtiesbelow.ui.bodypart

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.LimbStats

/** Renders one body part of the body diagram at a given screen rect, reflecting the part's
  * [[LimbStats]] (damage coloring, hover highlight). Trembling is the caller's concern — the given
  * rect already includes the tremble offset.
  *
  * Implementations: [[SkinBodyPartRenderer]] (default, draws the player skin's texture regions) and
  * [[BlockBodyPartRenderer]] (fallback, solid-color blocks).
  */
trait BodyPartRenderer {

  /** Whether this renderer can render the given player right now (e.g. the skin texture is
    * available for the skin renderer).
    */
  def isAvailable(player: LocalPlayer): Boolean

  def extractPart(
      graphics: GuiGraphicsExtractor,
      player: LocalPlayer,
      part: BodyPart,
      stats: Option[LimbStats],
      hovered: Boolean,
      x: Int,
      y: Int,
      width: Int,
      height: Int
  ): Unit
}

object BodyPartRenderer {

  /** Whether the body diagram may be drawn from the player's skin texture. When false (future
    * config option, for compatibility with mods that replace the player model — those no longer
    * follow the standard 64×64 skin UV layout), the diagram always falls back to solid-color
    * blocks.
    */
  var UseSkinRendering = true

  /** Picks the renderer for the given player: the skin renderer when allowed and available, the
    * block fallback otherwise.
    */
  def forPlayer(player: LocalPlayer): BodyPartRenderer = {
    if (UseSkinRendering && SkinBodyPartRenderer.isAvailable(player)) {
      SkinBodyPartRenderer
    } else {
      BlockBodyPartRenderer
    }
  }
}
