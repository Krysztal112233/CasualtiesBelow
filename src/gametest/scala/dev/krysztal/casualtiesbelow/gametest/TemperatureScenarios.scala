package dev.krysztal.casualtiesbelow.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionCallback
import dev.krysztal.casualtiesbelow.api.event.BodyHeatContributionContext
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.BiomeExtensions.*
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.progression.InjuryProgression
import dev.krysztal.casualtiesbelow.physiology.temperature.TemperatureCalc

/** In-game validation of the body-temperature progression.
  *
  * The GameTest world's biome is not controllable (flat overworld, plains-like), so no scenario may
  * assert biome-specific absolute temperatures: expectations are computed at runtime from the
  * actual biome and the live config, and curve-level behavior (cold biomes, the comfort band, the
  * drying curve) is covered by `TemperatureCalcTest` instead. Scenarios that compare approach
  * behavior assert the world's equilibrium sits inside the comfort band first, so a changed world
  * preset fails loudly rather than silently testing nothing.
  */
object TemperatureScenarios {

  /** Probe listener for the channel × armor semantics scenario. Fabric events have no unregister,
    * so the probe is registered once and stays inert unless armed; the scenario must disarm it in
    * `finally` so no state leaks into other tests.
    */
  @volatile private var probeDirectPerMinute = 0.0
  @volatile private var probeDissipativePerMinute = 0.0

  BodyHeatContributionCallback.EVENT.register(new BodyHeatContributionCallback {
    override def contribute(
        player: ServerPlayer,
        frame: BodyHeatContributionCallback.Frame,
        context: BodyHeatContributionContext
    ): Unit = {
      if (probeDirectPerMinute != 0.0) context.addDirect(probeDirectPerMinute)
      if (probeDissipativePerMinute != 0.0) context.addDissipative(probeDissipativePerMinute)
    }
  })

  /** From both sides of the equilibrium the core moves towards it (directional, not exact). */
  def coreApproachesEquilibriumFromBothSides(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val equilibrium = equilibriumAt(helper, player)

    VitalsMutations.setBodyTemperature(vitals, equilibrium - 2.0)
    tick(player, 400)
    val fromBelow = vitals.bodyTemperature
    helper.assertTrue(
      fromBelow > equilibrium - 2.0 + 0.05,
      s"core must rise towards the equilibrium, got $fromBelow from ${equilibrium - 2.0}"
    )
    helper.assertTrue(
      fromBelow <= equilibrium,
      s"approach must not overshoot the equilibrium, got $fromBelow vs $equilibrium"
    )

    VitalsMutations.setBodyTemperature(vitals, equilibrium + 2.0)
    tick(player, 400)
    val fromAbove = vitals.bodyTemperature
    helper.assertTrue(
      fromAbove < equilibrium + 2.0 - 0.05,
      s"core must fall towards the equilibrium, got $fromAbove from ${equilibrium + 2.0}"
    )
    helper.assertTrue(
      fromAbove >= equilibrium,
      s"approach must not overshoot the equilibrium, got $fromAbove vs $equilibrium"
    )
    helper.succeed()
  }

  /** Immersion soaks the player quickly and doubles the approach rate: starting equally far above
    * the equilibrium, the immersed phase closes strictly more distance than the air phase. The core
    * starts above the equilibrium so evaporative cooling (active once wet) works in the same
    * direction as the approach and cannot mask the rate multiplier.
    */
  def immersionAcceleratesApproachAndSoaks(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val airEquilibrium = equilibriumAt(helper, player, immersed = false)
    val waterEquilibrium = equilibriumAt(helper, player, immersed = true)
    helper.assertTrue(
      math.abs(waterEquilibrium - airEquilibrium) < 0.01,
      s"scenario assumes a biome whose mapped temperature is above freezing (air $airEquilibrium " +
        s"vs water $waterEquilibrium); curve-level cold-water behavior is unit-tested"
    )

    VitalsMutations.setBodyTemperature(vitals, airEquilibrium + 2.0)
    tick(player, 400)
    val airClosed = (airEquilibrium + 2.0) - vitals.bodyTemperature

    val relative = new BlockPos(1, 1, 1)
    helper.setBlock(relative, Blocks.WATER)
    val waterPos = helper.absolutePos(relative)
    player.setPos(waterPos.getX + 0.5, waterPos.getY + 0.1, waterPos.getZ + 0.5)
    VitalsMutations.setBodyTemperature(vitals, airEquilibrium + 2.0)
    // FakePlayer.tick is a no-op, so baseTick is driven once to refresh the water flag.
    player.baseTick()
    helper.assertTrue(player.isInWater, "fake player should register as in water after baseTick")
    tick(player, 400)
    val waterClosed = (airEquilibrium + 2.0) - vitals.bodyTemperature

    // Threshold must exceed the evaporative-cooling confusion (wetness dissipative cooling adds
    // ≈0.06–0.09 over the run): a broken 1× multiplier must fail, not hide behind evaporation.
    helper.assertTrue(
      waterClosed > airClosed + 0.15,
      s"immersion must accelerate the approach: closed $waterClosed in water vs $airClosed in air"
    )
    helper.assertTrue(
      vitals.wetness > 0.95,
      s"20 s immersed must soak the player, got wetness ${vitals.wetness}"
    )
    helper.succeed()
  }

