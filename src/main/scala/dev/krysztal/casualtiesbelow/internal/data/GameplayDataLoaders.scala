package dev.krysztal.casualtiesbelow.internal.data

import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.event.GameplayDataReloadedCallback
import dev.krysztal.casualtiesbelow.api.event.GameplayDataReloadedContext
import dev.krysztal.casualtiesbelow.data.schema.AdrenalineRuleData
import dev.krysztal.casualtiesbelow.data.schema.ArmorProtectionData
import dev.krysztal.casualtiesbelow.data.schema.DiscomfortData
import dev.krysztal.casualtiesbelow.data.schema.FoodImmuneData
import dev.krysztal.casualtiesbelow.data.schema.HitLocationData
import dev.krysztal.casualtiesbelow.data.schema.WoundProfile as WoundProfileEntry
import dev.krysztal.casualtiesbelow.data.schema.WoundRuleData

/** One reload transaction for all datapack-defined gameplay data types. */
object GameplayDataLoaders {
  private val WoundProfile =
    GameplayDataLoader[WoundProfileEntry]("wound_profile", WoundProfileEntry.Codec)
  private val WoundRule = GameplayDataLoader[WoundRuleData]("wound_rule", WoundRuleData.Codec)
  private val AdrenalineRule =
    GameplayDataLoader[AdrenalineRuleData]("adrenaline_rule", AdrenalineRuleData.Codec)
  private val ArmorProtection = GameplayDataLoader[ArmorProtectionData](
    "armor_protection",
    ArmorProtectionData.Codec
  )
  private val Discomfort = GameplayDataLoader[DiscomfortData]("discomfort", DiscomfortData.Codec)
  private val FoodImmuneItem =
    GameplayDataLoader[FoodImmuneData]("food_immune/item", FoodImmuneData.Codec)
  private val FoodImmuneTag =
    GameplayDataLoader[FoodImmuneData]("food_immune/tag", FoodImmuneData.Codec)
  private val HitLocation =
    GameplayDataLoader[HitLocationData]("hit_location", HitLocationData.Codec)

  def registerAll(): Unit = {
    DataResourceLoader
      .get()
      .registerReloadListener(CasualtiesBelow.ofIdentifier("gameplay_data"), ReloadListener)
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { (server, _, success) =>
      if (success) {
        GameplayDataReloadedCallback.EVENT
          .invoker()
          .onGameplayDataReloaded(new GameplayDataReloadedContext(server))
      }
    }
  }

  private object ReloadListener extends SimpleReloadListener[GameplayDataState] {

    override protected def prepare(state: SharedState): GameplayDataState = {
      val manager = state.resourceManager()
      val lookup = state.get(ResourceLoader.REGISTRY_LOOKUP_KEY)
      GameplayDataState.compile(
        GameplayDataStore(
          woundProfiles = WoundProfile.load(manager, lookup),
          woundRules = WoundRule.load(manager, lookup),
          armorProtection = ArmorProtection.load(manager, lookup),
          discomfort = Discomfort.load(manager, lookup),
          foodImmuneItems = FoodImmuneItem.load(manager, lookup),
          foodImmuneTags = FoodImmuneTag.load(manager, lookup),
          hitLocations = HitLocation.load(manager, lookup),
          adrenalineRules = AdrenalineRule.load(manager, lookup)
        )
      )
    }

    override protected def apply(prepared: GameplayDataState, state: SharedState): Unit = {
      // NOTE: Publish into the candidate resource store only; Minecraft installs it after every
      // reload listener succeeds, so a later failure leaves the live generation untouched.
      GameplayDataStores.publish(
        state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY),
        prepared
      )
    }
  }
}
