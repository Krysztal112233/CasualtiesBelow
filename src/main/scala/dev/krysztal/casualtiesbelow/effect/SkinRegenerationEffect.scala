package dev.krysztal.casualtiesbelow.effect

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.crafting.Ingredient

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.body
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems
import dev.krysztal.casualtiesbelow.physiology.limb.BleedingCalc

/** Skin Regeneration: a genuinely independent beneficial effect that each tick picks one
  * skin-damaged limb at random and repairs it, even while the wound is still bleeding. Raising skin
  * immediately lowers that limb's allowed bleeding cap, mirroring the vanilla-Regeneration
  * micro-repair in Limb.
  *
  * Unlike the vitals-driven mirror effects in [[CasualtiesBelowEffects]], this effect owns its
  * gameplay logic: nothing applies or removes it on the player's behalf, so it obeys vanilla
  * duration/amplifier semantics and works from any source (potions, commands, beacons, other mods).
  */
private[casualtiesbelow] final class SkinRegenerationEffect
    extends MobEffect(MobEffectCategory.BENEFICIAL, 0xe8a08c) {

  override def applyEffectTick(
      level: ServerLevel,
      entity: LivingEntity,
      amplifier: Int
  ): Boolean = {
    if (!entity.isInstanceOf[ServerPlayer]) return true
    val player = entity.asInstanceOf[ServerPlayer]

    val restore =
      Consts.Regeneration.SkinRegenerationEffectPerTick * (amplifier + 1).toDouble

    val part = {
      val damaged = BodyPart.values
        .filter(player.body.stats(_).skinIntegrity != LimbSnapshot.MaxValue)
      // NOTE: no damaged part
      if (!damaged.nonEmpty) None
      else Some(damaged(player.getRandom.nextInt(damaged.size)))
    }

    part.foreach { part =>
      val mutation = BodyMutations.mutate(player, part) { state =>
        if (state.skinIntegrity < LimbSnapshot.MaxValue) {
          state.skinIntegrity = (state.skinIntegrity + restore).min(LimbSnapshot.MaxValue)
          state.externalBleedingRate =
            state.externalBleedingRate.min(BleedingCalc.cap(state.skinIntegrity))
        }
      }
      val reachedFullSkin =
        mutation.before.skinIntegrity < LimbSnapshot.MaxValue &&
          mutation.after.skinIntegrity >= LimbSnapshot.MaxValue
      val stoppedBleeding =
        mutation.before.externalBleedingRate > 0.0 &&
          mutation.after.externalBleedingRate <= 0.0
      if (reachedFullSkin || stoppedBleeding) BodyMutations.markDirty(player)
    }

    true
  }

  /** Applies every tick for smooth regrowth instead of vanilla Regeneration's interval pulses. */
  override def shouldApplyEffectTickThisTick(tickCount: Int, amplifier: Int): Boolean = true

}

/** Registration of the standalone effect and its brewed potion. */
private[casualtiesbelow] object SkinRegenerationEffect {
  private val EffectKey: ResourceKey[MobEffect] =
    ResourceKey.create(Registries.MOB_EFFECT, CasualtiesBelowApi.id("skin_regeneration"))
  private val PotionKey: ResourceKey[Potion] =
    ResourceKey.create(Registries.POTION, CasualtiesBelowApi.id("skin_regeneration"))

  /** Holder for the effect, bound on first use and no later than [[register]]. Registration stays
    * lazy so frozen-registry unit tests can reference the key without registering content.
    */
  lazy val SkinRegeneration: Holder[MobEffect] =
    Registry.registerForHolder(
      BuiltInRegistries.MOB_EFFECT,
      EffectKey,
      new SkinRegenerationEffect
    )

  /** Holder for the brewed potion; same laziness contract as [[SkinRegeneration]]. */
  lazy val SkinRegenerationPotion: Holder[Potion] =
    Registry.registerForHolder(
      BuiltInRegistries.POTION,
      PotionKey,
      new Potion(
        "skin_regeneration",
        new MobEffectInstance(
          SkinRegeneration,
          Consts.Regeneration.SkinRegenerationPotionTicks,
          0
        )
      )
    )

  /** Binds both holders and adds the brewing recipe (awkward potion + basic bandage). */
  def register(): Unit = {
    SkinRegeneration
    SkinRegenerationPotion
    FabricPotionBrewingBuilder.BUILD.register { builder =>
      builder
        .asInstanceOf[FabricPotionBrewingBuilder]
        .registerPotionRecipe(
          Potions.AWKWARD,
          Ingredient.of(CasualtiesBelowItems.BasicBandage),
          SkinRegenerationPotion
        )
    }
    ()
  }
}
