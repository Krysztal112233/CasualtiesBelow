package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.gametest.framework.GameTestHelper

import net.fabricmc.fabric.api.gametest.v1.GameTest

/** GameTest discovery entrypoint for the opioid, syringe and poppy-fluid scenarios. */
final class PhysiologyGameTests {

  @GameTest(maxTicks = 20)
  def opioidPainReliefSettingScalesDrain(helper: GameTestHelper): Unit = {
    OpioidPhaseOneScenarios.opioidPainReliefSettingScalesDrain(helper)
  }

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
  def skinRegenerationEffectRestoresSkinAndCapsBleeding(helper: GameTestHelper): Unit = {
    SkinRegenerationScenarios.effectRestoresSkinAndCapsBleeding(helper)
  }

  @GameTest(maxTicks = 20)
  def skinRegenerationEffectDistributesAcrossDamagedLimbs(helper: GameTestHelper): Unit = {
    SkinRegenerationScenarios.effectDistributesAcrossDamagedLimbs(helper)
  }

  @GameTest(maxTicks = 20)
  def muscleRecoveryEffectRestoresMuscle(helper: GameTestHelper): Unit = {
    MuscleRecoveryScenarios.effectRestoresMuscle(helper)
  }

  @GameTest(maxTicks = 20)
  def muscleRecoveryEffectDistributesAcrossDamagedLimbs(helper: GameTestHelper): Unit = {
    MuscleRecoveryScenarios.effectDistributesAcrossDamagedLimbs(helper)
  }

  @GameTest(maxTicks = 20)
  def fractureMirrorFollowsLimbState(helper: GameTestHelper): Unit = {
    FractureDislocationScenarios.fractureMirrorFollowsLimbState(helper)
  }

  @GameTest(maxTicks = 20)
  def dislocationMirrorFollowsLimbState(helper: GameTestHelper): Unit = {
    FractureDislocationScenarios.dislocationMirrorFollowsLimbState(helper)
  }

  @GameTest(maxTicks = 20)
  def totemGrantsRecoveryBurst(helper: GameTestHelper): Unit = {
    TotemRecoveryScenarios.totemGrantsRecoveryBurst(helper)
  }

  @GameTest(maxTicks = 20)
  def boneHealingEffectAdvancesAndCompletesFractureRecovery(helper: GameTestHelper): Unit = {
    BoneHealingScenarios.effectAdvancesAndCompletesFractureRecovery(helper)
  }

  @GameTest(maxTicks = 20)
  def boneHealingEffectDistributesAcrossFracturedLimbs(helper: GameTestHelper): Unit = {
    BoneHealingScenarios.effectDistributesAcrossFracturedLimbs(helper)
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
