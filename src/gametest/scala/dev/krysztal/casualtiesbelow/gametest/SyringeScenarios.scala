package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.item.LiquidContents
import dev.krysztal.casualtiesbelow.item.SyringeContents

/** In-game validation of the syringe-to-vitals integration seam. */
object SyringeScenarios {

  def injectsStoredOpioidDose(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val syringe = new ItemStack(CasualtiesBelowItems.CalibratedSyringe)
    val dose = 37.5
    syringe.set(
      CasualtiesBelowDataComponents.SyringeContentsComponent,
      SyringeContents(
        LiquidContents.RefinedPoppyExtract.liquid,
        LiquidContents.AmpouleDroplets,
        dose
      )
    )

    CasualtiesBelowItems.CalibratedSyringe.finishUsingItem(syringe, player.level(), player)

    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose) <= 1.0e-9,
      "Injected opioid level did not rise by the stored syringe dose"
    )
    helper.assertTrue(
      math.abs(
        CasualtiesBelowComponents.vitals(player).discomfort -
          CasualtiesBelowConfig.OpioidRefinedSyringeDiscomfort.get()
      ) <= 1.0e-9,
      "Refined injection did not apply its configured discomfort pulse"
    )
    helper.assertTrue(syringe.isEmpty, "Survival injection did not consume the syringe")
    helper.succeed()
  }
}
