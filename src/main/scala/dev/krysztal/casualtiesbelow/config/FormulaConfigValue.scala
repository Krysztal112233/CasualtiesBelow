package dev.krysztal.casualtiesbelow.config

import java.util.function.Predicate

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dev.krysztal.casualtiesbelow.CasualtiesBelow

import com.ezylang.evalex.Expression
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue

/** A self-registering config value whose string content is an EvalEx formula
  * (https://github.com/ezylang/EvalEx).
  *
  * Constructing an instance registers the value with the given [[ModConfigSpec.Builder]] and
  * encapsulates declaration, validation, compilation, and caching in one place: the compiled
  * [[Expression]] is derived from the configured source text, compiled on first access and whenever
  * the underlying config value changes (e.g. after the file is hot-reloaded), so repeated
  * evaluations only pay the cost of expression evaluation, never recompilation.
  *
  * The default formula is compiled eagerly at construction, so a broken default fails fast during
  * config setup. If evaluation of the configured formula fails at runtime (e.g. a domain error like
  * a negative square root), the default formula is evaluated instead.
  *
  * The cached expression is mutated with variable values on every evaluation and is not
  * thread-safe; call sites must stay on the server thread.
  */
final class FormulaConfigValue(
    builder: ModConfigSpec.Builder,
    path: String,
    default: String,
    val variables: Seq[String],
    comment: Seq[String] = Seq.empty
) {

  // Declared before `spec`: Scala initializes fields in declaration order, and `spec`'s
  // `builder.define(...)` call reads `validator` eagerly during construction.
  private val validator: Predicate[Object] = (value: Object) =>
    value match {
      case source: String =>
        Try {
          FormulaConfigValue.compile(source, variables).evaluate()
        } match {
          case Success(_) => true
          case Failure(e) =>
            CasualtiesBelow.Logger.warn(
              "Unable to parse expression '{}' for config '{}': {}",
              source,
              path,
              e.getMessage
            )
            false
        }
      case _ => false
    }

  /** The underlying string config value, holding the formula source text. */
  val spec: ConfigValue[String] = {
    if (comment.nonEmpty) {
      builder.comment(comment*)
    }
    builder.define(path, default, validator)
  }

  /** Eagerly compiled at construction so a broken default fails fast. */
  private val defaultExpression: Expression = FormulaConfigValue.compile(default, variables)

  @volatile private var cached: (String, Expression) = (default, defaultExpression)

  /** The compiled expression for the current config value, recompiling on change. */
  def expression(): Expression = {
    val src = spec.get()
    cached match {
      case (cachedSrc, expr) if cachedSrc == src => expr
      case _                                     => {
        val expr = FormulaConfigValue.compile(src, variables)
        cached = (src, expr)
        expr
      }
    }
  }

  /** Evaluates the formula with the given variable values, in declaration order. If evaluation of
    * the configured formula fails at runtime (e.g. a domain error like a negative square root), the
    * default formula is evaluated instead.
    */
  def evaluate(values: Double*): Double = {
    require(
      values.length == variables.length,
      s"expected ${variables.length} values (${variables.mkString(", ")}), got ${values.length}"
    )
    Try(evaluateWith(expression(), values))
      .getOrElse(evaluateWith(defaultExpression, values))
  }

  private def evaluateWith(expression: Expression, values: Seq[Double]): Double = {
    variables.lazyZip(values).foreach { (name, value) =>
      expression.`with`(name, value)
    }
    expression.evaluate().getNumberValue().doubleValue()
  }
}

object FormulaConfigValue {

  /** Compiles a formula, binding dummy values to all declared variables so that parsing can resolve
    * them.
    */
  def compile(source: String, variables: Seq[String]): Expression = {
    val expression = new Expression(source)
    variables.foreach(name => expression.`with`(name, 1.0))
    expression
  }
}
