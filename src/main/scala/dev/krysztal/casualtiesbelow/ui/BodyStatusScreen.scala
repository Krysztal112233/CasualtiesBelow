package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.Util

import dev.krysztal.casualtiesbelow.CasualtiesBelowClient
import dev.krysztal.casualtiesbelow.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.component.BodyPart
import dev.krysztal.casualtiesbelow.component.LimbStats

/** Body status screen, summoned by the open-screen keybind (default: R). Pressing the keybind again
  * or Esc closes it.
  *
  * Opening plays a slide-up animation and closing a slide-down one, while the background dim ramps
  * in/out with the same (eased) progress. Closing is animated: a close request starts the exit
  * animation and the screen only removes itself once the progress reaches 0; pressing the keybind
  * again mid-close reverses back into the opening animation. The background blur itself is
  * vanilla's binary `blurBeforeThisStratum` — 26.2 has no progressive blur API, so only the dim
  * overlay follows the animation progress.
  *
  * Content is a front-view T-pose outline of the player's body, one block per [[BodyPart]], laid
  * out from the player model's front silhouette (32×32 in model/skin pixels with the arms raised
  * horizontal, see `HumanoidModel.createMesh`).
  *
  * Each part reflects its synced [[LimbStats]]: the outline reddens as skin integrity drops, the
  * fill reddens as muscle health drops, and the block trembles while the limb is in pain (amplitude
  * scales with pain). The hovered part is brightened and shows its localized name as a tooltip.
  */
