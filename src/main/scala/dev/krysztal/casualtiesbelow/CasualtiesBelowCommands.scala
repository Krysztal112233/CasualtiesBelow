package dev.krysztal.casualtiesbelow

import java.lang.Boolean
import java.lang.Double
import java.lang.Integer

import scala.jdk.CollectionConverters.*

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
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

import dev.krysztal.casualtiesbelow.adrenaline.Adrenaline
import dev.krysztal.casualtiesbelow.api.body.BodyComponent
import dev.krysztal.casualtiesbelow.api.body.BodyPart
import dev.krysztal.casualtiesbelow.api.body.CasualtiesBelowComponents
import dev.krysztal.casualtiesbelow.api.body.LimbStats
import dev.krysztal.casualtiesbelow.api.body.VitalsComponent
import dev.krysztal.casualtiesbelow.config.CasualtiesBelowConfig
import dev.krysztal.casualtiesbelow.pain.PainShock
import dev.krysztal.casualtiesbelow.progression.ConsciousnessProgression

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
    SimpleCommandExceptionType(Component.literal("Unknown body part"))
  private val UnknownStat =
    SimpleCommandExceptionType(Component.literal("Unknown limb stat"))

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
      "discomfort"
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
      DoubleArgumentType.doubleArg(0.0, LimbStats.MaxValue),
      classOf[Double]
    ) { (s, v) => s.muscleHealth = v },
    valueBranch(
      "skin_integrity",
      DoubleArgumentType.doubleArg(0.0, LimbStats.MaxValue),
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
      DoubleArgumentType.doubleArg(0.0, LimbStats.MaxValue),
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
      DoubleArgumentType.doubleArg(0.0, LimbStats.MaxValue),
      classOf[Double]
    ) { (s, v) => s.pain = v }
  )

  private def valueBranch[V](
      name: String,
      argType: ArgumentType[V],
      valueClass: Class[V]
  )(mutate: (LimbStats, V) => Unit): LiteralArgumentBuilder[CommandSourceStack] = {
    Commands
      .literal(name)
      .`then`(
        Commands.argument("value", argType).executes { ctx =>
          val value = ctx.getArgument("value", valueClass)
          mutateTargets(ctx) { (player, comp, part) =>
            val stats = comp.stats(part).copy()
            mutate(stats, value)
            comp.setStats(part, stats)
            CasualtiesBelowComponents.Body.sync(player)
            ctx.getSource.sendSuccess(
              () =>
                Component.literal(
                  s"${player.getName.getString} ${part.id}.$name = ${statValue(stats, name)}"
                ),
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
      set: (LimbStats, V) => Unit,
      clear: LimbStats => Unit
  ): LiteralArgumentBuilder[CommandSourceStack] = {
    valueBranch(name, argType, valueClass)(set).`then`(
      Commands.literal("clear").executes { ctx =>
        mutateTargets(ctx) { (player, comp, part) =>
          val stats = comp.stats(part).copy()
          clear(stats)
          comp.setStats(part, stats)
          CasualtiesBelowComponents.Body.sync(player)
          ctx.getSource.sendSuccess(
            () => Component.literal(s"${player.getName.getString} ${part.id}.$name = none"),
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
            () => Component.literal(s"$name ${part.id}.$s = $value"),
            false
          )
        case None =>
          src.sendSuccess(() => Component.literal(s"$name ${part.id}:"), false)
          StatNames.foreach { s =>
            src.sendSuccess(() => Component.literal(s"  $s = ${statValue(stats, s)}"), false)
          }
      }
    }
    players.size
  }

  private def mutateTargets(
      ctx: CommandContext[CommandSourceStack]
  )(action: (ServerPlayer, BodyComponent, BodyPart) => Unit): Int = {
    val part = getPart(ctx)
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    players.foreach { player => action(player, CasualtiesBelowComponents.Body.get(player), part) }
    players.size
  }

  private def recoverTargets(ctx: CommandContext[CommandSourceStack]): Int = {
    val players = EntityArgument.getPlayers(ctx, "targets").asScala.toList
    players.foreach { player =>
      CasualtiesBelowComponents.reset(player)
      ctx.getSource.sendSuccess(
        () => Component.literal(s"Fully recovered ${player.getName.getString}"),
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
      val vitals = CasualtiesBelowComponents.Vitals.get(player)
      val name = player.getName.getString
      stat match {
        case Some(s) =>
          src.sendSuccess(
            () => Component.literal(s"$name $s = ${vitalsValue(vitals, s)}"),
            false
          )
        case None =>
          src.sendSuccess(() => Component.literal(s"$name vitals:"), false)
          VitalsStatNames.foreach { s =>
            src.sendSuccess(
              () => Component.literal(s"  $s = ${vitalsValue(vitals, s)}"),
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
      val vitals = CasualtiesBelowComponents.Vitals.get(player)
      name match {
        case "immune_health" =>
          vitals.immuneHealth = value.min(CasualtiesBelowConfig.MaxImmuneHealth.get())
        case "consciousness" =>
          ConsciousnessProgression.applyAuthoritativeEdit(player, vitals, value)
        case "pain_shock_load" =>
          PainShock.applyAuthoritativeEdit(vitals, value)
        case "adrenaline" =>
          Adrenaline.applyAuthoritativeEdit(vitals, value)
          PainShock.reconcileAfterAdrenalineEdit(vitals)
        case "blood_oxygen" =>
          vitals.bloodOxygen = value.min(VitalsComponent.MaxBloodOxygen)
        case "blood_volume" =>
          vitals.bloodVolume = value.min(CasualtiesBelowConfig.MaxBloodVolume.get())
        case "sepsis" =>
          vitals.sepsis = value.min(CasualtiesBelowConfig.MaxSepsis.get())
        case "discomfort" =>
          vitals.discomfort = value.min(CasualtiesBelowConfig.MaxDiscomfort.get())
      }
      if (name != "consciousness") {
        ConsciousnessProgression.reconcileAfterEdit(player, vitals)
      }
      CasualtiesBelowComponents.Vitals.sync(player)
      src.sendSuccess(
        () =>
          Component.literal(s"${player.getName.getString} $name = ${vitalsValue(vitals, name)}"),
        false
      )
    }
    players.size
  }

  private def vitalsValue(vitals: VitalsComponent, stat: String): String = stat match {
    case "immune_health"          => f"${vitals.immuneHealth}%.1f"
    case "consciousness"          => f"${vitals.consciousness}%.1f"
    case "pain_shock_load"        => f"${vitals.painShockLoad}%.1f"
    case "pain_shock_stage"       => vitals.painShockStage.id
    case "adrenaline"             => f"${vitals.adrenaline}%.1f"
    case "adrenaline_grace_ticks" => vitals.adrenalineGraceTicks.toString
    case "blood_oxygen"           => f"${vitals.bloodOxygen}%.1f"
    case "blood_volume"           => f"${vitals.bloodVolume}%.1f mL"
    case "sepsis"                 => f"${vitals.sepsis}%.1f"
    case "discomfort"             => f"${vitals.discomfort}%.1f"
    case "unconscious"            => vitals.unconscious.toString
    case _                        => throw UnknownStat.create()
  }

  private def getPart(ctx: CommandContext[CommandSourceStack]): BodyPart = {
    val name = StringArgumentType.getString(ctx, "part")
    BodyPart.byId.getOrElse(name, throw UnknownPart.create())
  }

  private def statValue(stats: LimbStats, stat: String): String = stat match {
    case "muscle_health"           => f"${stats.muscleHealth}%.1f"
    case "skin_integrity"          => f"${stats.skinIntegrity}%.1f"
    case "dislocated"              => stats.dislocated.toString
    case "fracture_recovery_ticks" =>
      stats.fractureRecoveryTicks.map(t => s"$t ticks").getOrElse("none")
    case "infection_progress" =>
      stats.infectionProgress.map(p => f"$p%.1f").getOrElse("none")
    case "external_bleeding_rate" => f"${stats.externalBleedingRate}%.2f mL/tick"
    case "pain"                   => f"${stats.pain}%.1f"
    case _                        => throw UnknownStat.create()
  }
}
