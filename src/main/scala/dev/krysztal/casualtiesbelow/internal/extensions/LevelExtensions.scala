package dev.krysztal.casualtiesbelow.internal.extensions

import scala.collection.immutable.LazyList
import scala.jdk.StreamConverters.*

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

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

    /** Lazily scans the Manhattan (taxicab) region around `center`: `radius` blocks of horizontal
      * reach and `dy` blocks of vertical reach. Positions in unloaded chunks or outside the world
      * are skipped, so the scan never triggers chunk loads; returned positions are immutable
      * copies, safe to retain.
      *
      * @param center
      *   the region's center
      * @param radius
      *   horizontal reach on the X/Z axes
      * @param dy
      *   vertical reach on the Y axis; `-1` (the default) means "same as `radius`"
      * @return
      *   a memoized lazy sequence of `(pos, state)` pairs
      */
    def loadedStatesAround(
        center: BlockPos,
        radius: Int,
        dy: Int = -1
    ): LazyList[(pos: BlockPos, state: BlockState)] = {
      val checkedDy =
        if (dy == -1) radius
        else dy

      BlockPos
        .withinManhattanStream(center, radius, checkedDy, radius)
        // NOTE: SKIP UNLOADED BLOCK
        .filter(level.isLoaded(_))
        .map(pos => (pos.immutable(), level.getBlockState(pos)))
        .toScala(LazyList)
    }
  }
}