class BodyStatusScreen
    extends Screen(Component.translatable("screen.casualtiesbelow.body_status")) {

  /** Wall-clock open timestamp; keeps animating even while the game is paused. */
  private var openedAtMs = Util.getMillis()

  /** Close-animation state: when the close was requested and the progress at that moment. */
  private var closedAtMs = 0L
  private var progressAtClose = 0f
  private var closing = false

  /** Raw open progress in [0, 1]; counts back down to 0 while closing. */
  private def openProgress: Float = {
    if (!closing) {
      Mth.clamp(
        (Util.getMillis() - openedAtMs).toFloat / BodyStatusScreen.OpenDurationMs,
        0f,
        1f
      )
    } else {
      progressAtClose * (1f - Mth.clamp(
        (Util.getMillis() - closedAtMs).toFloat / BodyStatusScreen.CloseDurationMs,
        0f,
        1f
      ))
    }
  }

  /** Ease-out cubic of [[openProgress]]. */
  private def easedProgress: Float = {
    val inverse = 1f - openProgress
    1f - inverse * inverse * inverse
  }

  override def extractBackground(
      graphics: GuiGraphicsExtractor,
      mouseX: Int,
      mouseY: Int,
      a: Float
  ): Unit = {
    extractBlurredBackground(graphics)
    val alpha = (0xc0 * easedProgress).toInt
    if (alpha > 0) {
      graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x101010)
    }
    this.minecraft.gui.hud.extractDeferredSubtitles()
  }

  override def extractRenderState(
      graphics: GuiGraphicsExtractor,
      mouseX: Int,
      mouseY: Int,
      a: Float
  ): Unit = {
    super.extractRenderState(graphics, mouseX, mouseY, a)

    val panelWidth = BodyStatusScreen.PanelWidth
    val panelHeight = BodyStatusScreen.PanelHeight
    val x = (this.width - panelWidth) / 2
    val settledY = (this.height - panelHeight) / 2
    // Start fully below the screen's bottom edge, slide up to the center.
    val y = Mth.lerpInt(easedProgress, this.height, settledY)

    graphics.fill(
      x - BodyStatusScreen.BorderWidth,
      y - BodyStatusScreen.BorderWidth,
      x + panelWidth + BodyStatusScreen.BorderWidth,
      y + panelHeight + BodyStatusScreen.BorderWidth,
      BodyStatusScreen.PanelBorderColor
    )
    graphics.fill(x, y, x + panelWidth, y + panelHeight, BodyStatusScreen.PanelFillColor)

    val title = Component.translatable("screen.casualtiesbelow.body_status")
    graphics.text(
      this.font,
      title,
      x + (panelWidth - this.font.width(title)) / 2,
      y + BodyStatusScreen.TitleTopPadding,
      BodyStatusScreen.TitleColor,
      true
    )

    extractBody(
      graphics,
      x + (panelWidth - BodyStatusScreen.BodyWidth) / 2,
      y + BodyStatusScreen.BodyTopPadding,
      mouseX,
      mouseY
    )
  }

  /** Draws the body outline: each part is a solid block with a 1px outline (adjacent parts share
    * outline edges, giving 1px visual separation). The outline maps skin integrity and the fill
    * maps muscle health, both lerping gray → red as the stat drops; a limb in pain trembles with an
    * amplitude proportional to its pain. The hovered part (hit-tested against the un-trembled base
    * rect, so the highlight stays stable under trembling) is brightened and shows its localized
    * name as a tooltip.
    */
  private def extractBody(
      graphics: GuiGraphicsExtractor,
      originX: Int,
      originY: Int,
      mouseX: Int,
      mouseY: Int
  ): Unit = {
    val scale = BodyStatusScreen.BodyScale
    // ComponentKey.get is null when the provider has no such component; Option wraps that.
    val body = Option(CasualtiesBelowComponents.Body.get(this.minecraft.player))
    val hovered = BodyStatusScreen.PartLayout.find { rect =>
      mouseX >= originX + rect.x * scale && mouseX < originX + (rect.x + rect.width) * scale &&
      mouseY >= originY + rect.y * scale && mouseY < originY + (rect.y + rect.height) * scale
    }

    BodyStatusScreen.PartLayout.foreach { rect =>
      val stats = body.map(_.stats(rect.part))
      val (offsetX, offsetY) = stats match {
        case Some(s) if s.pain > 0.0 => BodyStatusScreen.trembleOffset(rect.part, s.pain)
        case _                       => (0, 0)
      }
      val px = originX + rect.x * scale + offsetX
      val py = originY + rect.y * scale + offsetY
      val pw = rect.width * scale
      val ph = rect.height * scale

      val outlineColor = stats match {
        case Some(s) =>
          BodyStatusScreen.lerpArgb(
            BodyStatusScreen.PartOutlineColor,
            BodyStatusScreen.SkinDamagedOutlineColor,
            1.0 - s.skinIntegrity / LimbStats.MaxValue
          )
        case None => BodyStatusScreen.PartOutlineColor
      }
      graphics.fill(px, py, px + pw, py + ph, outlineColor)

      val baseFillColor = stats match {
        case Some(s) =>
          BodyStatusScreen.lerpArgb(
            BodyStatusScreen.PartFillColor,
            BodyStatusScreen.MuscleDamagedFillColor,
            1.0 - s.muscleHealth / LimbStats.MaxValue
          )
        case None => BodyStatusScreen.PartFillColor
      }
      val fillColor =
        if (hovered.contains(rect)) {
          BodyStatusScreen.lerpArgb(baseFillColor, 0xffffffff, BodyStatusScreen.HoverBrighten)
        } else baseFillColor
      graphics.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, fillColor)
    }

    hovered.foreach { rect =>
      graphics.setTooltipForNextFrame(
        Component.translatable(s"bodypart.casualtiesbelow.${rect.part.id}"),
        mouseX,
        mouseY
      )
    }
  }

  /** Starts the closing animation instead of closing immediately; the screen removes itself in
    * [[tick]] once the animation reaches 0. Called again mid-close (R or Esc), it reverses back
    * into the opening animation, continuing from the current progress.
    */
  override def onClose(): Unit = {
    if (!closing) {
      progressAtClose = openProgress
      closedAtMs = Util.getMillis()
      closing = true
    } else {
      // Re-open: rewind the open clock so the opening animation continues from current progress.
      openedAtMs = Util.getMillis() - (openProgress * BodyStatusScreen.OpenDurationMs).toLong
      closing = false
    }
  }

  override def tick(): Unit = {
    if (closing && openProgress <= 0f) {
      // Actually remove the screen; done on the tick rather than mid-render.
      this.minecraft.gui.setScreen(null)
    }
  }

  override def keyPressed(event: KeyEvent): Boolean = {
    if (CasualtiesBelowClient.OpenScreenKey.matches(event)) {
      onClose()
      true
    } else {
      // Esc closes via vanilla Screen.keyPressed (shouldCloseOnEsc).
      super.keyPressed(event)
    }
  }
}

