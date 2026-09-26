package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.util.Util
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extensions.Prelude.*
import dev.krysztal.casualtiesbelow.internal.sync.InjectionBatchPayload
import dev.krysztal.casualtiesbelow.internal.sync.InjectionSync
import dev.krysztal.casualtiesbelow.item.InjectionSession
import dev.krysztal.casualtiesbelow.item.LiquidContents
import dev.krysztal.casualtiesbelow.item.SyringeContents
import dev.krysztal.casualtiesbelow.item.SyringeItem

import org.lwjgl.glfw.GLFW

/** The injection minigame screen, opened by using a filled syringe.
  *
  * The scene lives in a centered viewport (60% of the window width, 80% of its height); the full
  * window only gets the dim backdrop. The bottom third of the viewport is the skin — a baked
  * skin-tone texture, its side and bottom edges feathered to transparency so it blends into the
  * backdrop. In the PENDING phase the syringe is a scene object, not a cursor shadow: it only moves
  * while grabbed, and then freely in both axes within the viewport. One continuous hold drives the
  * whole procedure — no re-grabs: dragging down presses the needle against the skin (the last few
  * pixels of travel are an invisible resistance zone), piercing at the press depth locks the needle
  * in place, and pulling further down immediately becomes plunger pressure — the cursor-to-pad gap
  * maps linearly to injection speed and eases itself as the plunger descends; lifting the cursor
  * eases the speed back to zero and keeps raising the barrel, sliding the needle out, and the tip
  * crossing the skin line returns to PENDING, still held, with the already-pushed dose settled and
  * the remainder traveling with the syringe. Releasing mid-drag lets go where the syringe is;
  * closing the screen (Esc) likewise keeps the remainder for a later session. The plunger position
  * is the cumulative injected amount, so closing the screen (Esc) simply keeps the remainder for a
  * later session.
  *
  * Progress is accumulated locally ([[InjectionSession]]) and reported to the server in batches;
  * settlement (dose, side effects, remainder, consumption) is server-authoritative. Calibrated
  * syringes additionally show barrel scale marks; unmarked ones offer no references.
  *
  * The whole scene fades in on open and out on close (alpha ramps on a wall-clock timer, the same
  * animation culture as [[BodyStatusScreen]]). A close request (Esc, or the empty-syringe beat)
  * stops injection and input immediately, reports any pending batch, and the screen removes itself
  * once the fade completes; the blur backdrop is vanilla's binary one, so only the dim and the
  * scene follow the fade.
  */
