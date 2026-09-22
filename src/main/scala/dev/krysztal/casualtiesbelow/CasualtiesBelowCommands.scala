package dev.krysztal.casualtiesbelow

import java.lang.Boolean
import java.lang.Double
import java.lang.Integer

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.limb.BodyPart
import dev.krysztal.casualtiesbelow.api.body.limb.LimbSnapshot
import dev.krysztal.casualtiesbelow.component.BodyMutations
import dev.krysztal.casualtiesbelow.component.MutableLimbState
import dev.krysztal.casualtiesbelow.component.PhysiologyReset
import dev.krysztal.casualtiesbelow.component.VitalsComponentImpl
import dev.krysztal.casualtiesbelow.component.VitalsMutations
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.internal.extension.ComponentExtensions.*
import dev.krysztal.casualtiesbelow.internal.extension.PlayerExtensions.*
import dev.krysztal.casualtiesbelow.physiology.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.physiology.consciousness.Consciousness
import dev.krysztal.casualtiesbelow.physiology.pain.PainShock

/** Debug/admin commands for inspecting and editing body and vitals state:
  *
  *   - `/casualtiesbelow body <targets> get <part> [stat]`
  *   - `/casualtiesbelow body <targets> set <part> <stat> <value>`
  *   - `/casualtiesbelow body <targets> set <part> <fracture_recovery_ticks|infection_progress>
  *     clear`
  *   - `/casualtiesbelow vitals <targets> get [stat]`
  *   - `/casualtiesbelow vitals <targets> set <stat> <value>`
  *   - `/casualtiesbelow recover <targets>`
  */
object CasualtiesBelowCommands {
  private val UnknownPart =
    SimpleCommandExceptionType("Unknown body part".literal)
  private val UnknownStat =
    SimpleCommandExceptionType("Unknown limb stat".literal)

  private val StatNames = List(
    "muscle_health",
    "skin_integrity",
    "dislocated",
    "fracture_recovery_ticks",
    "infection_progress",
    "external_bleeding_rate",
    "pain"
  )

  private val VitalsEditableStatNames =
    List(
      "immune_health",
      "consciousness",
      "pain_shock_load",
      "adrenaline",
      "blood_oxygen",
      "blood_volume",
      "sepsis",
      "discomfort",
      "dirtiness",
      "body_temperature",
      "wetness",
      "opioid_level",
      "opioid_dependence"
    )
  private val VitalsStatNames =
    VitalsEditableStatNames ++ List(
      "pain_shock_stage",
      "adrenaline_grace_ticks",
      "unconscious"
    )

  def register(): Unit = {
    CommandRegistrationCallback.EVENT.register { (dispatcher, _, _) =>
      val get = Commands
        .literal("get")
        .`then`(
          partArg()
            .executes(ctx => getStats(ctx, None))
            .`then`(
              Commands
                .argument("stat", StringArgumentType.word())
                .suggests((_, b) => SharedSuggestionProvider.suggest(StatNames.asJava, b))
                .executes(ctx => getStats(ctx, Some(StringArgumentType.getString(ctx, "stat"))))
            )
        )

      val setTargets = partArg()
      setBranches().foreach { branch => setTargets.`then`(branch) }
      val set = Commands.literal("set").`then`(setTargets)

      val body = Commands
        .literal("body")
        .`then`(
          Commands.argument("targets", EntityArgument.players()).`then`(get).`then`(set)
        )

      val vitalsStat = Commands
        .argument("stat", StringArgumentType.word())
        .suggests((_, b) => SharedSuggestionProvider.suggest(VitalsStatNames.asJava, b))
      val vitals = Commands
        .literal("vitals")
        .`then`(
          Commands
            .argument("targets", EntityArgument.players())
            .`then`(
              Commands
                .literal("get")
                .executes(ctx => getVitals(ctx, None))
                .`then`(
                  vitalsStat.executes(ctx =>
                    getVitals(ctx, Some(StringArgumentType.getString(ctx, "stat")))
                  )
                )
            )
            .`then`(
              Commands
                .literal("set")
                .`then`(
                  Commands
                    .argument("stat", StringArgumentType.word())
                    .suggests((_, b) =>
                      SharedSuggestionProvider.suggest(VitalsEditableStatNames.asJava, b)
                    )
                    .`then`(
                      Commands
                        .argument("value", DoubleArgumentType.doubleArg(0.0))
                        .executes(setVitals)
                    )
                )
            )
        )

      val recover = Commands
        .literal("recover")
        .`then`(
          Commands
            .argument("targets", EntityArgument.players())
            .executes(recoverTargets)
        )

      dispatcher.register(
        Commands
          .literal("casualtiesbelow")
          .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
          .`then`(body)
          .`then`(vitals)
          .`then`(recover)
      )
    }
  }

  private def partArg() = {
    Commands
      .argument("part", StringArgumentType.word())
      .suggests((_, b) =>
        SharedSuggestionProvider.suggest(BodyPart.values.map(_.id).toSeq.asJava, b)
      )
  }