object BodyStatusScreen {
  private val PanelWidth = 176
  private val PanelHeight = 164

  /** Scale of the body diagram relative to model pixels (the T-pose silhouette is 32×32 model
    * pixels).
    */
  private val BodyScale = 4
  private val BodyWidth = 32 * BodyScale
  private val BodyHeight = 32 * BodyScale

  /** Padding between the top of the panel and the top of the body diagram. */
  private val BodyTopPadding = 26

  private val OpenDurationMs = 280
  private val CloseDurationMs = 200

  /** 1px outline around the panel. */
  private val BorderWidth = 1
  private val TitleTopPadding = 8

  // Colors are 32-bit ARGB (0xAARRGGBB).
  private val PanelBorderColor = 0xff3a3a3a // opaque dark gray
  private val PanelFillColor = 0xc0181818 // 75%-opaque near-black
  private val TitleColor = 0xffffffff // opaque white
  private val PartOutlineColor = 0xff3a3a3a // opaque dark gray (healthy skin)
  private val PartFillColor = 0xff8a8a8a // opaque mid gray (healthy muscle)
  private val SkinDamagedOutlineColor = 0xffe74c3c // opaque bright red
  private val MuscleDamagedFillColor = 0xffb03a30 // opaque deep red

  /** How much a hovered part's fill is lerped toward white. */
  private val HoverBrighten = 0.3

  /** Tremble amplitude at maximum pain, in screen pixels. */
  private val MaxTremblePixels = 1.4

  /** Tremble angular frequency in radians per millisecond (period ≈ 126ms). */
  private val TrembleFrequency = 0.1f

  /** Per-channel ARGB lerp; `progress` is clamped to [0, 1]. */
  private def lerpArgb(from: Int, to: Int, progress: Double): Int = {
    val p = Mth.clamp(progress, 0.0, 1.0)
    def channel(shift: Int): Int = {
      val a = (from >> shift) & 0xff
      val b = (to >> shift) & 0xff
      (a + ((b - a) * p).toInt) & 0xff
    }
    (channel(24) << 24) | (channel(16) << 16) | (channel(8) << 8) | channel(0)
  }

  /** Pixel offset for a limb in pain: two detuned sines per axis give an irregular jitter; the
    * per-part phase keeps limbs from shaking in lockstep. Wall-clock driven, so it keeps animating
    * while the game is paused (matching the open/close animation).
    */
  private def trembleOffset(part: BodyPart, pain: Double): (Int, Int) = {
    val amplitude = pain / LimbStats.MaxValue * MaxTremblePixels
    val t = Util.getMillis().toFloat * TrembleFrequency
    val phase = part.ordinal * 1.37f
    val dx = Mth.sin(t + phase) * amplitude +
      Mth.sin(t * 2.7f + phase * 2f) * amplitude * 0.4
    val dy = Mth.cos(t * 1.3f + phase) * amplitude * 0.6
    (Math.round(dx).toInt, Math.round(dy).toInt)
  }

  /** One part's rectangle within the 32×32 T-pose silhouette, in model pixels.
    *
    * Front view (as if facing the player): the player's right side is on the viewer's left, so
    * [[BodyPart.ArmRight]]/[[BodyPart.LegRight]] are the left-hand blocks. Arms are raised
    * horizontal at shoulder height: each 4×12 arm becomes a 12×4 block aligned with the torso's top
    * edge.
    */
  private final case class PartRect(part: BodyPart, x: Int, y: Int, width: Int, height: Int)

  private val PartLayout: List[PartRect] = List(
    PartRect(BodyPart.Head, 12, 0, 8, 8),
    PartRect(BodyPart.Torso, 12, 8, 8, 12),
    PartRect(BodyPart.ArmRight, 0, 8, 12, 4),
    PartRect(BodyPart.ArmLeft, 20, 8, 12, 4),
    PartRect(BodyPart.LegRight, 12, 20, 4, 12),
    PartRect(BodyPart.LegLeft, 16, 20, 4, 12)
  )
}
