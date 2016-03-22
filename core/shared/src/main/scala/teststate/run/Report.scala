package teststate.run

// import acyclic.file
import scala.annotation.elidable
import teststate.typeclass.ShowError
import Report._

case class Report[+E](history: History[E], stats: Stats) {

  @inline def failure = history.failure
  @inline def failed = history.failed
  @inline def result = history.result

  @elidable(elidable.ASSERTION)
  def assert[EE >: E]()(implicit as: AssertionSettings, se: ShowError[EE]): Unit =
    history.failureReason(se) match {

      case None =>
        as.onPass.print[EE](this)

      case Some(e) =>
        as.onFail.print[EE](this)
        throw new AssertionError(e)
    }

  def format[EE >: E](implicit as: AssertionSettings, s: ShowError[EE]): String =
    format[EE](if (failed) as.onFail else as.onPass)(s)

  def format[EE >: E](f: Format)(implicit s: ShowError[EE]): String =
    f.format[EE](this)(s) getOrElse ""
}

object Report {

  // Help keep Exports small

  type History[+E] = teststate.run.History[E]
  val  History     = teststate.run.History

  type Format = teststate.run.ReportFormat
  val  Format = teststate.run.ReportFormat

  type Stats = teststate.run.Stats
  val  Stats = teststate.run.Stats


  case class AssertionSettings(onPass: Format, onFail: Format) {
    def silenceOnPass: AssertionSettings =
      copy(onPass = Format.quiet)
  }

  object AssertionSettings {
    def uniform(format: Format): AssertionSettings =
      AssertionSettings(format, format)

    def uncoloured = AssertionSettings(
      onPass = Format.Default.uncoloured.apply,
      onFail = Format.Default.uncoloured.alwaysShowChildren.apply)

    def coloured = AssertionSettings(
      onPass = Format.Default.coloured.apply,
      onFail = Format.Default.coloured.alwaysShowChildren.apply)
  }
}