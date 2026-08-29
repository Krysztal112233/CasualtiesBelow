package dev.krysztal.casualtiesbelow.api.data

import net.minecraft.core.HolderLookup

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader

import dev.krysztal.casualtiesbelow.CasualtiesBelow
import dev.krysztal.casualtiesbelow.api.wound.WoundProfile as WoundProfileEntry
import dev.krysztal.casualtiesbelow.api.wound.WoundProfiles

/** Reload-listener stores for the datapack-defined gameplay data types. */
object GameplayDataLoaders {
  val WoundProfile = GameplayDataLoader[WoundProfileEntry]("wound_profile", WoundProfileEntry.Codec)
  val WoundRule = GameplayDataLoader[WoundRuleData]("wound_rule", WoundRuleData.Codec)
  val ArmorProtection = GameplayDataLoader[ArmorProtectionData](
    "armor_protection",
    ArmorProtectionData.Codec
  )
  val Discomfort = GameplayDataLoader[DiscomfortData]("discomfort", DiscomfortData.Codec)
  val HitLocation = GameplayDataLoader[HitLocationData]("hit_location", HitLocationData.Codec)

  def registerAll(): Unit = {
    register("wound_profile", WoundProfile)
    register("wound_rule", WoundRule)
    register("armor_protection", ArmorProtection)
    register("discomfort", Discomfort)
    register("hit_location", HitLocation)
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, successful) => {
      if (successful) WoundProfiles.rebuild()
    })
    ServerLifecycleEvents.SERVER_STARTED.register(_ => WoundProfiles.rebuild())
    ServerLifecycleEvents.SERVER_STOPPED.register(_ => WoundProfiles.clear())
  }

  private def register[T](segment: String, loader: GameplayDataLoader[T]): Unit = {
    DataResourceLoader
      .get()
      .registerReloadListener(
        CasualtiesBelow.ofIdentifier(s"gameplay_data/$segment"),
        (lookup: HolderLookup.Provider) => loader.bind(lookup)
      )
  }
}
