package dev.krysztal.casualtiesbelow.effect

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.crafting.Ingredient

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder

import dev.krysztal.casualtiesbelow.api.CasualtiesBelowApi
import dev.krysztal.casualtiesbelow.internal.Consts
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems

/** Registry of the mod's standalone gameplay effects and their potions — effects that own their
  * logic instead of mirroring vitals (for those, see [[CasualtiesBelowEffects]]). Registration
  * follows vanilla's `MobEffects`/`Potions` style: public holders bound through private `register`
  * helpers, all bound when this object initializes (no later than [[register]]).
  *
  * Note: the public holders cannot be introduced by a tuple-pattern val — pattern identifiers
  * starting with an uppercase letter are stable-identifier matches, not bindings, so no members
  * would be generated. Bind a lowercase intermediate and project its components instead.
  */
private[casualtiesbelow] object CasualtiesBelowPotionEffects {

  val (SkinRegeneration @ _, SkinRegenerationPotion @ _) = register(
    "skin_regeneration",
    new SkinRegenerationEffect,
    Consts.Regeneration.SkinRegenerationPotionTicks,
    Ingredient.of(CasualtiesBelowItems.BasicBandage)
  )

  val (MuscleRecovery @ _, MuscleRecoveryPotion @ _) = register(
    "muscle_recovery",
    new MuscleRecoveryEffect,
    Consts.Regeneration.MuscleRecoveryPotionTicks,
    Ingredient.of(Items.COOKED_BEEF)
  )

  private def register(
      name: String,
      effect: MobEffect,
      duration: Int,
      ingredient: Ingredient
  ): (Holder[MobEffect], Holder[Potion]) = {
    val effectHolder = registerEffect(name, effect)
    val potionHolder =
      registerPotion(name, new Potion(name, new MobEffectInstance(effectHolder, duration, 0)))

    FabricPotionBrewingBuilder.BUILD.register { builder =>
      builder
        .asInstanceOf[FabricPotionBrewingBuilder]
        .registerPotionRecipe(
          Potions.AWKWARD,
          ingredient,
          potionHolder
        )
    }

    (effectHolder, potionHolder)
  }

  private def registerEffect(name: String, effect: MobEffect): Holder[MobEffect] = {
    Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, CasualtiesBelowApi.id(name), effect)
  }

  private def registerPotion(name: String, potion: Potion): Holder[Potion] = {
    Registry.registerForHolder(BuiltInRegistries.POTION, CasualtiesBelowApi.id(name), potion)
  }

  /** Calling this method initializes this object, which binds every eager val above — registering
    * the effect, its potion, and the brewing recipe in one go.
    */
  def register(): Unit = {}
}
