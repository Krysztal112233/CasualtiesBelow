package dev.krysztal.casualtiesbelow.ui

/** Screen-space layout of the injection minigame, derived from the current GUI size. Pure geometry:
  * syringe drag and pierce state live on the screen, layout only positions things.
  */
private[ui] final case class InjectionLayout(width: Int, height: Int) {

  def regionLeft: Int = ((width * (1.0 - InjectionLayout.RegionWidthFraction)) / 2).toInt
  def regionTop: Int = ((height * (1.0 - InjectionLayout.RegionHeightFraction)) / 2).toInt
  def regionRight: Int = width - regionLeft
  def regionBottom: Int = height - regionTop
  def regionCenterX: Int = (regionLeft + regionRight) / 2

  /** The skin is the bottom third of the viewport. */
  def skinTopY: Int = regionBottom - (regionBottom - regionTop) / 3

  /** Barrel top when the syringe rests at its anchor above the skin. */
  def homeTopY(skinTop: Int): Int =
    skinTop - InjectionLayout.NeedleLength -
      InjectionLayout.BarrelHeight - InjectionLayout.HoverGapPixels
}

private[ui] object InjectionLayout {

  /** Fraction of the window occupied by the scene viewport, centered on both axes. */
  private[ui] val RegionWidthFraction = 0.6
  private[ui] val RegionHeightFraction = 0.8

  private[ui] val HoverGapPixels = 48
  private[ui] val BarrelHeight = 52
  private[ui] val NeedleLength = 16
}
