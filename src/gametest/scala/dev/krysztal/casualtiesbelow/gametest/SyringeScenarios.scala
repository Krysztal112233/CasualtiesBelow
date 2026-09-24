package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowDataComponents
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.item.InjectionSettlement
import dev.krysztal.casualtiesbelow.item.LiquidContents
import dev.krysztal.casualtiesbelow.item.SyringeContents

/** In-game validation of the injection settlement seam that the injection screen reports into. */
object SyringeScenarios {

  /** Two batches at maximum speed settle the full stored dose, each half settling half of it; the
    * side-effect caps accrue linearly with pushed amount; emptying consumes the syringe in
    * survival.
    */
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
    player.setItemInHand(InteractionHand.MAIN_HAND, syringe)

    val half = LiquidContents.AmpouleDroplets / 2
    val discomfortCap = Consts.Injection.FullDoseSideEffectDiscomfort

    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      half,
      1.0
    )

    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose / 2) <= 1.0e-9,
      "First half-batch did not settle half the stored dose"
    )
    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).discomfort - discomfortCap / 2) <= 1.0e-9,
      "First half-batch at full speed did not settle half the discomfort cap"
    )
    helper.assertTrue(
      !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty,
      "Half-injected syringe was consumed early"
    )

    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      half, // the held syringe now holds only its remaining half
      half,
      1.0
    )

    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose) <= 1.0e-9,
      "Second half-batch did not settle the remaining stored dose"
    )
    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).discomfort - discomfortCap) <= 1.0e-9,
      "Full-speed full-syringe injection did not settle the full discomfort cap"
    )
    helper.assertTrue(
      player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty,
      "Survival injection did not consume the emptied syringe"
    )
    helper.succeed()
  }

  /** Abort semantics: a partial push scales droplets and stored dose down proportionally, leaving a
    * resumable remainder.
    */
  def partialInjectionKeepsProportionalRemainder(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val syringe = new ItemStack(CasualtiesBelowItems.CalibratedSyringe)
    val dose = 40.0
    syringe.set(
      CasualtiesBelowDataComponents.SyringeContentsComponent,
      SyringeContents(
        LiquidContents.RefinedPoppyExtract.liquid,
        LiquidContents.AmpouleDroplets,
        dose
      )
    )
    player.setItemInHand(InteractionHand.MAIN_HAND, syringe)

    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      LiquidContents.AmpouleDroplets / 2,
      1.0
    )

    val remainder = player
      .getItemInHand(InteractionHand.MAIN_HAND)
      .get(CasualtiesBelowDataComponents.SyringeContentsComponent)
    helper.assertTrue(remainder != null, "Partially injected syringe lost its contents")
    helper.assertTrue(
      remainder.droplets == LiquidContents.AmpouleDroplets / 2,
      "Remainder droplets did not scale down by the injected amount"
    )
    helper.assertTrue(
      math.abs(remainder.opioidDose - dose / 2) <= 1.0e-9,
      "Remainder dose did not scale down proportionally with droplets"
    )
    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose / 2) <= 1.0e-9,
      "Partial batch did not settle its proportional dose"
    )
    helper.succeed()
  }

  /** Injection-site pain lands in the arm not holding the syringe: a right-handed main-hand
    * injection hurts the left arm.
    */
  def injectionPainLandsInOppositeArm(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val syringe = new ItemStack(CasualtiesBelowItems.CalibratedSyringe)
    syringe.set(
      CasualtiesBelowDataComponents.SyringeContentsComponent,
      SyringeContents(
        LiquidContents.RefinedPoppyExtract.liquid,
        LiquidContents.AmpouleDroplets,
        10.0
      )
    )
    player.setItemInHand(InteractionHand.MAIN_HAND, syringe)

    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      LiquidContents.AmpouleDroplets,
      1.0
    )

    val painCap = Consts.Injection.FullDoseSideEffectPain
    val leftPain = CasualtiesBelowComponents.Body.get(player).stats(BodyPart.ArmLeft).pain
    val rightPain = CasualtiesBelowComponents.Body.get(player).stats(BodyPart.ArmRight).pain
    helper.assertTrue(
      math.abs(leftPain - painCap) <= 1.0e-9,
      "Full-speed injection did not settle the full pain cap in the opposite arm"
    )
    helper.assertTrue(
      rightPain == 0.0,
      "Injection-site pain leaked into the syringe-holding arm"
    )
    helper.succeed()
  }

  /** Stack-swap guard: a batch whose identity (liquid + expected remaining droplets) does not match
    * the held syringe is dropped instead of settling against the wrong stack.
    */
  def batchWithStaleIdentityIsIgnored(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val syringe = new ItemStack(CasualtiesBelowItems.CalibratedSyringe)
    val dose = 40.0
    syringe.set(
      CasualtiesBelowDataComponents.SyringeContentsComponent,
      SyringeContents(
        LiquidContents.RefinedPoppyExtract.liquid,
        LiquidContents.AmpouleDroplets,
        dose
      )
    )
    player.setItemInHand(InteractionHand.MAIN_HAND, syringe)

    val half = LiquidContents.AmpouleDroplets / 2
    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      half,
      1.0
    )
    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose / 2) <= 1.0e-9,
      "Valid first batch did not settle its dose"
    )

    // Stale droplet count: the batch claims the syringe is still full (as it would after the
    // player swapped to a fresh, identical syringe mid-session).
    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      half,
      1.0
    )
    // Wrong liquid: the batch names a different contents than the held syringe carries.
    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.CrudePoppyLiquid.liquid,
      half,
      half,
      1.0
    )

    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose / 2) <= 1.0e-9,
      "Identity-mismatched batches still settled dose"
    )
    helper.assertTrue(
      math.abs(
        CasualtiesBelowComponents.vitals(player).discomfort -
          Consts.Injection.FullDoseSideEffectDiscomfort / 2
      ) <= 1.0e-9,
      "Identity-mismatched batches still settled side effects"
    )
    val held = player
      .getItemInHand(InteractionHand.MAIN_HAND)
      .get(CasualtiesBelowDataComponents.SyringeContentsComponent)
    helper.assertTrue(
      held != null && held.droplets == half,
      "Identity-mismatched batches drained the held syringe"
    )
    helper.assertTrue(
      math.abs(held.opioidDose - dose / 2) <= 1.0e-9,
      "Identity-mismatched batches altered the held syringe's stored dose"
    )
    helper.succeed()
  }

  /** Creative injection never consumes the syringe: the dose settles, but the emptied stack stays
    * in hand with its contents untouched.
    */
  def creativeInjectionNeverConsumes(helper: GameTestHelper): Unit = {
    val player = GameTestPlayers.createCreativePlayer(helper)
    val syringe = new ItemStack(CasualtiesBelowItems.CalibratedSyringe)
    val dose = 40.0
    syringe.set(
      CasualtiesBelowDataComponents.SyringeContentsComponent,
      SyringeContents(
        LiquidContents.RefinedPoppyExtract.liquid,
        LiquidContents.AmpouleDroplets,
        dose
      )
    )
    player.setItemInHand(InteractionHand.MAIN_HAND, syringe)

    InjectionSettlement.applyBatch(
      player,
      InteractionHand.MAIN_HAND,
      LiquidContents.RefinedPoppyExtract.liquid,
      LiquidContents.AmpouleDroplets,
      LiquidContents.AmpouleDroplets,
      1.0
    )

    helper.assertTrue(
      math.abs(CasualtiesBelowComponents.vitals(player).opioidLevel - dose) <= 1.0e-9,
      "Creative injection did not settle the full stored dose"
    )
    val held = player.getItemInHand(InteractionHand.MAIN_HAND)
    helper.assertTrue(!held.isEmpty, "Creative injection consumed the syringe")
    val contents = held.get(CasualtiesBelowDataComponents.SyringeContentsComponent)
    helper.assertTrue(
      contents != null && contents.droplets == LiquidContents.AmpouleDroplets,
      "Creative injection drained the syringe's contents"
    )
    helper.succeed()
  }
}
