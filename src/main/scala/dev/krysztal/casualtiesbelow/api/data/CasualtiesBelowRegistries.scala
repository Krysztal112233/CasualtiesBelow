package dev.krysztal.casualtiesbelow.api.data

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

import net.fabricmc.fabric.api.event.registry.DynamicRegistries

import dev.krysztal.casualtiesbelow.CasualtiesBelow

/** Resource keys and bootstrap registration for the mod's synced datapack registries. */
object CasualtiesBelowRegistries {
  val WoundProfile: ResourceKey[Registry[WoundProfileData]] = registryKey("wound_profile")
  val WoundRule: ResourceKey[Registry[WoundRuleData]] = registryKey("wound_rule")
  val ArmorProtection: ResourceKey[Registry[ArmorProtectionData]] = registryKey("armor_protection")
  val Discomfort: ResourceKey[Registry[DiscomfortData]] = registryKey("discomfort")
  val FallRules: ResourceKey[Registry[FallRulesData]] = registryKey("fall_rules")
  val HitLocation: ResourceKey[Registry[HitLocationData]] = registryKey("hit_location")

  /** Must run before the mod registers any consumer that may access world registries. */
  def registerAll(): Unit = {
    DynamicRegistries.registerSynced(WoundProfile, WoundProfileData.Codec)
    DynamicRegistries.registerSynced(WoundRule, WoundRuleData.Codec)
    DynamicRegistries.registerSynced(ArmorProtection, ArmorProtectionData.Codec)
    DynamicRegistries.registerSynced(Discomfort, DiscomfortData.Codec)
    DynamicRegistries.registerSynced(FallRules, FallRulesData.Codec)
    DynamicRegistries.registerSynced(HitLocation, HitLocationData.Codec)
  }

  def entryKey[T](registry: ResourceKey[? <: Registry[T]], path: String): ResourceKey[T] =
    ResourceKey.create(registry, CasualtiesBelow.ofIdentifier(path))

  private def registryKey[T](path: String): ResourceKey[Registry[T]] =
    ResourceKey.createRegistryKey(CasualtiesBelow.ofIdentifier(path))
}
