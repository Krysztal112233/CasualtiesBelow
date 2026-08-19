package dev.krysztal.casualtiesbelow.ui.bodypart

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType

import dev.krysztal.casualtiesbelow.component.BodyPart
import dev.krysztal.casualtiesbelow.component.LimbStats
import dev.krysztal.casualtiesbelow.ui.Argb

/** Default [[BodyPartRenderer]]: each part blits the matching front-face region of the player's
  * skin texture (base layer + overlay: hat/jacket/sleeve/pants) into its block rect. The arms'
  * texture regions are upright, so they are drawn upright and rotated 90° into their horizontal
  * T-pose position via the pose stack (right arm clockwise, left arm counter-clockwise, both around
  * the shoulder end). Damage tints the part red (the blit tint multiplies, so white leaves the
  * texture unchanged); hover draws a translucent white overlay on top.
  */
object SkinBodyPartRenderer extends BodyPartRenderer {

  /** Player skin texture dimensions (classic 64×64 layout). */
  private val SkinTextureSize = 64

  /** Damage tint target: the worse of skin/muscle damage lerps the blit tint toward this red. */
  private val DamagedTintColor = 0xffff4040

  /** Semi-transparent white drawn over a hovered part (blit tint can only darken). */
  private val HoverOverlayColor = 0x40ffffff

  override def isAvailable(player: LocalPlayer): Boolean = {
    player != null && player.getSkin.body() != null
  }

  override def extractPart(
      graphics: GuiGraphicsExtractor,
      player: LocalPlayer,
      part: BodyPart,
      stats: Option[LimbStats],
      hovered: Boolean,
      x: Int,
      y: Int,
      width: Int,
      height: Int
  ): Unit = {
    val skin = player.getSkin
    val texture = skin.body().texturePath()
    val slim = skin.model() == PlayerModelType.SLIM
    val region = SkinRegions(part)
    // Slim models have 3px-wide arms; only the arms are rotated regions.
    val srcWidth = if (slim && region.rotated) 3 else region.width
    val tint = stats match {
      case Some(s) =>
        val damage = math.max(
          1.0 - s.skinIntegrity / LimbStats.MaxValue,
          1.0 - s.muscleHealth / LimbStats.MaxValue
        )
        Argb.lerp(0xffffffff, DamagedTintColor, damage)
      case None => 0xffffffff
    }

    if (!region.rotated) {
      blit(graphics, texture, x, y, width, height, region, srcWidth, tint)
    } else {
      val pose = graphics.pose()
      pose.pushMatrix()
      // Draw upright (width = height, height = width) and rotate into the horizontal block
      // rect; the shoulder end (texture top) lands on the torso side.
      if (part == BodyPart.ArmRight) {
        pose.translate(x + width.toFloat, y.toFloat)
        pose.rotate((Math.PI / 2).toFloat)
      } else {
        pose.translate(x.toFloat, (y + height).toFloat)
        pose.rotate((-Math.PI / 2).toFloat)
      }
      blit(graphics, texture, 0, 0, height, width, region, srcWidth, tint)
      pose.popMatrix()
    }

    if (hovered) {
      graphics.fill(x, y, x + width, y + height, HoverOverlayColor)
    }
  }

  /** Blits the region's base layer and overlay layer at (`x`, `y`). */
  private def blit(
      graphics: GuiGraphicsExtractor,
      texture: Identifier,
      x: Int,
      y: Int,
      width: Int,
      height: Int,
      region: SkinRegion,
      srcWidth: Int,
      tint: Int
  ): Unit = {
    graphics.blit(
      RenderPipelines.GUI_TEXTURED,
      texture,
      x,
      y,
      region.u.toFloat,
      region.v.toFloat,
      width,
      height,
      srcWidth,
      region.height,
      SkinTextureSize,
      SkinTextureSize,
      tint
    )
    graphics.blit(
      RenderPipelines.GUI_TEXTURED,
      texture,
      x,
      y,
      region.overlayU.toFloat,
      region.overlayV.toFloat,
      width,
      height,
      srcWidth,
      region.height,
      SkinTextureSize,
      SkinTextureSize,
      tint
    )
  }

  /** Front-face region of one body part in the 64×64 skin texture (u, v, width, height in texture
    * pixels), plus its overlay layer (hat/jacket/sleeve/pants). `rotated` parts (the arms) are
    * stored upright and rotated 90° into their horizontal T-pose position at draw time.
    */
  private final case class SkinRegion(
      u: Int,
      v: Int,
      overlayU: Int,
      overlayV: Int,
      width: Int,
      height: Int,
      rotated: Boolean = false
  )

  private val SkinRegions: Map[BodyPart, SkinRegion] = Map(
    BodyPart.Head -> SkinRegion(8, 8, 40, 8, 8, 8),
    BodyPart.Torso -> SkinRegion(20, 20, 20, 36, 8, 12),
    BodyPart.ArmRight -> SkinRegion(44, 20, 44, 36, 4, 12, rotated = true),
    BodyPart.ArmLeft -> SkinRegion(36, 52, 52, 52, 4, 12, rotated = true),
    BodyPart.LegRight -> SkinRegion(4, 20, 4, 36, 4, 12),
    BodyPart.LegLeft -> SkinRegion(20, 52, 36, 52, 4, 12)
  )
}
