package teststate

import scala.annotation.tailrec

sealed trait Result[+Err] {
  def failure: Option[Err]
}
object Result {
  case object Pass extends Result[Nothing] {
    override def failure = None
  }
  case object Skip extends Result[Nothing] {
    override def failure = None
  }
  case class Fail[+Err](error: Err) extends Result[Err] {
    override def failure = Some(error)
  }
}

object Runner {

  trait HalfCheck[O1, S1, O2, S2, Err] {
    type A
    val check: Check.Aux[O1, S1, O2, S2, Err, A]
    val before: A
  }
  def HalfCheck[O1, S1, O2, S2, Err, a](_check: Check.Aux[O1, S1, O2, S2, Err, a])(_before: a): HalfCheck[O1, S1, O2, S2, Err] =
    new HalfCheck[O1, S1, O2, S2, Err] {
      override type A     = a
      override val check  = _check
      override val before = _before
    }

  def run[Ref, Obs, State, Err](action: Action[Ref, Obs, State, Obs, State, Err],
                                invariants: Invariants[Obs, State, Err] = Invariants.empty,
                                invariants2: Checks[Obs, State, Obs, State, Err] = Checks.empty)
                               (initialState: State,
                                ref: Ref)
                               (observe: Ref => Obs): History[Err] = {

    type A = Action[Ref, Obs, State, Obs, State, Err]
    type HS = History.Steps[Err]

    val invariantChecks = invariants.toChecks & invariants2

    case class OMG(obs: Obs, state: State, sos: Some[(Obs, State)], history: HS) {
//      def modHistory(f: HS => HS): OMG =
//        copy(history = f(history))

//      def addStep(s: History.Step[Err]): OMG =
//        copy(history = history :+ s)
    }

    def start(a: A, obs: Obs, state: State, sos: Some[(Obs, State)], history: HS) =
      go(vector1(a), OMG(obs, state, sos, history))

    @tailrec
    def go(queue: Vector[A], omg: OMG): OMG =
      if (queue.isEmpty)
        omg
      else {
        import omg._

//        def addStep(name: String, result: Result[Err], children: History[Err] = History.empty) =
//          history :+ History.Step(name, result, children)

        queue.head match {

          // ==============================================================================
          case Action.Single(nameFn, run, checks) =>
            val name = nameFn(sos)

            def addHistory(result: Result[Err]) =
              omg.copy(history = history :+ History.Step(name, result))

            run(ref, obs, state) match {
              case Some(act) =>

                halfChecks(checks & invariantChecks)(obs, state, sos) match {
                  case Right(hcs) =>

                    act() match {
                      case Right(f) =>
                        val obs2 = observe(ref)
                        val state2 = f(obs2)

                        performChecks(hcs)(_.check name sos, c => c.check.test(obs2, state2, c.before)) match {
                          case None =>
                            val h = History.Step(name, Result.Pass)
                            val omg2 = OMG(obs2, state2, Some((obs2, state2)), history :+ h)
                            go(queue.tail, omg2)
                          case Some(failedStep) =>
                            omg.copy(history = history :+ failedStep(name))
                        }


                      case Left(e) =>
                        addHistory(Result.Fail(e))
                    }

                  case Left(failedStep) =>
                    omg.copy(history = history :+ failedStep(name))
                }

              case None =>
                go(queue.tail, addHistory(Result.Skip))
            }

          // ==============================================================================
          case Action.Group(nameFn, children) =>
            val name = nameFn(sos)
            val omg2 = start(children, obs, state, sos, Vector.empty)
            val h2   = History(omg2.history)
            val omg3 = omg2.copy(history = omg.history :+ History.parent(name, h2))
            if (h2.failure.isDefined)
              omg3
            else
              go(queue.tail, omg3)

          // ==============================================================================
          case Action.Composite(actions) =>
            go(queue.tail ++ actions.toVector, omg)
        }
      }

    History {
      val initialObs = observe(ref)
      val sos = Some((initialObs, initialState))

      val firstSteps: HS = {
        val iv = invariants.toVector
        if (iv.isEmpty)
          Vector.empty
        else {
          val children = iv
            .map { i =>
              val name = i.name(sos)
              val result = i.test(initialObs, initialState).fold[Result[Err]](Result.Pass)(Result.Fail(_))
              History.Step(name, result)
            }
          vector1(History.parent("Initial checks.", History(children)))
        }
      }

      if (firstSteps.exists(_.failed))
        firstSteps
      else {
        val runResults = start(action, initialObs, initialState, sos, Vector.empty).history
        val h = firstSteps ++ runResults
        if (runResults.exists(_.failed))
          h
        else
          h :+ History.Step("All pass.", Result.Pass)
      }
    }
  }

  private def halfChecks[O1, S1, O2, S2, E](checks: Checks[O1, S1, O2, S2, E])(obs: O1, state: S1, sos: Some[(O1, S1)])
  : Either[String => History.Step[E], Vector[HalfCheck[O1, S1, O2, S2, E]]] = {
    val r = Vector.newBuilder[HalfCheck[O1, S1, O2, S2, E]]
    val o = performChecks(checks.toVector)(
      _ name sos,
      c0 => {
        val c = c0.aux
        c.before(obs, state) match {
          case Right(a) => r += HalfCheck(c)(a); None
          case Left(e) => Some(e)
        }
      }
    )
    o match {
      case None => Right(r.result())
      case Some(h) => Left(h)
    }
  }

  private def performChecks[A, E](as: Vector[A])(name: A => String, test: A => Option[E]): Option[String => History.Step[E]] = {
    var failures = List.empty[(Int, E)]

    for (i <- as.indices) {
      val a = as(i)
      test(a) match {
        case None    => ()
        case Some(e) => failures ::= ((i, e))
      }
    }

    if (failures.isEmpty)
      None
    else
      Some {
        val m = failures.toMap
        val history =
          as.indices.iterator.map { i =>
            val a = as(i)
            val n = name(a)
            val r = m.get(i).fold[Result[E]](Result.Pass)(Result.Fail(_))
            History.Step(n, r)
          }.toVector

        val firstFailure = failures.head._2
        History.Step(_, Result Fail firstFailure, History(history))
      }
  }

}