class InjectionScreen private (
    hand: InteractionHand,
    contents: SyringeContents,
    calibrated: Boolean
) extends Screen("screen.casualtiesbelow.injection".translatable()) {

  /** A screen that pauses the world would let players inject safely mid-combat; keep it ticking,
    * like the body status screen.
    */
  override def isPauseScreen(): Boolean = false

  private val session =
    new InjectionSession(LiquidContents.AmpouleDroplets, contents.droplets)
  private val liquid = contents.liquid

  private var pierced = false
  private var grabbingSyringe = false
  private var speedFraction = 0.0

  /** Syringe center X; free while pending, locked once the needle pierces. Seeded at the viewport
    * center on open and on resize (pre-pierce state only).
    */
  private var syringeX = 0
  private var barrelTopY = 0

  /** Mouse minus syringe origin at grab time, preserved so the syringe does not jump when grabbed.
    */
  private var grabOffsetX = 0
  private var grabOffsetY = 0

  private var lastAdvanceMs = Util.getMillis()
  private var lastFlushMs = Util.getMillis()
  private var emptyCloseAtMs = 0L

  /** Fade state: wall-clock open timestamp; once `closing` is set, `closedAtMs` marks when the
    * close was requested. No reversal — a close request always completes (unlike the body status
    * screen, this one has in-flight injection state that must wind down exactly once).
    */
  private var openedAtMs = Util.getMillis()
  private var closing = false
  private var closedAtMs = 0L

  /** Raw fade progress in [0, 1]: ramps up after open, back down while closing. */
  private def fadeProgress: Float = {
    if (!closing) {
      Mth.clamp(
        (Util.getMillis() - openedAtMs).toFloat / InjectionScreen.FadeInDurationMs,
        0f,
        1f
      )
    } else {
      1f - Mth.clamp(
        (Util.getMillis() - closedAtMs).toFloat / InjectionScreen.FadeOutDurationMs,
        0f,
        1f
      )
    }
  }

  /** Ease-out cubic of [[fadeProgress]], matching the body status screen's feel. */
  private def fade: Float = {
    val inverse = 1f - fadeProgress
    1f - inverse * inverse * inverse
  }

  /** `color` with its alpha channel scaled by the current screen fade. */
  private def faded(color: Int): Int = {
    val alpha = ((color >>> 24) * fade).toInt
    (alpha << 24) | (color & 0x00ffffff)
  }

  override def init(): Unit = {
    // Vanilla re-invokes init() on window resize; a pierced syringe is locked in place, so only
    // the pending-phase geometry may be (re)seeded here — back to the anchor above the skin, which
    // is safe because nothing has been committed before piercing.
    if (!pierced) {
      syringeX = regionCenterX
      barrelTopY = homeTopY(skinTopY)
    }
  }

  override def extractBackground(
      graphics: GuiGraphicsExtractor,
      mouseX: Int,
      mouseY: Int,
      a: Float
  ): Unit = {
    extractBlurredBackground(graphics)
    graphics.fill(0, 0, width, height, faded(InjectionScreen.BackgroundDim))
    minecraft.gui.hud.extractDeferredSubtitles()
  }

  override def extractRenderState(
      graphics: GuiGraphicsExtractor,
      mouseX: Int,
      mouseY: Int,
      a: Float
  ): Unit = {
    super.extractRenderState(graphics, mouseX, mouseY, a)
    advanceSession(mouseY)
    flushIfDue()

    val skinTop = skinTopY
    extractSkin(graphics, skinTop)

    if (!pierced) {
      updatePendingDrag(mouseX, mouseY, skinTop)
    } else {
      updatePiercedDrag(mouseY, skinTop)
    }
    extractSyringe(graphics, skinTop)

    graphics.text(
      font,
      this.title,
      (width - font.width(this.title)) / 2,
      regionTop + InjectionScreen.TitleTopPadding,
      faded(InjectionScreen.TextColor),
      true
    )
    val hint = Component.translatable(
      if (pierced) "screen.casualtiesbelow.injection.hint_inject"
      else "screen.casualtiesbelow.injection.hint_pierce"
    )
    graphics.text(
      font,
      hint,
      (width - font.width(hint)) / 2,
      regionTop + InjectionScreen.TitleTopPadding + InjectionScreen.HintSpacing,
      faded(InjectionScreen.HintColor),
      true
    )
  }

  /** Advances the plunger from the current press gap and wall-clock delta, then reports due
    * batches. Speed is recomputed every frame: it is nonzero only while the plunger is held and
    * pressed below its rest position.
    */
  private def advanceSession(mouseY: Int): Unit = {
    val now = Util.getMillis()
    val dtSeconds = ((now - lastAdvanceMs).toDouble / 1000.0).min(InjectionScreen.MaxDtSeconds)
    lastAdvanceMs = now

    speedFraction =
      if (pierced && grabbingSyringe && emptyCloseAtMs == 0L && !closing) {
        // Only a needle pinned against the pierce depth converts cursor travel into plunger
        // pressure; while the barrel is lifting the needle out, speed stays zero.
        val deepestTop = skinTopY + InjectionScreen.PierceDepthPixels -
          InjectionLayout.NeedleLength - InjectionLayout.BarrelHeight
        if (barrelTopY >= deepestTop) {
          val gap = (mouseY - plungerPadCenterY).toDouble
          (gap / Consts.Injection.FullSpeedPressDepthPixels.toDouble)
            .max(0.0)
            .min(1.0)
        } else 0.0
      } else 0.0

    if (speedFraction > 0.0) {
      val droplets = speedFraction *
        Consts.Injection.MaxSpeedFractionPerSecond *
        LiquidContents.AmpouleDroplets.toDouble * dtSeconds
      session.advance(speedFraction, droplets)
    }

    if (session.fullyInjected && emptyCloseAtMs == 0L) {
      flushPending()
      playUiSound(SoundEvents.BOTTLE_EMPTY, 0.8f, 1.2f)
      emptyCloseAtMs = now
    }
  }

  /** UI feedback through the local player, if present. */
  private def playUiSound(event: SoundEvent, volume: Float, pitch: Float): Unit =
    Option(minecraft.player).foreach(_.playSound(event, volume, pitch))

  private def flushIfDue(): Unit = {
    val now = Util.getMillis()
    if (
      now - lastFlushMs >=
        Consts.Injection.BatchIntervalMilliseconds.longValue()
    ) {
      flushPending()
    }
  }

  private def flushPending(): Unit = {
    lastFlushMs = Util.getMillis()
    if (!ClientPlayNetworking.canSend(InjectionSync.PayloadId)) return
    // Baseline before flush(): the batch must identify the stack as it stands once every
    // previously sent batch has settled.
    val expectedDroplets = session.expectedStackDroplets
    session.flush().foreach { batch =>
      ClientPlayNetworking.send(
        InjectionBatchPayload(
          hand == InteractionHand.MAIN_HAND,
          liquid,
          expectedDroplets,
          batch.droplets,
          batch.averageSpeed
        )
      )
    }
  }

  /** Pending-phase dragging: the syringe moves freely in both axes while grabbed, clamped to the
    * viewport and — vertically — between the home position and the pierce depth, so it cannot rise
    * above the title or sink below the press limit; releasing mid-drag lets go of it where it is.
    * The needle goes through once its tip reaches the pierce depth, locking the syringe in place.
    */
  private def updatePendingDrag(mouseX: Int, mouseY: Int, skinTop: Int): Unit = {
    if (!grabbingSyringe) return
    syringeX = Mth.clamp(
      mouseX - grabOffsetX,
      regionLeft + InjectionScreen.BarrelWidth,
      regionRight - InjectionScreen.BarrelWidth
    )
    val homeTop = homeTopY(skinTop)
    val deepestTop = skinTop + InjectionScreen.PierceDepthPixels -
      InjectionLayout.NeedleLength - InjectionLayout.BarrelHeight
    barrelTopY = Mth.clamp(
      mouseY - grabOffsetY,
      homeTop.max(regionTop + InjectionScreen.TitleClearanceY),
      deepestTop
    )
    if (needleTipY >= skinTop + InjectionScreen.PierceDepthPixels) {
      // The hold continues seamlessly: from here the same grab drives injection and withdrawal.
      pierced = true
      playUiSound(SoundEvents.BOTTLE_FILL, 0.6f, 1.4f)
    }
  }

  /** Pierced-phase drag — one continuous hold from piercing onward, no re-grab: while the cursor
    * pulls at or below the pierce depth the needle stays pinned and the pull becomes plunger
    * pressure (speed via [[advanceSession]]'s cursor-to-pad gap, the inherited injection logic);
    * lifting the cursor eases speed back to zero and keeps raising the barrel, sliding the needle
    * out — the tip crossing the skin line returns to PENDING, still held. The needle never goes
    * deeper or moves sideways inside the skin.
    */
  private def updatePiercedDrag(mouseY: Int, skinTop: Int): Unit = {
    if (!grabbingSyringe) return
    val deepestTop = skinTop + InjectionScreen.PierceDepthPixels -
      InjectionLayout.NeedleLength - InjectionLayout.BarrelHeight
    val desiredTop = mouseY - grabOffsetY
    if (desiredTop >= deepestTop) {
      barrelTopY = deepestTop
    } else {
      barrelTopY = Mth.clamp(desiredTop, regionTop + InjectionScreen.TitleClearanceY, deepestTop)
      if (needleTipY <= skinTop) {
        pierced = false
        playUiSound(SoundEvents.BOTTLE_FILL, 0.6f, 0.7f)
      }
    }
  }

  /** Centered viewport the scene is laid out in (fractions of the window, centered on both axes);
    * the full window only gets the dim backdrop. Derived from `width`/`height`, so a window resize
    * re-derives everything downstream.
    */
  // Viewport geometry is pure layout; syringe drag state stays on the screen.
  private def layout: InjectionLayout = InjectionLayout(width, height)

  private def regionLeft: Int = layout.regionLeft
  private def regionTop: Int = layout.regionTop
  private def regionRight: Int = layout.regionRight
  private def regionBottom: Int = layout.regionBottom
  private def regionCenterX: Int = layout.regionCenterX
  private def skinTopY: Int = layout.skinTopY
  private def homeTopY(skinTop: Int): Int = layout.homeTopY(skinTop)

  private def needleTipY: Int =
    barrelTopY + InjectionLayout.BarrelHeight + InjectionLayout.NeedleLength

  /** Plunger seal depth within the barrel: the plunger is the cumulative injected amount, so a
    * syringe resumed after an abort starts partway down.
    */
  private def plungerSealY: Int = {
    val fraction = 1.0 - session.remainingFractionOfFull
    barrelTopY + InjectionScreen.BarrelPadding +
      (fraction * InjectionScreen.PlungerTravel).toInt
  }

  private def plungerPadCenterY: Int =
    plungerSealY - InjectionScreen.PlungerRodLength - InjectionScreen.PlungerPadHeight / 2

  /** The skin band is a baked skin texture (epidermis-to-subcutaneous gradient) whose side and
    * bottom edges are feathered to transparency inside the texture itself, so the band blends into
    * the dimmed backdrop rather than fading toward any fixed color. The top edge stays a crisp
    * line, inset by the feather so it dissolves alongside the skin: it is the pierce reference at
    * the syringe's position, where the band is fully opaque.
    */
  private def extractSkin(graphics: GuiGraphicsExtractor, skinTop: Int): Unit = {
    graphics.blit(
      RenderPipelines.GUI_TEXTURED,
      InjectionScreen.SkinTexture,
      regionLeft,
      skinTop,
      0f,
      0f,
      regionRight - regionLeft,
      regionBottom - skinTop,
      InjectionScreen.SkinTextureWidth,
      InjectionScreen.SkinTextureHeight,
      faded(0xffffffff)
    )
    val feather =
      (regionRight - regionLeft) * InjectionScreen.SkinEdgeFeather /
        InjectionScreen.SkinTextureWidth
    graphics.fill(
      regionLeft + feather,
      skinTop,
      regionRight - feather,
      skinTop + InjectionScreen.SkinLineThickness,
      faded(InjectionScreen.SkinLineColor)
    )
  }

  private def extractSyringe(graphics: GuiGraphicsExtractor, skinTop: Int): Unit = {
    val left = syringeX - InjectionScreen.BarrelWidth / 2
    val right = syringeX + InjectionScreen.BarrelWidth / 2
    val bottom = barrelTopY + InjectionLayout.BarrelHeight

    // Needle (drawn first so the barrel overlaps its top). Pre-pierce the tip is clamped at the
    // skin line: the press travel below the surface is an invisible resistance zone (input only),
    // the needle is never drawn clipping into un-pierced skin. Pierced, the tip tracks the barrel
    // so withdrawal slides the needle out instead of stretching it.
    val needleBottom =
      if (pierced) needleTipY
      else needleTipY.min(skinTop)
    graphics.fill(
      syringeX - InjectionScreen.NeedleWidth / 2,
      bottom,
      syringeX + InjectionScreen.NeedleWidth / 2,
      needleBottom,
      faded(InjectionScreen.NeedleColor)
    )

    // Barrel.
    graphics.fill(left, barrelTopY, right, bottom, faded(InjectionScreen.BarrelFillColor))
    outline(graphics, left, barrelTopY, right, bottom, faded(InjectionScreen.BarrelBorderColor))

    // Remaining liquid between the plunger seal and the barrel bottom.
    val sealY = plungerSealY
    if (sealY < bottom - InjectionScreen.BarrelPadding) {
      graphics.fill(
        left + 1,
        sealY,
        right - 1,
        bottom - InjectionScreen.BarrelPadding,
        faded(liquidColor)
      )
    }

    // Plunger rod and thumb pad above the seal.
    val padTop = plungerPadCenterY - InjectionScreen.PlungerPadHeight / 2
    graphics.fill(
      syringeX - InjectionScreen.PlungerRodWidth / 2,
      padTop + InjectionScreen.PlungerPadHeight,
      syringeX + InjectionScreen.PlungerRodWidth / 2,
      sealY,
      faded(InjectionScreen.PlungerColor)
    )
    graphics.fill(
      syringeX - InjectionScreen.PlungerPadWidth / 2,
      padTop,
      syringeX + InjectionScreen.PlungerPadWidth / 2,
      padTop + InjectionScreen.PlungerPadHeight,
      faded(InjectionScreen.PlungerColor)
    )

    if (calibrated) {
      var mark = 0
      while (mark <= InjectionScreen.ScaleMarkCount) {
        val y = barrelTopY + InjectionScreen.BarrelPadding +
          mark * InjectionScreen.PlungerTravel / InjectionScreen.ScaleMarkCount
        graphics.fill(right + 2, y, right + 5, y + 1, faded(InjectionScreen.ScaleMarkColor))
        mark += 1
      }
      // Live remaining-capacity readout, riding the plunger seal so the number tracks the liquid
      // level; one full syringe is 1 mL (see LiquidContents.DropletsPerMilliliter).
      val remainingMl = session.remainingExact / LiquidContents.DropletsPerMilliliter.toDouble
      val label = f"$remainingMl%.2f mL"
      graphics.text(
        font,
        label,
        right + 8,
        sealY - font.lineHeight / 2,
        faded(InjectionScreen.TextColor),
        true
      )
    }
  }

  private def liquidColor: Int = {
    if (liquid == LiquidContents.RefinedPoppyExtract.liquid) InjectionScreen.RefinedLiquidColor
    else if (liquid == LiquidContents.CrudePoppyLiquid.liquid) InjectionScreen.CrudeLiquidColor
    else InjectionScreen.UnknownLiquidColor
  }

  private def outline(
      graphics: GuiGraphicsExtractor,
      left: Int,
      top: Int,
      right: Int,
      bottom: Int,
      color: Int
  ): Unit = {
    graphics.fill(left, top, right, top + 1, color)
    graphics.fill(left, bottom - 1, right, bottom, color)
    graphics.fill(left, top, left + 1, bottom, color)
    graphics.fill(right - 1, top, right, bottom, color)
  }

  override def mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean = {
    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && emptyCloseAtMs == 0L && !closing) {
      val mouseX = event.x().toInt
      val mouseY = event.y().toInt
      if (isOverSyringe(mouseX, mouseY)) {
        // One continuous hold drives both phases: down pierces then presses the plunger, up eases
        // off then pulls the needle out.
        grabbingSyringe = true
        grabOffsetX = mouseX - syringeX
        grabOffsetY = mouseY - barrelTopY
        return true
      }
    }
    super.mouseClicked(event, doubleClick)
  }

  override def mouseReleased(event: MouseButtonEvent): Boolean = {
    // Vanilla routes release events to the open screen regardless of cursor position, so a pause
    // always lands even when the cursor has left the window. Symmetrically, holding with the
    // cursor parked outside the window freezes mouseY and injects at a constant speed until
    // re-entry — harmless, so no special handling.
    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && grabbingSyringe) {
      // Releasing lets go where the syringe is (pending) or pauses the injection (pierced) —
      // either way, report the pushed amount right away.
      grabbingSyringe = false
      flushPending()
      return true
    }
    super.mouseReleased(event)
  }

  private def isOverSyringe(mouseX: Int, mouseY: Int): Boolean = {
    mouseX >= syringeX - InjectionScreen.BarrelWidth &&
    mouseX <= syringeX + InjectionScreen.BarrelWidth &&
    mouseY >= barrelTopY - InjectionScreen.PlungerPadHeight - InjectionScreen.PlungerRodLength &&
    mouseY <= barrelTopY + InjectionLayout.BarrelHeight + InjectionLayout.NeedleLength
  }

  /** Best-effort final report on teardown (death or world exit — vanilla closes screens in both,
    * without going through [[onClose]]). Abort semantics keep the remainder: whatever was not yet
    * pushed stays in the syringe.
    */
  override def removed(): Unit = {
    flushPending()
    super.removed()
  }

  /** Starts the fade-out instead of closing immediately; [[tick]] removes the screen once the fade
    * reaches 0. Injection and input stop at this moment (see [[advanceSession]] and
    * [[mouseClicked]]), and anything pushed so far is reported right away. Idempotent: repeated
    * close requests (Esc during the fade, the empty beat racing it) do not restart the animation.
    */
  override def onClose(): Unit = {
    if (!closing) {
      closing = true
      closedAtMs = Util.getMillis()
      grabbingSyringe = false
      flushPending()
    }
  }

  override def tick(): Unit = {
    // The empty beat requests a close (through onClose, so it fades like any other close); the
    // !closing guard keeps this from fighting the animation it just started.
    if (
      emptyCloseAtMs != 0L && !closing &&
      Util.getMillis() - emptyCloseAtMs >= InjectionScreen.EmptyBeatMs
    ) {
      onClose()
    }
    if (closing && fadeProgress <= 0f) {
      // Actually remove the screen; done on the tick rather than mid-render.
      minecraft.gui.setScreen(null)
    }
  }
}

