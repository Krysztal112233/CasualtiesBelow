package dev.krysztal.casualtiesbelow.ui

import net.minecraft.util.Mth

/** 32-bit ARGB (0xAARRGGBB) color helpers shared by the UI widgets. */
object Argb {

  /** Per-channel ARGB lerp; `progress` is clamped to [0, 1]. */
  def lerp(from: Int, to: Int, progress: Double): Int = {
    val p = Mth.clamp(progress, 0.0, 1.0)
    def channel(shift: Int): Int = {
      val a = (from >> shift) & 0xff
      val b = (to >> shift) & 0xff
      (a + ((b - a) * p).toInt) & 0xff
    }
    (channel(24) << 24) | (channel(16) << 16) | (channel(8) << 8) | channel(0)
  }
}
