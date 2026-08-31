package dev.krysztal.casualtiesbelow.ui.bodypart

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer

import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.ui.Argb

/** Fallback [[BodyPartRenderer]]: each part is a solid block with a 1px outline (adjacent parts
  * share outline edges, giving 1px visual separation). The outline maps skin integrity and the fill
  * maps muscle health, both lerping gray → red as the stat drops; hover brightens the fill.
  */
object BlockBodyPartRenderer extends BodyPartRenderer {

  /** The block fallback can always render. */
  override def isAvailable(player: LocalPlayer): Boolean = true

  // Colors are 32-bit ARGB (0xAARRGGBB).
  private val OutlineColor = 0xff3a3a3a // opaque dark gray (healthy skin)
  private val FillColor = 0xff8a8a8a // opaque mid gray (healthy muscle)
  private val SkinDamagedOutlineColor = 0xffe74c3c // opaque bright red
  private val MuscleDamagedFillColor = 0xffb03a30 // opaque deep red

  /** How much a hovered part's fill is lerped toward white. */
  private val HoverBrighten = 0.3

  override def extractPart(
      graphics: GuiGraphicsExtractor,
      player: LocalPlayer,
      part: BodyPart,
      stats: Option[LimbSnapshot],
      hovered: Boolean,
      x: Int,
      y: Int,
      width: Int,
      height: Int
  ): Unit = {
    val outlineColor = stats match {
      case Some(s) =>
        Argb.lerp(
          OutlineColor,
          SkinDamagedOutlineColor,
          1.0 - s.skinIntegrity / LimbSnapshot.MaxValue
        )
      case None => OutlineColor
    }
    graphics.fill(x, y, x + width, y + height, outlineColor)

    val baseFillColor = stats match {
      case Some(s) =>
        Argb.lerp(
          FillColor,
          MuscleDamagedFillColor,
          1.0 - s.muscleHealth / LimbSnapshot.MaxValue
        )
      case None => FillColor
    }
    val fillColor =
      if (hovered) Argb.lerp(baseFillColor, 0xffffffff, HoverBrighten)
      else baseFillColor
    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor)
  }
}
