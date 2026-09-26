package dev.krysztal.casualtiesbelow.internal.extensions

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/** Enrichments over [Level] for the broadcast-sound shapes this mod repeats. */
private[casualtiesbelow] object LevelExtensions {

  extension (level: Level) {

    /** Plays a block-at-`pos` sound at vanilla default volume and pitch. */
    def playBlockSound(pos: BlockPos, sound: SoundEvent): Unit =
      level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f)

    /** Plays a player-attributed sound from the player's position. */
    def playPlayerSound(
        player: Player,
        sound: SoundEvent,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ): Unit =
      level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch)
  }
}
