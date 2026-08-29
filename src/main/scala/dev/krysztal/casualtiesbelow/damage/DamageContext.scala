package dev.krysztal.casualtiesbelow.damage

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource

/** Immutable provenance shared by every application produced from one classified damage event. */
private[casualtiesbelow] final case class DamageContext(
    player: ServerPlayer,
    source: DamageSource,
    damage: Double,
    ruleId: Identifier,
    applicationType: Identifier
)
