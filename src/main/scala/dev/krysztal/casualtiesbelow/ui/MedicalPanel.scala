package dev.krysztal.casualtiesbelow.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.pain.PainCalc
import dev.krysztal.casualtiesbelow.sync.GameplayDataSnapshot

/** Medical status panel docked to the left screen edge, in the spirit of Scav Prototype's health
  * panel: an always-visible vitals section (consciousness, immune health) on top and a limb section
  * below it that only appears while a body part is selected. Muscle/skin/vitals stats are drawn as
  * label + bar; pain and the condition rows (fracture, dislocation, infection, bleeding) are plain
  * label + value rows. Values in a bad condition are drawn red.
  *
  * Stateless and independent of any screen — reusable from screens or HUD overlays. All data is
  * read from the given player's synced components. [[MedicalPanel.Width]] tells callers how much
  * horizontal space the panel occupies when docked.
  */
object MedicalPanel {

  /** Panel width in pixels (excluding the 1px right border). */
  val Width = 132

  private val Padding = 10
  private val ContentWidth = Width - 2 * Padding
  private val SectionGap = 16
  private val StatRowHeight = 11
  private val StatRowGap = 6
  private val BarHeight = 5

  /** 1px outline on the panel's right edge. */
  private val BorderWidth = 1

  // Colors are 32-bit ARGB (0xAARRGGBB).
  private val BorderColor = 0xff3a3a3a // opaque dark gray
  private val FillColor = 0xc0181818 // 75%-opaque near-black
  private val HeaderColor = 0xffffffff // opaque white
  private val LabelColor = 0xffa0a0a0 // opaque light gray
  private val ValueColor = 0xffffffff // opaque white
  private val ValueBadColor = 0xffff5555 // opaque red (vanilla-style bad condition)
  private val BarBackgroundColor = 0xff3a3a3a // opaque dark gray
  private val BarHealthyColor = 0xff8a8a8a // opaque mid gray
  private val BarDamagedColor = 0xffb03a30 // opaque deep red

  /** Bad-condition thresholds (values at/past these are drawn red; vitals/muscle/skin are
    * lower-is-bad, pain is higher-is-bad).
    */
  private val MuscleBadThreshold = 10.0
  private val SkinBadThreshold = 10.0
  private val PainBadThreshold = 50.0
  private val ConsciousnessBadThreshold = 50.0
  private val BloodBadThreshold = 70.0

