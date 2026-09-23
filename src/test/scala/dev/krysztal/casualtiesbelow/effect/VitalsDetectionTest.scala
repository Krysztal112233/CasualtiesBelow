package dev.krysztal.casualtiesbelow.effect

import dev.krysztal.casualtiesbelow.api.body.vitals.PainShockStage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class VitalsDetectionTest {

  @Test
  def opioidLevelMapsToFiveEqualAmplifierBands(): Unit = {
    assertEquals(None, amplifierForValue(0.0, 200.0, 5))
    assertEquals(Some(0), amplifierForValue(0.1, 200.0, 5))
    assertEquals(Some(0), amplifierForValue(40.0, 200.0, 5))
    assertEquals(Some(1), amplifierForValue(40.1, 200.0, 5))
    assertEquals(Some(2), amplifierForValue(100.0, 200.0, 5))
    assertEquals(Some(4), amplifierForValue(200.0, 200.0, 5))
  }

  @Test
  def dependenceMapsToThreeEqualAmplifierBands(): Unit = {
    val firstBand = 100.0 / 3.0
    val secondBand = 200.0 / 3.0

    assertEquals(None, amplifierForValue(0.0, 100.0, 3))
    assertEquals(Some(0), amplifierForValue(firstBand, 100.0, 3))
    assertEquals(Some(1), amplifierForValue(firstBand + 0.01, 100.0, 3))
    assertEquals(Some(1), amplifierForValue(secondBand, 100.0, 3))
    assertEquals(Some(2), amplifierForValue(100.0, 100.0, 3))
  }

  @Test
  def nonPositiveAndNonFiniteValuesDoNotProduceAnEffect(): Unit = {
    assertEquals(None, amplifierForValue(-1.0, 200.0, 5))
    assertEquals(None, amplifierForValue(Double.NaN, 200.0, 5))
    assertEquals(None, amplifierForValue(Double.PositiveInfinity, 200.0, 5))
  }

  @Test
  def physiologyCuesUseStrictThresholdBoundaries(): Unit = {
    assertEquals(None, amplifierBelowThreshold(50.0, 50.0))
    assertEquals(Some(0), amplifierBelowThreshold(49.9, 50.0))
    assertEquals(None, amplifierAboveThreshold(39.5, 39.5))
    assertEquals(Some(0), amplifierAboveThreshold(39.6, 39.5))
    assertEquals(None, amplifierAboveThreshold(0.0, 0.0))
    assertEquals(Some(0), amplifierAboveThreshold(0.01, 0.0))
    assertEquals(None, amplifierWhen(false))
    assertEquals(Some(0), amplifierWhen(true))
  }

  @Test
  def bloodLossCueUsesFractionOfHealthyMaximum(): Unit = {
    assertEquals(None, bloodLossAmplifier(4500.0, 5000.0, 0.9))
    assertEquals(Some(0), bloodLossAmplifier(4499.0, 5000.0, 0.9))
    assertEquals(None, bloodLossAmplifier(5000.0, 5000.0, 0.9))
    assertEquals(None, bloodLossAmplifier(4000.0, 0.0, 0.9))
    assertEquals(None, bloodLossAmplifier(Double.NaN, 5000.0, 0.9))
  }

  @Test
  def shockCueFollowsTheActiveEpisodeStages(): Unit = {
    assertEquals(None, painShockAmplifierFromStage(PainShockStage.Stable))
    assertEquals(Some(0), painShockAmplifierFromStage(PainShockStage.Deferred))
    assertEquals(Some(0), painShockAmplifierFromStage(PainShockStage.Collapsed))
    assertEquals(Some(0), painShockAmplifierFromStage(PainShockStage.Recovering))
  }

  @Test
  def alertnessAndWetnessCuesRequirePositiveValues(): Unit = {
    assertEquals(None, amplifierAboveThreshold(0.0, 0.0))
    assertEquals(Some(0), amplifierAboveThreshold(0.01, 0.0))
  }

  @Test
  def dirtinessCueStartsAtTheGrimyBand(): Unit = {
    assertEquals(None, amplifierAtOrAboveThreshold(29.9, 30.0))
    assertEquals(Some(0), amplifierAtOrAboveThreshold(30.0, 30.0))
    assertEquals(Some(0), amplifierAtOrAboveThreshold(30.1, 30.0))
    assertEquals(None, amplifierAtOrAboveThreshold(Double.NaN, 30.0))
    assertEquals(None, amplifierAtOrAboveThreshold(30.0, Double.PositiveInfinity))
  }
}
