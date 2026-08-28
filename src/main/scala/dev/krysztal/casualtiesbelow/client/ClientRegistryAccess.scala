package dev.krysztal.casualtiesbelow.client

import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/** Access to the dynamic registries synced into the current client level. */
@Environment(EnvType.CLIENT)
object ClientRegistryAccess {
  def current: Option[RegistryAccess] = {
    Option(Minecraft.getInstance().level).map(_.registryAccess())
  }
}
