package dev.krysztal.casualtiesbelow.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/** Built-in inputs that receive both shelf-drying behavior and client tint feedback. Datapacks may
  * extend the drying tag, but extra entries intentionally keep their own visual model.
  */
private[casualtiesbelow] object FiberClothDryingInputs {
  val BuiltInItems: Seq[Item] = Seq(
    Items.VINE,
    Items.WEEPING_VINES,
    Items.TWISTING_VINES,
    Items.SHORT_GRASS,
    Items.TALL_GRASS,
    Items.FERN,
    Items.LARGE_FERN,
    Items.DRY_SHORT_GRASS,
    Items.DRY_TALL_GRASS,
    Items.LEAF_LITTER
  )
}