  private def setBranches(): List[LiteralArgumentBuilder[CommandSourceStack]] = List(
    valueBranch(
      "muscle_health",
      DoubleArgumentType.doubleArg(0.0, LimbSnapshot.MaxValue),
      classOf[Double]
    ) { (s, v) => s.muscleHealth = v },
    valueBranch(
      "skin_integrity",
      DoubleArgumentType.doubleArg(0.0, LimbSnapshot.MaxValue),
      classOf[Double]
    ) { (s, v) => s.skinIntegrity = v },
    valueBranch("dislocated", BoolArgumentType.bool(), classOf[Boolean]) { (s, v) =>
      s.dislocated = v
    },
    clearableBranch(
      "fracture_recovery_ticks",
      IntegerArgumentType.integer(0),
      classOf[Integer]
    )(
      { (s, v) => s.fractureRecoveryTicks = Some(v.toInt) },
      { s => s.fractureRecoveryTicks = None }
    ),
    clearableBranch(
      "infection_progress",
      DoubleArgumentType.doubleArg(0.0, LimbSnapshot.MaxValue),
      classOf[Double]
    )(
      { (s, v) => s.infectionProgress = Some(v.doubleValue) },
      { s => s.infectionProgress = None }
    ),
    valueBranch(
      "external_bleeding_rate",
      DoubleArgumentType.doubleArg(0.0),
      classOf[Double]
    ) { (s, v) => s.externalBleedingRate = v },
    valueBranch(
      "pain",
      DoubleArgumentType.doubleArg(0.0, LimbSnapshot.MaxValue),
      classOf[Double]
    ) { (s, v) => s.pain = v }
  )

  private def valueBranch[V](
      name: String,
      argType: ArgumentType[V],
      valueClass: Class[V]
  )(mutate: (MutableLimbState, V) => Unit): LiteralArgumentBuilder[CommandSourceStack] = {
    Commands
      .literal(name)
      .`then`(
        Commands.argument("value", argType).executes { ctx =>
          val value = ctx.getArgument("value", valueClass)
          mutateTargets(ctx) { (player, part) =>
            val result = BodyMutations.mutate(player, part) { state => mutate(state, value) }
            BodyMutations.syncNow(player)
            ctx.getSource.sendSuccess(
              () =>
                s"${player.getName.getString} ${part.id}.$name = ${statValue(result.after, name)}".literal,
              false
            )
          }
        }
      )
  }

  private def clearableBranch[V](
      name: String,
      argType: ArgumentType[V],
      valueClass: Class[V]
  )(
      set: (MutableLimbState, V) => Unit,
      clear: MutableLimbState => Unit
  ): LiteralArgumentBuilder[CommandSourceStack] = {
    valueBranch(name, argType, valueClass)(set).`then`(
      Commands.literal("clear").executes { ctx =>
        mutateTargets(ctx) { (player, part) =>
          BodyMutations.mutate(player, part)(clear)
          BodyMutations.syncNow(player)
          ctx.getSource.sendSuccess(
            () => s"${player.getName.getString} ${part.id}.$name = none".literal,
            false
          )
        }
      }
    )
  }

  private def getStats(ctx: CommandContext[CommandSourceStack], stat: Option[String]): Int = {
    stat.foreach { name =>
      if (!StatNames.contains(name)) throw UnknownStat.create()
    }
    val part = getPart(ctx)
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    val src = ctx.getSource
    players.foreach { player =>
      val stats = CasualtiesBelowComponents.Body.get(player).stats(part)
      val name = player.getName.getString
      stat match {
        case Some(s) =>
          val value = statValue(stats, s)
          src.sendSuccess(
            () => s"$name ${part.id}.$s = $value".literal,
            false
          )
        case None =>
          src.sendSuccess(() => s"$name ${part.id}:".literal, false)
          StatNames.foreach { s =>
            src.sendSuccess(() => s"  $s = ${statValue(stats, s)}".literal, false)
          }
      }
    }
    players.size
  }

  private def mutateTargets(
      ctx: CommandContext[CommandSourceStack]
  )(action: (ServerPlayer, BodyPart) => Unit): Int = {
    val part = getPart(ctx)
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    players.foreach { player => action(player, part) }
    players.size
  }

  private def recoverTargets(ctx: CommandContext[CommandSourceStack]): Int = {
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    players.foreach { player =>
      PhysiologyReset.reset(player)
      ctx.getSource.sendSuccess(
        () => s"Fully recovered ${player.getName.getString}".literal,
        false
      )
    }
    players.size
  }

