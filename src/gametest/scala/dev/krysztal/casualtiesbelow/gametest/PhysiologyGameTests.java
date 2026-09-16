package dev.krysztal.casualtiesbelow.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Java discovery bridge for the Scala opioid GameTest scenarios. */
public final class PhysiologyGameTests {
    @GameTest(maxTicks = 400)
    public void analgesiaShockSuppression(GameTestHelper helper) {
        OpioidPhaseOneScenarios.analgesiaShockSuppression(helper);
    }

    @GameTest(maxTicks = 1000)
    public void overdoseDeath(GameTestHelper helper) {
        OpioidPhaseOneScenarios.overdoseDeath(helper);
    }

    @GameTest(maxTicks = 600)
    public void withdrawalTrio(GameTestHelper helper) {
        OpioidPhaseOneScenarios.withdrawalTrio(helper);
    }

    @GameTest(maxTicks = 20)
    public void syringeInjectsStoredOpioidDose(GameTestHelper helper) {
        SyringeScenarios.injectsStoredOpioidDose(helper);
    }

    @GameTest(maxTicks = 20)
    public void syringePartialInjectionKeepsRemainder(GameTestHelper helper) {
        SyringeScenarios.partialInjectionKeepsProportionalRemainder(helper);
    }

    @GameTest(maxTicks = 20)
    public void syringeInjectionPainLandsInOppositeArm(GameTestHelper helper) {
        SyringeScenarios.injectionPainLandsInOppositeArm(helper);
    }

    @GameTest(maxTicks = 20)
    public void syringeBatchWithStaleIdentityIsIgnored(GameTestHelper helper) {
        SyringeScenarios.batchWithStaleIdentityIsIgnored(helper);
    }

    @GameTest(maxTicks = 20)
    public void syringeCreativeInjectionNeverConsumes(GameTestHelper helper) {
        SyringeScenarios.creativeInjectionNeverConsumes(helper);
    }

    @GameTest(maxTicks = 20)
    public void infusionCauldronExposesUnfilteredFluidStorage(GameTestHelper helper) {
        PoppyFluidScenarios.infusionCauldronExposesUnfilteredStorage(helper);
    }

    @GameTest(maxTicks = 20)
    public void bucketConvertsWithInfusionCauldron(GameTestHelper helper) {
        PoppyFluidScenarios.bucketConvertsWithInfusionCauldron(helper);
    }

    @GameTest(maxTicks = 20)
    public void flowingFluidInvokerMapsLegacyLevels(GameTestHelper helper) {
        PoppyFluidScenarios.flowingFluidInvokerMapsLegacyLevels(helper);
    }

    @GameTest(maxTicks = 20)
    public void waterFluidInvokerDelegatesDropLogic(GameTestHelper helper) {
        PoppyFluidScenarios.waterFluidInvokerDelegatesDropLogic(helper);
    }
}
