package dev.krysztal.casualtiesbelow.damage

import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.EvokerFangs
import net.minecraft.world.entity.projectile.Projectile

import dev.krysztal.casualtiesbelow.component.BodyPart
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

/** A classified hit: its profile, whether the damage scatters as shrapnel across several random
  * body parts (explosions) instead of striking one located part, and whether the hit is forced onto
  * a specific part regardless of geometry (falling objects always land on the head).
  */
final case class Wound(
    profile: WoundProfile,
    scatter: Boolean,
    forcedPart: Option[BodyPart] = None
)

/** Maps incoming damage sources to wound profiles.
  *
  * The cascade order is significant — earlier branches win:
  *
  *   1. fire (`IS_FIRE`, includes fireballs): burns
  *   1. explosions (`IS_EXPLOSION`, plus wither skulls): blasts, scattered
  *   1. projectiles: piercing types (arrow/trident/mob projectile) wound; spit, wind charges and
  *      other harmless projectiles are ignored
  *   1. falling objects (anvils, blocks, stalactites): crush or pierce, forced onto the head
  *   1. environmental pricks (cactus, sweet berry bushes): skin scratches only
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

  def classify(source: DamageSource): Option[Wound] = source match {
    case s if s.is(DamageTypeTags.IS_FIRE) => wound(CasualtiesBelowConfig.BurnWound)
    case s if s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypes.WITHER_SKULL) =>
      Some(Wound(of(CasualtiesBelowConfig.BlastWound), scatter = true))
    case s if s.getDirectEntity.isInstanceOf[Projectile] => projectileProfile(s)
    case s if isFallingObject(s)                         => fallingObjectProfile(s)
    case s if s.is(DamageTypes.CACTUS) || s.is(DamageTypes.SWEET_BERRY_BUSH) =>
      wound(CasualtiesBelowConfig.PrickWound)
    case s if s.is(DamageTypes.SONIC_BOOM)     => wound(CasualtiesBelowConfig.BluntWound)
    case s if s.is(DamageTypes.INDIRECT_MAGIC) => magicProfile(s)
    case s if isMelee(s)                       => Some(Wound(meleeProfile(s), scatter = false))
    case _                                     => None
  }

  private def wound(config: WoundProfileConfig): Option[Wound] = {
    Some(Wound(of(config), scatter = false))
  }

  private def isMelee(source: DamageSource): Boolean = {
    source.isDirect && source.getDirectEntity.isInstanceOf[LivingEntity]
  }

  /** Falling anvils and blocks crush, falling stalactites pierce — all land on the head regardless
    * of hit geometry (helmets then mitigate, as in vanilla).
    */
  private def fallingObjectProfile(source: DamageSource): Option[Wound] = {
    val profile =
      if (source.is(DamageTypes.FALLING_STALACTITE)) CasualtiesBelowConfig.PierceWound
      else CasualtiesBelowConfig.BluntWound
    Some(Wound(of(profile), scatter = false, forcedPart = Some(BodyPart.Head)))
  }

  private def isFallingObject(source: DamageSource): Boolean = {
    source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK) ||
    source.is(DamageTypes.FALLING_STALACTITE)
  }

  /** Piercing projectiles (arrow/trident/mob projectile) wound; spit, wind charges and other
    * harmless projectiles are ignored.
    */
  private def projectileProfile(source: DamageSource): Option[Wound] = {
    val pierces =
      source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT) ||
        source.is(DamageTypes.MOB_PROJECTILE)
    Option.when(pierces)(Wound(of(CasualtiesBelowConfig.PierceWound), scatter = false))
  }

  /** Evoker fangs pierce; witch potions and guardian beams leave no physical wound. */
  private def magicProfile(source: DamageSource): Option[Wound] = source.getDirectEntity match {
    case _: EvokerFangs => Some(Wound(of(CasualtiesBelowConfig.PierceWound), scatter = false))
    case _              => None
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