  /** Renders the panel spanning the full screen height at the left edge.
    *
    * @param hoveredPart
    *   the body part whose data fills the limb section; the limb section is omitted entirely when
    *   empty
    * @param slideProgress
    *   0 hides the panel fully off the left edge, 1 docks it; intermediate values slide it
    *   horizontally (pass an eased open/close progress)
    */
  def extract(
      graphics: GuiGraphicsExtractor,
      font: Font,
      player: Player,
      hoveredPart: Option[BodyPart],
      slideProgress: Float,
      screenHeight: Int
  ): Unit = {
    val x = Mth.lerpInt(slideProgress, -Width - BorderWidth, 0)
    graphics.fill(x, 0, x + Width + BorderWidth, screenHeight, BorderColor)
    graphics.fill(x, 0, x + Width, screenHeight, FillColor)

    val contentX = x + Padding
    var y = Padding + 4

    val vitals = CasualtiesBelowComponents.Vitals.get(player)
    val body = CasualtiesBelowComponents.Body.get(player)
    val gameplayData = GameplayDataSnapshot.current
    graphics.text(
      font,
      Component.translatable("screen.casualtiesbelow.body_status.section.vitals"),
      contentX,
      y,
      HeaderColor,
      true
    )
    y += SectionGap
    y = extractStatBar(
      graphics,
      font,
      contentX,
      y,
      Component.translatable("screen.casualtiesbelow.body_status.stat.consciousness"),
      vitals.consciousness,
      ConsciousnessBadThreshold
    )

    // Immune health as a percentage of the configured maximum; red below the infection
    // break-even point (see CasualtiesBelowConfig.immuneBreakEven), where the immune system
    // can no longer outpace infections.
    val maxImmune = CasualtiesBelowConfig.MaxImmuneHealth.get()

    y = extractStatBar(
      graphics,
      font,
      contentX,
      y,
      Component.translatable("screen.casualtiesbelow.body_status.stat.immune_health"),
      vitals.immuneHealth / maxImmune * 100.0,
      CasualtiesBelowConfig.immuneBreakEven / maxImmune * 100.0
    )
    // Blood volume as a fraction of the effective maximum: sepsis compresses the cap
    // (see CasualtiesBelowConfig.effectiveMaxBloodVolume), so the bar shows the remaining
    // room, not the configured base maximum.
    val effectiveMaxBlood = CasualtiesBelowConfig.effectiveMaxBloodVolume(vitals.sepsis)

    y = extractStatBar(
      graphics,
      font,
      contentX,
      y,
      Component.translatable("screen.casualtiesbelow.body_status.stat.blood"),
      if (effectiveMaxBlood > 0.0) vitals.bloodVolume / effectiveMaxBlood * 100.0 else 0.0,
      BloodBadThreshold
    )
    // Sepsis is a "higher is worse" meter, so it is a plain row like pain rather than a
    // depletion bar; any sepsis at all is bad news.
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      Component.translatable("screen.casualtiesbelow.body_status.stat.sepsis"),
      Component.literal(
        (vitals.sepsis / CasualtiesBelowConfig.MaxSepsis.get() * 100.0).toInt.toString
      ),
      vitals.sepsis > 0.0
    )
    // Discomfort is another "higher is worse" meter (see Discomfort); red once nausea
    // territory is reached.
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      Component.translatable("screen.casualtiesbelow.body_status.stat.discomfort"),
      Component.literal(
        (vitals.discomfort / gameplayData.maxDiscomfort * 100.0).toInt.toString
      ),
      vitals.discomfort >= gameplayData.nauseaThreshold
    )

    // Whole-body pain is derived from limb pain on the spot (see PainCalc); "higher is worse",
    // so it is a plain row (red above the threshold) rather than a depletion bar.
    val totalPain = PainCalc.total(body)
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      Component.translatable("screen.casualtiesbelow.body_status.stat.pain"),
      Component.literal(totalPain.toInt.toString),
      totalPain > PainBadThreshold
    )

    hoveredPart.foreach { part =>
      val stats = body.stats(part)
      y += SectionGap / 2
      val partName = Component.translatable(s"bodypart.casualtiesbelow.${part.id}")
      graphics.text(font, partName, contentX, y, HeaderColor, true)
      y += SectionGap
      y = extractStatBar(
        graphics,
        font,
        contentX,
        y,
        Component.translatable("screen.casualtiesbelow.body_status.stat.muscle_health"),
        stats.muscleHealth,
        MuscleBadThreshold
      )
      y = extractStatBar(
        graphics,
        font,
        contentX,
        y,
        Component.translatable("screen.casualtiesbelow.body_status.stat.skin_integrity"),
        stats.skinIntegrity,
        SkinBadThreshold
      )
      y = extractStatRow(
        graphics,
        font,
        contentX,
        y,
        Component.translatable("screen.casualtiesbelow.body_status.stat.pain"),
        Component.literal(stats.pain.toInt.toString),
        stats.pain > PainBadThreshold
      )
      conditionRows(stats).foreach { case (label, value) =>
        y = extractStatRow(graphics, font, contentX, y, label, value, true)
      }
    }
  }

  /** Draws one labeled bar stat (label left, floored value right, bar below) at (`x`, `y`) and
    * returns the y for the next row. The bar fill lerps healthy gray → red as the stat drops; the
    * value is red when below `badThreshold`.
    */
  private def extractStatBar(
      graphics: GuiGraphicsExtractor,
      font: Font,
      x: Int,
      y: Int,
      label: Component,
      value: Double,
      badThreshold: Double
  ): Int = {
    val nextY = extractStatRow(
      graphics,
      font,
      x,
      y,
      label,
      Component.literal(value.toInt.toString),
      value < badThreshold
    )
    graphics.fill(x, nextY, x + ContentWidth, nextY + BarHeight, BarBackgroundColor)
    val fraction = Mth.clamp(value / LimbStats.MaxValue, 0.0, 1.0)
    val fillWidth = Math.round(ContentWidth * fraction).toInt
    if (fillWidth > 0) {
      graphics.fill(
        x,
        nextY,
        x + fillWidth,
        nextY + BarHeight,
        Argb.lerp(BarHealthyColor, BarDamagedColor, 1.0 - fraction)
      )
    }
    nextY + BarHeight + StatRowGap
  }

  /** Draws one label + value row at (`x`, `y`) and returns the y for the next row; the value is
    * right-aligned and drawn red when `bad`.
    */
  private def extractStatRow(
      graphics: GuiGraphicsExtractor,
      font: Font,
      x: Int,
      y: Int,
      label: Component,
      value: Component,
      bad: Boolean
  ): Int = {
    graphics.text(font, label, x, y, LabelColor, false)
    graphics.text(
      font,
      value,
      x + ContentWidth - font.width(value),
      y,
      if (bad) ValueBadColor else ValueColor,
      false
    )
    y + StatRowHeight
  }

  /** Condition rows for one limb, present only while the condition is active (like the reference
    * health panel, which hides inactive statuses). All are bad conditions, so callers draw the
    * values red.
    */
  private def conditionRows(stats: LimbStats): List[(Component, Component)] = {
    def stat(id: String): Component =
      Component.translatable(s"screen.casualtiesbelow.body_status.stat.$id")

    val rows = List.newBuilder[(Component, Component)]
    stats.fractureRecoveryTicks.foreach { ticks =>
      rows += ((
        stat("fracture"),
        Component.translatable(
          "screen.casualtiesbelow.body_status.value.minutes",
          f"${ticks / 20.0 / 60.0}%.1f"
        )
      ))
    }
    if (stats.dislocated) {
      rows += ((stat("dislocated"), Component.translatable("gui.yes")))
    }
    stats.infectionProgress.foreach { progress =>
      rows += ((stat("infection"), Component.literal(f"$progress%.0f%%")))
    }
    if (stats.externalBleedingRate > 0.0) {
      rows += ((stat("bleeding"), Component.literal(f"${stats.externalBleedingRate}%.2f mL/t")))
    }
    rows.result()
  }
}
