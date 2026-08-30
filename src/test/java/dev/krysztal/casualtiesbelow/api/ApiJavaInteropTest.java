package dev.krysztal.casualtiesbelow.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

import dev.krysztal.casualtiesbelow.api.body.BodyComponent;
import dev.krysztal.casualtiesbelow.api.body.BodyPart;
import dev.krysztal.casualtiesbelow.api.body.LimbSnapshot;
import dev.krysztal.casualtiesbelow.api.body.PainShockStage;
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent;
import dev.krysztal.casualtiesbelow.api.event.AdrenalineChangedCallback;
import dev.krysztal.casualtiesbelow.api.event.AdrenalineChangedContext;
import dev.krysztal.casualtiesbelow.api.event.ConsciousnessStateChangeCallback;
import dev.krysztal.casualtiesbelow.api.event.ConsciousnessStateChangeContext;
import dev.krysztal.casualtiesbelow.api.event.GameplayDataReloadedCallback;
import dev.krysztal.casualtiesbelow.api.event.GameplayDataReloadedContext;
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryAllowCallback;
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryAppliedCallback;
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryAppliedContext;
import dev.krysztal.casualtiesbelow.api.event.LimbInjuryContext;
import dev.krysztal.casualtiesbelow.api.event.PainShockStageChangedCallback;
import dev.krysztal.casualtiesbelow.api.event.PainShockStageChangedContext;
import dev.krysztal.casualtiesbelow.api.event.TraumaStartedCallback;
import dev.krysztal.casualtiesbelow.api.event.TraumaStartedContext;

import org.junit.jupiter.api.Test;
import org.ladysnake.cca.api.v3.component.CopyableComponent;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

final class ApiJavaInteropTest {
    @Test
    void exposesJavaFriendlyReadOnlyViews() {
        assertEquals("head", BodyPart.fromId("head").orElseThrow().id());
        assertEquals("deferred", PainShockStage.fromId("deferred").orElseThrow().id());

        var snapshot =
                new LimbSnapshot(
                        95.0,
                        90.0,
                        OptionalInt.empty(),
                        OptionalDouble.of(12.5),
                        false,
                        0.25,
                        20.0);
        assertFalse(snapshot.fractured());
        assertTrue(snapshot.infected());
        assertEquals(12.5, snapshot.infectionProgress().orElseThrow(), 1.0e-9);
    }

    @Test
    void keepsCcaLifecycleCapabilitiesOffPublicComponentContracts() {
        assertFalse(AutoSyncedComponent.class.isAssignableFrom(BodyComponent.class));
        assertFalse(CopyableComponent.class.isAssignableFrom(BodyComponent.class));
        assertFalse(AutoSyncedComponent.class.isAssignableFrom(VitalsComponent.class));
        assertFalse(CopyableComponent.class.isAssignableFrom(VitalsComponent.class));
    }

    @Test
    void exposesEventsAsJavaSamCallbacks() {
        TraumaStartedCallback.EVENT().register(context -> {});
        LimbInjuryAllowCallback.EVENT().register(context -> true);
        LimbInjuryAppliedCallback.EVENT().register(context -> {});
        AdrenalineChangedCallback.EVENT().register(context -> {});
        PainShockStageChangedCallback.EVENT().register(context -> {});
        ConsciousnessStateChangeCallback.EVENT().register(context -> {});
        GameplayDataReloadedCallback.EVENT().register(context -> {});
    }

    @Test
    void keepsScalaImplementationHelpersOutOfTheJavaApi() {
        assertNoDeclaredMethods(
                BodyPart.class, "adjacent", "arms", "byId", "codec", "legs");
        assertNoDeclaredMethods(PainShockStage.class, "byId");
        Stream.of(
                        TraumaStartedContext.class,
                        LimbInjuryContext.class,
                        LimbInjuryAppliedContext.class,
                        AdrenalineChangedContext.class,
                        PainShockStageChangedContext.class,
                        ConsciousnessStateChangeContext.class,
                        GameplayDataReloadedContext.class)
                .forEach(context -> assertNoDeclaredMethods(context, "create"));
    }

    private static void assertNoDeclaredMethods(Class<?> type, String... forbiddenNames) {
        var forbidden = Set.of(forbiddenNames);
        assertFalse(
                Stream.of(type.getDeclaredMethods())
                        .map(method -> method.getName())
                        .anyMatch(forbidden::contains),
                () -> type.getName() + " leaks an implementation helper");
    }
}
