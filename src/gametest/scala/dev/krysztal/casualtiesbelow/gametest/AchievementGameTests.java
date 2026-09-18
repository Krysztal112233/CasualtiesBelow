package dev.krysztal.casualtiesbelow.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Java discovery bridge for the Scala achievement GameTest scenarios. */
public final class AchievementGameTests {
    @GameTest(maxTicks = 20)
    public void bandageObtainedGrantsAdvancement(GameTestHelper helper) {
        AchievementScenarios.bandageObtainedGrantsAdvancement(helper);
    }

    @GameTest(maxTicks = 20)
    public void refinedExtractObtainedGrantsAdvancement(GameTestHelper helper) {
        AchievementScenarios.refinedExtractObtainedGrantsAdvancement(helper);
    }

    @GameTest(maxTicks = 20)
    public void opioidOverdoseDeathGrantsAdvancement(GameTestHelper helper) {
        AchievementScenarios.opioidOverdoseDeathGrantsAdvancement(helper);
    }

    @GameTest(maxTicks = 20)
    public void hypoxiaDeathWithoutOpioidDoesNotGrant(GameTestHelper helper) {
        AchievementScenarios.hypoxiaDeathWithoutOpioidDoesNotGrant(helper);
    }

    @GameTest(maxTicks = 20)
    public void consciousnessMinimumGrantsAdvancement(GameTestHelper helper) {
        AchievementScenarios.consciousnessMinimumGrantsAdvancement(helper);
    }

    @GameTest(maxTicks = 20)
    public void consciousPlayerDoesNotGrantMinimum(GameTestHelper helper) {
        AchievementScenarios.consciousPlayerDoesNotGrantMinimum(helper);
    }

    @GameTest(maxTicks = 20)
    public void maxDiscomfortFoodGrantsAdvancement(GameTestHelper helper) {
        AchievementScenarios.maxDiscomfortFoodGrantsAdvancement(helper);
    }

    @GameTest(maxTicks = 20)
    public void ordinaryFoodDoesNotGrantMaxDiscomfort(GameTestHelper helper) {
        AchievementScenarios.ordinaryFoodDoesNotGrantMaxDiscomfort(helper);
    }

    @GameTest(maxTicks = 20)
    public void firstBleedingGrantsRoot(GameTestHelper helper) {
        AchievementScenarios.firstBleedingGrantsRoot(helper);
    }

    @GameTest(maxTicks = 20)
    public void hemostasisAfterNearMaxBleedingGrantsAdvancement(GameTestHelper helper) {
        AchievementScenarios.hemostasisAfterNearMaxBleedingGrantsAdvancement(helper);
    }

    @GameTest(maxTicks = 20)
    public void bleedingBelowThresholdDoesNotGrantHemostasis(GameTestHelper helper) {
        AchievementScenarios.bleedingBelowThresholdDoesNotGrantHemostasis(helper);
    }
}