object InjectionScreen {

  /** Opens the screen for the filled syringe held in `hand`, if any. The item's [[SyringeItem]]
    * type supplies the calibrated profile; the only caller always passes a syringe.
    */
  def openFor(player: Player, hand: InteractionHand): Unit = {
    val stack = player.getItemInHand(hand)
    if (stack.isEmpty) return
    val contents = stack.syringeContents match {
      case Some(contents) => contents
      case None           => return
    }
    if (contents.droplets <= 0L) return
    stack.getItem match {
      case syringe: SyringeItem =>
        Minecraft
          .getInstance()
          .gui
          .setScreen(new InjectionScreen(hand, contents, syringe.calibrated))
      case _ => ()
    }
  }

  // Layout (screen pixels).
  private val TitleTopPadding = 10
  private val HintSpacing = 12
  private val TitleClearanceY = 30

  /** Baked skin-band texture (epidermis-to-subcutaneous gradient), blitted stretched over the band;
    * sized near 1:1 at typical window dimensions so stretching artifacts stay negligible. The alpha
    * channel carries the edge feather (sides + bottom fade to transparent).
    */
  private val SkinTexture: Identifier =
    CasualtiesBelow.ofIdentifier("textures/gui/injection_skin.png")
  private val SkinTextureWidth = 512
  private val SkinTextureHeight = 128

