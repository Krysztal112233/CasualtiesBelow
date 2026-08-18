package dev.krysztal.casualtiesbelow

import java.util.function.BiFunction

import net.minecraft.client.gui.screens.Screen

import net.fabricmc.api.ClientModInitializer
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry

object CasualtiesBelowClient extends ClientModInitializer {
  override def onInitializeClient(): Unit = {
    // Expose the config screen through Forge Config API Port's built-in ModMenu integration.
    // Harmless when ModMenu is not installed (the registry is FCAP's own API).
    ConfigScreenFactoryRegistry.INSTANCE.register(
      CasualtiesBelow.ModId,
      (modId, parent) => ConfigurationScreen(modId, parent)
    )
  }
}
