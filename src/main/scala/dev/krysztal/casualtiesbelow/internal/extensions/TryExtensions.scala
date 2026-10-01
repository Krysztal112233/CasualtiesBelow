package dev.krysztal.casualtiesbelow.internal.extensions

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dev.krysztal.casualtiesbelow.CasualtiesBelow

/** Failure-handling extensions for [[scala.util.Try]]. */
private[casualtiesbelow] object TryExtensions {

  extension [T](tried: Try[T]) {

    /** `Some(value)` on success; on failure logs [message] as a warning with the error's stack
      * trace attached and returns `None`. For recoverable failures where the context message plus
      * the exception is all the diagnostics needed; call sites that must stay silent or customize
      * the log call keep their own `match` instead.
      */
    def orLogWarn(message: => String): Option[T] = tried match {
      case Success(value) => Some(value)
      case Failure(error) =>
        CasualtiesBelow.Logger.warn(message, error)
        None
    }
  }
}
