package dev.krysztal.casualtiesbelow.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Java discovery bridge for the Scala hygiene GameTest scenarios. */
public final class HygieneGameTests {
    @GameTest(maxTicks = 20)
    public void dirtinessPassiveAccrualAccumulates(GameTestHelper helper) {
        HygieneScenarios.passiveAccrualAccumulates(helper);
    }

    @GameTest(maxTicks = 20)
    public void cauldronSoakWashesAndConsumesLevels(GameTestHelper helper) {
        HygieneScenarios.cauldronSoakWashesAndConsumesLevels(helper);
    }

    @GameTest(maxTicks = 20)
    public void waterWashRemovesDirtiness(GameTestHelper helper) {
        HygieneScenarios.waterWashRemovesDirtiness(helper);
    }

    @GameTest(maxTicks = 20)
    public void dirtyInjectionSeedsInfection(GameTestHelper helper) {
        HygieneScenarios.dirtyInjectionSeedsInfection(helper);
    }
}
