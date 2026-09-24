package dev.krysztal.casualtiesbelow.ui

import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ComponentExtensions.*
import dev.krysztal.casualtiesbelow.internal.sync.GameplayDataSnapshot
import dev.krysztal.casualtiesbelow.physiology.pain.PainCalc

/** Medical status panel docked to the left screen edge, in the spirit of Scav Prototype's health
  * panel: an always-visible vitals section (consciousness, blood oxygen, immune health, body
  * temperature) on top and a limb section below it that only appears while a body part is selected.
  * Muscle/skin/vitals stats are drawn as label + bar; pain, body temperature and the condition rows
  * (fracture, dislocation, infection, bleeding) are plain label + value rows. Values in a bad
  * condition are drawn red.
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

  /** Whole-body pain row: once opioid analgesia masks at least this fraction of the displayed pain,
    * the felt value is appended in yellow parentheses.
    */
  private val FeltPainDeltaFraction = 0.10
  private val ConsciousnessBadThreshold = 50.0
  private val BloodBadThreshold = 70.0

  private val BodyTempLowWarning = 35.0
  private val BodyTempHighWarning = 39.5

  // Wetness row appears only while actually wet, like the limb condition rows.
  private val WetnessEpsilon = 0.01

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
      "screen.casualtiesbelow.body_status.section.vitals".translatable(),
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
      "screen.casualtiesbelow.body_status.stat.consciousness".translatable(),
      vitals.consciousness.level,
      ConsciousnessBadThreshold
    )
    y = extractStatBar(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.blood_oxygen".translatable(),
      vitals.circulation.bloodOxygen,
      gameplayData.bloodOxygenHypoxiaThreshold
    )

    // Immune health displays its actual value while the bar fill uses the configured maximum; red
    // below the infection break-even point (see CasualtiesBelowConfig.immuneBreakEven), where the
    // immune system can no longer outpace infections.
    val maxImmune = CasualtiesBelowConfig.vitals.maxImmuneHealth.get()

    y = extractStatBar(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.immune_health".translatable(),
      vitals.infection.immuneHealth,
      CasualtiesBelowConfig.immuneBreakEven,
      maxImmune
    )
    // Blood volume as a fraction of the effective maximum: sepsis compresses the cap
    // (see CasualtiesBelowConfig.effectiveMaxBloodVolume), so the bar shows the remaining
    // room, not the configured base maximum.
    val effectiveMaxBlood = CasualtiesBelowConfig.effectiveMaxBloodVolume(vitals.infection.sepsis)

    y = extractStatBar(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.blood".translatable(),
      if (effectiveMaxBlood > 0.0) vitals.circulation.bloodVolume / effectiveMaxBlood * 100.0
      else 0.0,
      BloodBadThreshold
    )
    // Sepsis is a "higher is worse" meter, so it is a plain row like pain rather than a
    // depletion bar; any sepsis at all is bad news.
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.sepsis".translatable(),
      Component.literal(
        (vitals.infection.sepsis / CasualtiesBelowConfig.sepsis.maxSepsis
          .get() * 100.0).toInt.toString
      ),
      vitals.infection.sepsis > 0.0
    )
    // Discomfort is another "higher is worse" meter (see Discomfort); red once nausea
    // territory is reached.
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.discomfort".translatable(),
      Component.literal(
        (vitals.discomfort / gameplayData.maxDiscomfort * 100.0).toInt.toString
      ),
      vitals.discomfort >= gameplayData.nauseaThreshold
    )

    // Body temperature is a deviation meter, not a depletion bar: a plain row with the °C value,
    // red outside the safe band.
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.body_temperature".translatable(),
      f"${vitals.bodyTemperature}%.1f °C".literal,
      vitals.bodyTemperature < BodyTempLowWarning || vitals.bodyTemperature > BodyTempHighWarning
    )
    // Wetness only has consequences while present (armor collapse, evaporative cooling), so the
    // row appears only when wet.
    if (vitals.wetness > WetnessEpsilon) {
      y = extractStatRow(
        graphics,
        font,
        contentX,
        y,
        "screen.casualtiesbelow.body_status.stat.wetness".translatable(),
        Component.literal((vitals.wetness * 100.0).toInt.toString),
        false
      )
    }

    // Whole-body pain is derived from limb pain on the spot (see PainCalc); "higher is worse",
    // so it is a plain row (red above the threshold) rather than a depletion bar. Opioid
    // analgesia masks pain only at consumption (see PainCalc.feltTotal), so while the felt value
    // drops meaningfully below the displayed one we append it in yellow parentheses.
    val totalPain = PainCalc.total(body)
    val feltPain = PainCalc.feltTotal(body, vitals)
    val painValue = Component.literal(totalPain.toInt.toString)
    if (totalPain > 0.0 && totalPain - feltPain >= totalPain * FeltPainDeltaFraction) {
      painValue.append(
        Component.literal(s" (${feltPain.toInt.toString})").withStyle(ChatFormatting.YELLOW)
      )
    }
    y = extractStatRow(
      graphics,
      font,
      contentX,
      y,
      "screen.casualtiesbelow.body_status.stat.pain".translatable(),
      painValue,
      totalPain > PainBadThreshold
    )

    hoveredPart.foreach { part =>
      val stats = body.stats(part)
      y += SectionGap / 2
      val partName = s"bodypart.casualtiesbelow.${part.id}".translatable()
      graphics.text(font, partName, contentX, y, HeaderColor, true)
      y += SectionGap
      y = extractStatBar(
        graphics,
        font,
        contentX,
        y,
        "screen.casualtiesbelow.body_status.stat.muscle_health".translatable(),
        stats.muscleHealth,
        MuscleBadThreshold
      )
      y = extractStatBar(
        graphics,
        font,
        contentX,
        y,
        "screen.casualtiesbelow.body_status.stat.skin_integrity".translatable(),
        stats.skinIntegrity,
        SkinBadThreshold
      )
      y = extractStatRow(
        graphics,
        font,
        contentX,
        y,
        "screen.casualtiesbelow.body_status.stat.pain".translatable(),
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
      badThreshold: Double,
      maxValue: Double = LimbSnapshot.MaxValue
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
    val fraction = Mth.clamp(value / maxValue, 0.0, 1.0)
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
  private def conditionRows(stats: LimbSnapshot): List[(Component, Component)] = {
    def stat(id: String): Component =
      s"screen.casualtiesbelow.body_status.stat.$id".translatable()

    val rows = List(
      Option.when(stats.fractureRecoveryTicks.isPresent) {
        val ticks = stats.fractureRecoveryTicks.getAsInt
        stat("fracture") -> "screen.casualtiesbelow.body_status.value.minutes".translatable(
          f"${ticks / 20.0 / 60.0}%.1f"
        )
      },
      Option.when(stats.dislocated) {
        stat("dislocated") -> "gui.yes".translatable()
      },
      Option.when(stats.infectionProgress.isPresent) {
        val progress = stats.infectionProgress.getAsDouble
        stat("infection") -> f"$progress%.0f%%".literal
      },
      Option.when(stats.externalBleedingRate > 0.0) {
        val bleedingPerSecond = stats.externalBleedingRate * SharedConstants.TICKS_PER_SECOND
        stat("bleeding") -> f"$bleedingPerSecond%.2f mL/s".literal
      }
    )
    rows.flatten
  }
}
