package dev.krysztal.casualtiesbelow

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.Screen

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper

import dev.krysztal.casualtiesbelow.bleeding.BleedingParticles
import dev.krysztal.casualtiesbelow.sync.GameplayDataSync
import dev.krysztal.casualtiesbelow.ui.BodyStatusScreen

import net.neoforged.neoforge.client.gui.ConfigurationScreen
import org.lwjgl.glfw.GLFW
import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry

object CasualtiesBelowClient extends ClientModInitializer {

  val KeyCategory: KeyMapping.Category =
    KeyMapping.Category.register(CasualtiesBelow.ofIdentifier("main"))

  /** Opens the body status screen; pressing it again while the screen is open closes it (handled in
    * [[BodyStatusScreen.keyPressed]], since keybinds do not fire while a screen is open).
    */
  val OpenScreenKey: KeyMapping = KeyMappingHelper.registerKeyMapping(
    new KeyMapping(
      "key.casualtiesbelow.open_screen",
      InputConstants.Type.KEYSYM,
      GLFW.GLFW_KEY_R,
      KeyCategory
    )
  )

  override def onInitializeClient(): Unit = {
    // Expose the config screen through Forge Config API Port's built-in ModMenu integration.
    // Harmless when ModMenu is not installed (the registry is FCAP's own API).
    ConfigScreenFactoryRegistry.INSTANCE.register(
      CasualtiesBelow.ModId,
      (modId, parent) => ConfigurationScreen(modId, parent)
    )

    ClientTickEvents.END_CLIENT_TICK.register { client =>
      while (OpenScreenKey.consumeClick()) {
        Option(client.player)
          .filter(_ => Option(client.gui.screen()).isEmpty)
          .foreach(_ => client.gui.setScreen(BodyStatusScreen()))
      }
    }
    BleedingParticles.register()
    GameplayDataSync.registerClient()
  }
}
