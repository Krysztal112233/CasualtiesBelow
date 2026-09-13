package dev.krysztal.casualtiesbelow.item

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player

/** Common-side bridge for opening the client-only injection screen from item use code. The client
  * initializer installs the opener; on a dedicated server none is ever installed, so screen classes
  * are never loaded there and [[open]] is a no-op.
  */
private[casualtiesbelow] object InjectionScreenHook {
  @volatile private var opener: Option[(Player, InteractionHand) => Unit] = None

  def register(opener: (Player, InteractionHand) => Unit): Unit =
    this.opener = Some(opener)

  def open(player: Player, hand: InteractionHand): Unit =
    opener.foreach(open => open(player, hand))
}