  /** Sustained exhaustion registers as exercise heat on the direct channel, pushing the core above
    * the equilibrium. The injected total stays below vanilla's 40.0 exhaustion cap so the signal
    * never saturates (FoodData.tick is not driven, so nothing drains it either).
    */
  def exerciseHeatRaisesCore(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val equilibrium = equilibriumAt(helper, player)
    VitalsMutations.setBodyTemperature(vitals, equilibrium)

    (1 to 400).foreach { _ =>
      player.getFoodData.addExhaustion(0.09f)
      InjuryProgression.tickForGameTest(player)
    }
    helper.assertTrue(
      vitals.bodyTemperature > equilibrium + 0.1,
      s"sustained exertion must warm the core above the equilibrium, " +
        s"got ${vitals.bodyTemperature} vs $equilibrium"
    )
    helper.succeed()
  }

  /** Being on fire heats on the direct channel and flash-dries wetness through the drying bonus:
    * from 0.8 wetness, the soaked state clears within seconds while the core climbs.
    */
  def onFireHeatsAndFlashDries(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val equilibrium = equilibriumAt(helper, player)
    VitalsMutations.setBodyTemperature(vitals, equilibrium)
    VitalsMutations.setWetness(vitals, 0.8)

    // The manual ticks never drive baseTick, so the burning DOT is emitted by hand at its
    // vanilla rhythm — one on_fire hit every 20 ticks, through the real damage-entry pipeline.
    // igniteForTicks keeps the flag lit for the flash-dry side of the assertion.
    player.igniteForTicks(340)
    helper.assertTrue(player.isOnFire, "player must be on fire after igniteForTicks")
    val sources = helper.getLevel.damageSources()
    (1 to 300).foreach { i =>
      if (i % 20 == 0) {
        player.hurtServer(helper.getLevel, sources.onFire(), 1.0f)
      }
      tick(player, 1)
    }
    helper.assertTrue(
      vitals.bodyTemperature > equilibrium + 0.15,
      s"fire contact heat must warm the core, got ${vitals.bodyTemperature} vs $equilibrium"
    )
    helper.assertTrue(
      vitals.wetness < 0.5,
      s"fire must flash-dry wetness within seconds, got ${vitals.wetness} from 0.8 after 15 s"
    )
    helper.succeed()
  }

  /** The hot_floor damage type applies the medium contact tier, per tick while standing on it. */
  def hotFloorDamageHeats(helper: GameTestHelper): Unit = {
    contactDamageHeats(helper, _.hotFloor())
  }

  /** The campfire damage type applies the medium contact tier. */
  def campfireDamageHeats(helper: GameTestHelper): Unit = {
    contactDamageHeats(helper, _.campfire())
  }

  /** The lava damage type applies the extreme tier, strictly above the burning tier — each at its
    * honest vanilla rhythm: lava attempts per tick, the burning DOT once per 20 ticks.
    */
  def lavaDamageOutranksBurning(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val lavaPlayer = GameTestPlayers.createSurvivalPlayer(helper)
    val firePlayer = GameTestPlayers.createSurvivalPlayer(helper)
    val equilibrium = equilibriumAt(helper, lavaPlayer)
    resetCore(equilibrium, lavaPlayer, firePlayer)

    val sources = helper.getLevel.damageSources()
    (1 to 200).foreach { i =>
      lavaPlayer.hurtServer(helper.getLevel, sources.lava(), 4.0f)
      if (i % 20 == 0) {
        firePlayer.hurtServer(helper.getLevel, sources.onFire(), 1.0f)
      }
      tick(lavaPlayer, 1)
      tick(firePlayer, 1)
    }
    val lavaRise = lavaPlayer.vitals.bodyTemperature - equilibrium
    val fireRise = firePlayer.vitals.bodyTemperature - equilibrium
    helper.assertTrue(
      lavaRise > fireRise,
      s"the lava tier must outrank the burning tier, got $lavaRise vs $fireRise"
    )
    helper.succeed()
  }

  /** Damage outside the fire table never arms the archive: the core does not move. */
  def unrelatedDamageDoesNotHeat(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val equilibrium = equilibriumAt(helper, player)
    VitalsMutations.setBodyTemperature(vitals, equilibrium)

    val sources = helper.getLevel.damageSources()
    (1 to 10).foreach { _ =>
      player.hurtServer(helper.getLevel, sources.magic(), 1.0f)
      tick(player, 1)
    }
    helper.assertTrue(
      math.abs(vitals.bodyTemperature - equilibrium) <= 1e-9,
      s"unrelated damage must not warm the core, got ${vitals.bodyTemperature} vs $equilibrium"
    )
    helper.succeed()
  }

