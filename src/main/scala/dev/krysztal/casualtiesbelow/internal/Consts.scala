package dev.krysztal.casualtiesbelow.internal

import net.minecraft.world.entity.EquipmentSlot

/** Domain-general constants shared across the mod: vanilla game invariants and the vanilla armor
  * slot set. Feature-specific tuning (sync cadences, effect fade durations) stays in its owning
  * subsystem — same numeric value does not imply same semantics.
  */
private[casualtiesbelow] object Consts {

  /** Vanilla runs twenty ticks per second; per-second recurrence math divides/multiplies by this.
    */
  val TicksPerSecond: Int = 20

  /** Inverse of `TicksPerSecond`: one tick in seconds. */
  val SecondsPerTick: Double = 1.0 / TicksPerSecond

  /** The four equipment slots making up a full armor set, in display order (head first).
    * Order-insensitive consumers treat this as a set; order-sensitive consumers render head first.
    */
  val ArmorSlots: List[EquipmentSlot] =
    List(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
}