  /** Feather width baked into [[SkinTexture]]'s alpha channel, in texture px (scaled to screen at
    * render time).
    */
  private val SkinEdgeFeather = 48
  private val BarrelWidth = 12
  private val BarrelPadding = 3
  private val NeedleWidth = 2
  private val PlungerRodWidth = 3
  private val PlungerPadWidth = 14
  private val PlungerPadHeight = 4
  private val PlungerTravel = InjectionLayout.BarrelHeight - 2 * BarrelPadding

  /** Rigid plunger, like a real syringe: one rod length always spans pad to seal, so a full syringe
    * shows the rod standing tall above the barrel and an empty one leaves the pad resting on the
    * barrel rim. (Not a free constant — derived so the pad lands exactly on the rim when the seal
    * reaches the bottom.)
    */
  private val PlungerRodLength = PlungerTravel + BarrelPadding
  private val PierceDepthPixels = 6
  private val SkinLineThickness = 2
  private val ScaleMarkCount = 10
  private val ScaleMarkColor = 0xff8a9298.toInt

  /** Upper bound on a single frame's advance delta, so a stall cannot slam the plunger down. */
  private val MaxDtSeconds = 0.1

  /** Beat between the syringe emptying and the screen closing itself. */
  private val EmptyBeatMs = 400L

  /** Fade durations (wall-clock): open eases in a touch slower than the close eases out. */
  private val FadeInDurationMs = 240L
  private val FadeOutDurationMs = 180L

  // Colors are 32-bit ARGB (0xAARRGGBB).
  private val BackgroundDim = 0x80101010
  private val TextColor = 0xffffffff
  private val HintColor = 0xffa8a8a8.toInt
  private val SkinLineColor = 0xff8d5f42.toInt
  private val BarrelFillColor = 0x60e8f0f4
  private val BarrelBorderColor = 0xff9aa4a8.toInt
  private val NeedleColor = 0xffc8ccd0.toInt
  private val PlungerColor = 0xff5a5f66.toInt
  private val RefinedLiquidColor = 0xffe3c46b.toInt
  private val CrudeLiquidColor = 0xff7e4f24.toInt
  private val UnknownLiquidColor = 0xff9fb4c8.toInt
}