  /** Channel × armor semantics with the probe listener: the direct channel bypasses armor (full
    * leather warms exactly like naked), while the dissipative channel pays the armor's dissipation
    * block (full leather cools strictly slower than naked).
    */
  def dissipativePaysArmorBlockAndDirectBypasses(helper: GameTestHelper): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val naked = GameTestPlayers.createSurvivalPlayer(helper)
    val leather = GameTestPlayers.createSurvivalPlayer(helper)
    equipFullLeather(leather)
    val equilibrium = equilibriumAt(helper, naked)
    helper.assertTrue(
      math.abs(equilibrium - 37.0) < 0.5,
      s"scenario assumes an in-band equilibrium so armor cannot move the approach target, " +
        s"got $equilibrium"
    )

    try {
      probeDirectPerMinute = 6.0
      resetCore(equilibrium, naked, leather)
      tick(naked, 200)
      tick(leather, 200)
      val nakedDirect = naked.vitals.bodyTemperature - equilibrium
      val leatherDirect = leather.vitals.bodyTemperature - equilibrium
      helper.assertTrue(
        nakedDirect > 0.5,
        s"direct probe heat must warm the naked player, got $nakedDirect after 10 s"
      )
      helper.assertTrue(
        math.abs(nakedDirect - leatherDirect) < 0.05,
        s"armor must not touch the direct channel: naked $nakedDirect vs leather $leatherDirect"
      )
      probeDirectPerMinute = 0.0

      probeDissipativePerMinute = 6.0
      resetCore(equilibrium, naked, leather)
      tick(naked, 200)
      tick(leather, 200)
      val nakedCooled = equilibrium - naked.vitals.bodyTemperature
      val leatherCooled = equilibrium - leather.vitals.bodyTemperature
      helper.assertTrue(
        nakedCooled > 0.5,
        s"dissipative probe cooling must cool the naked player, got $nakedCooled after 10 s"
      )
      helper.assertTrue(
        nakedCooled > leatherCooled + 0.15,
        s"leather's dissipation block must slow cooling: naked $nakedCooled vs " +
          s"leather $leatherCooled"
      )
    } finally {
      probeDirectPerMinute = 0.0
      probeDissipativePerMinute = 0.0
    }
    helper.succeed()
  }

  /** The access-widened downfall read returns a sane humidity value. */
  def biomeDownfallIsReadable(helper: GameTestHelper): Unit = {
    val biome = helper.getLevel.getBiome(helper.absolutePos(new BlockPos(1, 1, 1))).value()
    val downfall = biome.climateSettings.downfall
    helper.assertTrue(
      !downfall.isNaN && downfall >= 0.0f && downfall <= 1.0f,
      s"biome downfall must read within [0, 1], got $downfall"
    )
    helper.succeed()
  }

  // ---- Helpers -----------------------------------------------------------------

  private def tick(player: ServerPlayer, ticks: Int): Unit =
    (1 to ticks).foreach(_ => InjuryProgression.tickForGameTest(player))

  /** The equilibrium core temperature the progression computes for the player's current position,
    * derived from the actual biome and live config — GameTest biomes are not controllable.
    */
  private def equilibriumAt(
      helper: GameTestHelper,
      player: ServerPlayer,
      immersed: Boolean = false
  ): Double = {
    val level = helper.getLevel
    val pos = player.blockPosition()
    val biome = level.getBiome(pos).value()
    val mapped = biome.mappedTemperature(pos, level.getSeaLevel)
    val apparent = TemperatureCalc.apparentTemperature(mapped, immersed)
    CasualtiesBelowConfig.temperature.comfortBandFormula.evaluate(
      apparent,
      CasualtiesBelowConfig.temperature.comfortLowCelsius.get(),
      CasualtiesBelowConfig.temperature.comfortHighCelsius.get(),
      CasualtiesBelowConfig.temperature.comfortSlope.get()
    )
  }

  /** Contact sources fire the entry-layer event once per tick; emulate standing in the source for
    * ten ticks and expect the medium tier to warm the core.
    */
  private def contactDamageHeats(
      helper: GameTestHelper,
      pick: net.minecraft.world.damagesource.DamageSources => net.minecraft.world.damagesource.DamageSource
  ): Unit = {
    helper.getLevel.setRainLevel(0.0f)
    val player = GameTestPlayers.createSurvivalPlayer(helper)
    val vitals = player.vitals
    val equilibrium = equilibriumAt(helper, player)
    VitalsMutations.setBodyTemperature(vitals, equilibrium)

    val sources = helper.getLevel.damageSources()
    (1 to 40).foreach { _ =>
      player.hurtServer(helper.getLevel, pick(sources), 1.0f)
      tick(player, 1)
    }
    helper.assertTrue(
      vitals.bodyTemperature > equilibrium,
      s"contact damage must warm the core, got ${vitals.bodyTemperature} vs $equilibrium"
    )
    helper.succeed()
  }

  private def equipFullLeather(player: ServerPlayer): Unit = {
    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET))
    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE))
    player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS))
    player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS))
  }

  private def resetCore(equilibrium: Double, players: ServerPlayer*): Unit =
    players.foreach(player => VitalsMutations.setBodyTemperature(player.vitals, equilibrium))
}
