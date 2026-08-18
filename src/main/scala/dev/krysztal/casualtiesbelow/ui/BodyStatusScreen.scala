package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.Util

import dev.krysztal.casualtiesbelow.CasualtiesBelowClient

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
  * Content is a placeholder for now.
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
    val placeholder = Component.translatable("screen.casualtiesbelow.body_status.placeholder")
    graphics.text(
      this.font,
      placeholder,
      x + (panelWidth - this.font.width(placeholder)) / 2,
      y + panelHeight / 2,
      BodyStatusScreen.PlaceholderColor,
      false
    )
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
  private val PanelWidth = 240
  private val PanelHeight = 120

  private val OpenDurationMs = 280
  private val CloseDurationMs = 200

  /** 1px outline around the panel. */
  private val BorderWidth = 1
  private val TitleTopPadding = 8

  // Colors are 32-bit ARGB (0xAARRGGBB).
  private val PanelBorderColor = 0xff3a3a3a // opaque dark gray
  private val PanelFillColor = 0xc0181818 // 75%-opaque near-black
  private val TitleColor = 0xffffffff // opaque white
  private val PlaceholderColor = 0xffa0a0a0 // opaque light gray
}
