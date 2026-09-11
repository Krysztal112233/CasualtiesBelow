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
}
