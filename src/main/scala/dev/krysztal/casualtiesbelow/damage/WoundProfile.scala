package dev.krysztal.casualtiesbelow.damage

import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.EvokerFangs
import net.minecraft.world.entity.projectile.Projectile

import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig.WoundProfileConfig

/** How one damage kind wounds a limb: skin integrity and muscle health lost per half-heart of
  * damage, external bleeding rate granted per wound (zero = the wound never bleeds, e.g. burns
  * cauterize), and pain granted per half-heart.
  */
final case class WoundProfile(
    skinPerPoint: Double,
    musclePerPoint: Double,
    bleedRatePerWound: Double,
    painPerPoint: Double
)

/** A classified hit: its profile, and whether the damage scatters as shrapnel across several random
  * body parts (explosions) instead of striking one located part.
  */
final case class Wound(profile: WoundProfile, scatter: Boolean)

/** Maps incoming damage sources to wound profiles.
  *
  * The cascade order is significant — earlier branches win:
  *
  *   1. fire (`IS_FIRE`, includes fireballs): burns
  *   1. explosions (`IS_EXPLOSION`, plus wither skulls): blasts, scattered
  *   1. projectiles: piercing types (arrow/trident/mob projectile) wound; spit, wind charges and
  *      other harmless projectiles are ignored
  *   1. sonic boom: blunt shockwave
  *   1. indirect magic: evoker fangs pierce (they erupt from the ground, so hit geometry locates
  *      them on the legs); witch potions and guardian beams leave no physical wound
  *   1. direct melee from a living attacker: sharp-weapon hits cut, other armed hits are blunt, and
  *      bare-handed hits bite/scratch — unless the attacker is a slam type
  *      ([[CasualtiesBelowTags.BluntMeleeEntities]])
  *
  * Everything else (drowning, freezing, starvation, wither/poison effects, magic, ...) leaves no
  * limb wound.
  */
object WoundProfiles {

  def classify(source: DamageSource): Option[Wound] = {
    if (source.is(DamageTypeTags.IS_FIRE)) {
      return Some(Wound(of(CasualtiesBelowConfig.BurnWound), scatter = false))
    }
    if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.WITHER_SKULL)) {
      return Some(Wound(of(CasualtiesBelowConfig.BlastWound), scatter = true))
    }
    if (source.getDirectEntity.isInstanceOf[Projectile]) {
      val pierces =
        source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT) ||
          source.is(DamageTypes.MOB_PROJECTILE)
      return Option.when(pierces)(Wound(of(CasualtiesBelowConfig.PierceWound), scatter = false))
    }
    if (source.is(DamageTypes.SONIC_BOOM)) {
      return Some(Wound(of(CasualtiesBelowConfig.BluntWound), scatter = false))
    }
    if (source.is(DamageTypes.INDIRECT_MAGIC)) {
      val fangs = source.getDirectEntity.isInstanceOf[EvokerFangs]
      return Option.when(fangs)(Wound(of(CasualtiesBelowConfig.PierceWound), scatter = false))
    }
    if (source.isDirect && source.getDirectEntity.isInstanceOf[LivingEntity]) {
      return Some(Wound(meleeProfile(source), scatter = false))
    }

    None
  }

  /** Melee sub-classification: sharp weapons cut, other weapons bludgeon, bare hands bite or
    * scratch — except slam-type attackers (slimes, golems, ...), which bludgeon bare-handed.
    */
  private def meleeProfile(source: DamageSource): WoundProfile = {
    // `getWeaponItem` is the empty stack for bare-handed attackers, not null.
    Option(source.getWeaponItem).filterNot(_.isEmpty) match {
      case Some(weapon) if weapon.is(CasualtiesBelowTags.SharpMeleeItems) =>
        of(CasualtiesBelowConfig.CutWound)
      case Some(_) =>
        of(CasualtiesBelowConfig.BluntWound)
      case None =>
        val slams = source.getDirectEntity.is(CasualtiesBelowTags.BluntMeleeEntities)
        of(if (slams) CasualtiesBelowConfig.BluntWound else CasualtiesBelowConfig.BiteWound)
    }
  }

  private def of(config: WoundProfileConfig): WoundProfile = WoundProfile(
    config.skinPerPoint.get(),
    config.musclePerPoint.get(),
    config.bleedRatePerWound.get(),
    config.painPerPoint.get()
  )
}
