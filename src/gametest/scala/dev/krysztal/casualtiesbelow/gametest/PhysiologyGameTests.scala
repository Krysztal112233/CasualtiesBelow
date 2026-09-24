package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the opioid, syringe and poppy-fluid scenarios. */
final class PhysiologyGameTests {

  @GameTest(maxTicks = 400)
  def opioidAcceleratesPainDecay(helper: GameTestHelper): Unit = {
    OpioidPhaseOneScenarios.opioidAcceleratesPainDecay(helper)
  }

  @GameTest(maxTicks = 1000)
  def overdoseDeath(helper: GameTestHelper): Unit = {
    OpioidPhaseOneScenarios.overdoseDeath(helper)
  }

  @GameTest(maxTicks = 600)
  def withdrawalTrio(helper: GameTestHelper): Unit = {
    OpioidPhaseOneScenarios.withdrawalTrio(helper)
  }

  @GameTest(maxTicks = 20)
  def syringeInjectsStoredOpioidDose(helper: GameTestHelper): Unit = {
    SyringeScenarios.injectsStoredOpioidDose(helper)
  }

  @GameTest(maxTicks = 20)
  def syringePartialInjectionKeepsRemainder(helper: GameTestHelper): Unit = {
    SyringeScenarios.partialInjectionKeepsProportionalRemainder(helper)
  }

  @GameTest(maxTicks = 20)
  def syringeInjectionPainLandsInOppositeArm(helper: GameTestHelper): Unit = {
    SyringeScenarios.injectionPainLandsInOppositeArm(helper)
  }

  @GameTest(maxTicks = 20)
  def syringeBatchWithStaleIdentityIsIgnored(helper: GameTestHelper): Unit = {
    SyringeScenarios.batchWithStaleIdentityIsIgnored(helper)
  }

  @GameTest(maxTicks = 20)
  def syringeCreativeInjectionNeverConsumes(helper: GameTestHelper): Unit = {
    SyringeScenarios.creativeInjectionNeverConsumes(helper)
  }

  @GameTest(maxTicks = 20)
  def infusionCauldronExposesUnfilteredFluidStorage(helper: GameTestHelper): Unit = {
    PoppyFluidScenarios.infusionCauldronExposesUnfilteredStorage(helper)
  }

  @GameTest(maxTicks = 20)
  def bucketConvertsWithInfusionCauldron(helper: GameTestHelper): Unit = {
    PoppyFluidScenarios.bucketConvertsWithInfusionCauldron(helper)
  }

  @GameTest(maxTicks = 20)
  def flowingFluidInvokerMapsLegacyLevels(helper: GameTestHelper): Unit = {
    PoppyFluidScenarios.flowingFluidInvokerMapsLegacyLevels(helper)
  }

  @GameTest(maxTicks = 20)
  def waterFluidInvokerDelegatesDropLogic(helper: GameTestHelper): Unit = {
    PoppyFluidScenarios.waterFluidInvokerDelegatesDropLogic(helper)
  }
}