  private def getVitals(ctx: CommandContext[CommandSourceStack], stat: Option[String]): Int = {
    stat.foreach { name =>
      if (!VitalsStatNames.contains(name)) throw UnknownStat.create()
    }
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    val src = ctx.getSource
    players.foreach { player =>
      val vitals = player.vitals
      val name = player.getName.getString
      stat match {
        case Some(s) =>
          src.sendSuccess(
            () => s"$name $s = ${vitalsValue(vitals, s)}".literal,
            false
          )
        case None =>
          src.sendSuccess(() => s"$name vitals:".literal, false)
          VitalsStatNames.foreach { s =>
            src.sendSuccess(
              () => s"  $s = ${vitalsValue(vitals, s)}".literal,
              false
            )
          }
      }
    }
    players.size
  }

  private def setVitals(ctx: CommandContext[CommandSourceStack]): Int = {
    val name = StringArgumentType.getString(ctx, "stat")
    if (!VitalsEditableStatNames.contains(name)) throw UnknownStat.create()

    val value = DoubleArgumentType.getDouble(ctx, "value")
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    val src = ctx.getSource
    players.foreach { player =>
      val vitals = player.vitals
      name match {
        case "immune_health" =>
          VitalsMutations.setImmuneHealth(vitals, value)
        case "consciousness" =>
          Consciousness.applyAuthoritativeEdit(player, vitals, value)
        case "pain_shock_load" =>
          PainShock.applyAuthoritativeEdit(player, vitals, value)
        case "adrenaline" =>
          Adrenaline.applyAuthoritativeEdit(player, vitals, value)
          PainShock.reconcileAfterAdrenalineEdit(player, vitals)
        case "blood_oxygen" =>
          VitalsMutations.setBloodOxygen(vitals, value)
        case "blood_volume" =>
          VitalsMutations.setBloodVolume(vitals, value)
        case "sepsis" =>
          VitalsMutations.setSepsis(vitals, value)
        case "discomfort" =>
          VitalsMutations.setDiscomfort(vitals, value)
        case "dirtiness" =>
          VitalsMutations.setDirtiness(vitals, value)
        case "body_temperature" =>
          VitalsMutations.setBodyTemperature(vitals, value)
        case "wetness" =>
          VitalsMutations.setWetness(vitals, value)
        case "opioid_level" =>
          VitalsMutations.setOpioidLevel(vitals, value)
        case "opioid_dependence" =>
          VitalsMutations.setOpioidDependence(vitals, value)
      }
      if (name != "consciousness") {
        Consciousness.reconcileAfterEdit(player, vitals)
      }
      VitalsMutations.syncNow(player)
      src.sendSuccess(
        () => s"${player.getName.getString} $name = ${vitalsValue(vitals, name)}".literal,
        false
      )
    }
    players.size
  }

  private def vitalsValue(vitals: VitalsComponentImpl, stat: String): String = stat match {
    case "immune_health"          => f"${vitals.infection.immuneHealth}%.1f"
    case "consciousness"          => f"${vitals.consciousness.level}%.1f"
    case "pain_shock_load"        => f"${vitals.shock.load}%.1f"
    case "pain_shock_stage"       => vitals.shock.stage.id
    case "adrenaline"             => f"${vitals.adrenaline}%.1f"
    case "adrenaline_grace_ticks" => VitalsMutations.adrenalineGraceTicks(vitals).toString
    case "blood_oxygen"           => f"${vitals.circulation.bloodOxygen}%.1f"
    case "blood_volume"           => f"${vitals.circulation.bloodVolume}%.1f mL"
    case "sepsis"                 => f"${vitals.infection.sepsis}%.1f"
    case "discomfort"             => f"${vitals.discomfort}%.1f"
    case "dirtiness"              => f"${vitals.dirtiness}%.1f"
    case "body_temperature"       => f"${vitals.bodyTemperature}%.2f °C"
    case "wetness"                => f"${vitals.wetness}%.2f"
    case "opioid_level"           => f"${vitals.opioidLevel}%.1f"
    case "opioid_dependence"      => f"${vitals.opioidDependence}%.1f"
    case "unconscious"            => vitals.consciousness.unconscious.toString
    case _                        => throw UnknownStat.create()
  }

  private def getPart(ctx: CommandContext[CommandSourceStack]): BodyPart = {
    val name = StringArgumentType.getString(ctx, "part")
    BodyPart.fromId(name).toScala.getOrElse(throw UnknownPart.create())
  }

  private def statValue(stats: LimbSnapshot, stat: String): String = stat match {
    case "muscle_health"           => f"${stats.muscleHealth}%.1f"
    case "skin_integrity"          => f"${stats.skinIntegrity}%.1f"
    case "dislocated"              => stats.dislocated.toString
    case "fracture_recovery_ticks" =>
      if (stats.fractureRecoveryTicks.isPresent)
        s"${stats.fractureRecoveryTicks.getAsInt} ticks"
      else "none"
    case "infection_progress" =>
      if (stats.infectionProgress.isPresent) f"${stats.infectionProgress.getAsDouble}%.1f"
      else "none"
    case "external_bleeding_rate" => f"${stats.externalBleedingRate}%.2f mL/tick"
    case "pain"                   => f"${stats.pain}%.1f"
    case _                        => throw UnknownStat.create()
  }
}
