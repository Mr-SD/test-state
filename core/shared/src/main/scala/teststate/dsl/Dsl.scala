package teststate.dsl

import acyclic.file
import teststate.core._
import teststate.data._
import teststate.run.Test
import teststate.typeclass._
import CoreExports._
import Dsl.{Types, ActionB}
import Types.CheckShape

object Dsl {
  def apply[F[_]: ExecutionModel, R, O, S, E] =
    new Dsl[F, R, O, S, E]

  @inline def sync[R, O, S, E] =
    apply[Id, R, O, S, E]

  import scala.concurrent._
  @inline def future[R, O, S, E](implicit ec: ExecutionContext) =
    apply[Future, R, O, S, E]

  // TODO Rename DSL types like Point1, Around1, etc
  trait Types[F[_], R, O, S, E] {
    final type OS          = teststate.data.OS[O, S]
    final type ROS         = teststate.data.ROS[R, O, S]
    final type NameFn      = teststate.data.NameFn[OS]
    final type Point1      = Points[O, S, E]
    final type Around1     = Arounds[O, S, E]
    final type Check       = Invariants[O, S, E]
    final type Action      = Actions[F, R, O, S, E]
    final type Action1     = teststate.core.Action.Single[F, R, O, S, E]
    final type ActionFn    = ROS => teststate.core.Action.Prepared[F, O, S, E]
    final type TestContent = teststate.run.TestContent[F, R, O, S, E]
    final type Test        = teststate.run.Test[F, R, O, S, E]
  }

  implicit def sadfhasdlfkj[F[_], R, O, S, E](b: Dsl.ActionB[F, R, O, S, E]) = b.noStateUpdate
  final class ActionB[F[_], R, O, S, E](actionName: => String)(implicit EM: ExecutionModel[F]) extends Types[F, R, O, S, E] {

    private def build(fn: (ROS => F[Option[E]]) => ActionFn): ActionB2[F, R, O, S, E] =
      new ActionB2(act => Action.Single[F, R, O, S, E](
        actionName,
        fn(act), Sack.empty))

    def updateState(nextState: S => S) =
      updateStateO(s => _ => nextState(s))

    def updateStateO(nextState: S => O => S) =
      build(act => i => Some(() =>
        EM.map(act(i))(Or.liftLeft(_, o => Right(nextState(i.state)(o))))
      ))

    def updateState2(f: S => E Or S) =
      build(act => i => Some(() =>
        EM.map(act(i))(Or.liftLeft(_) >> f(i.state).map(s => Function const Right(s)))
      ))

    def noStateUpdate =
      updateStateO(s => _ => s)
  }

  final class ActionB2[F[_], R, O, S, E](build: (ROS[R, O, S] => F[Option[E]]) => Action.Single[F, R, O, S, E])(implicit EM: ExecutionModel[F]) {

    def act[U](f: ROS[R, O, S] => F[U]) =
      build(f.andThen(EM.map(_)(_ => None)))

    def actTry(f: ROS[R, O, S] => F[Option[E]]) =
      build(f)
  }
}

final class Dsl[F[_], R, O, S, E](implicit EM: ExecutionModel[F]) extends Types[F, R, O, S, E] {

  private def sack1[A](a: A) =
    Sack.Value(Right(a))

  private def sackE(ne: NamedError[E]) =
    Sack.Value(Left(ne))

  def point(name: NameFn, test: OS => Option[E]): Point1 =
    sack1(Point(name, Tri failedOption test(_)))

  def around[A](name: NameFn, before: OS => A)(test: (OS, A) => Option[E]): Around1 =
    sack1(Around.Delta(Around.DeltaA(name, os => Passed(before(os)), test)))

  private def strErrorFn(implicit ev: String =:= E): Any => E = _ => ""
  private def strErrorFn2(implicit ev: String =:= E): (Any, Any) => E = (_,_) => ""


  def test(name: NameFn, testFn: OS => Boolean)(implicit ev: String =:= E): Point1 =
    test(name, testFn, strErrorFn)

  def test(name: NameFn, testFn: OS => Boolean, error: OS => E): Point1 =
    point(name, os => if (testFn(os)) None else Some(error(os)))

  def testAround(name: NameFn, testFn: (OS, OS) => Boolean)(implicit ev: String =:= E): Around1 =
    testAround(name, testFn, strErrorFn2)

  def testAround(name: NameFn, testFn: (OS, OS) => Boolean, error: (OS, OS) => E): Around1 =
    around(name, identity)((x, y) => if (testFn(x, y)) None else Some(error(x, y)))

  def choose[C[-_, _]](name: Name, f: OS => CheckShape[C, O, S, E]): CheckShape[C, O, S, E] =
    Sack.CoProduct(name, f)

  def chooseE[C[-_, _]](name: Name, f: OS => E Or CheckShape[C, O, S, E]): CheckShape[C, O, S, E] =
    Sack.CoProduct(name, f(_).recover(e => sackE(NamedError(name, e))))

  def focus(focusName: => String) =
    new Focus(focusName)

  final class Focus(focusName: => String) {
    // TODO Make implicit
    def value[A: Show](f: OS => A) =
      new FocusValue(focusName, f)

    def collection[T[x] <: TraversableOnce[x], A](f: OS => T[A]) =
      new FocusColl(focusName, f)

    def obsAndState[A: Show](fo: O => A, fs: S => A) =
      new ObsAndState[A](focusName, fo, fs)

    def compare[A: Show](actual: OS => A, expect: OS => A) =
      new BiFocus[A](focusName, fa = actual, fe = expect)
  }

  // ===================================================================================================================

  def emptyAction: Action =
    Action.empty

  def emptyAround: Around1 =
    Sack.empty

  def emptyInvariant: Check =
    Sack.empty

  def emptyTest(implicit r: Recover[E]): TestContent =
    Test(emptyAction)(EM, r)

  def action(actionName: => String) =
    new ActionB[F, R, O, S, E](actionName)

  // ===================================================================================================================

  final class FocusValue[A](focusName: => String, focusFn: OS => A)(implicit showA: Show[A]) {

    def map[B: Show](f: A => B): FocusValue[B] =
      new FocusValue(focusName, f compose focusFn)

    private def suffix(desc: String): String => String =
      _ + " " + desc

    def test(descSuffix: String, testFn: A => Boolean)(implicit ev: String =:= E): Point1 =
      test(suffix(descSuffix), testFn)

    def test(descSuffix: String, testFn: A => Boolean, error: A => E): Point1 =
      test(suffix(descSuffix), testFn, error)

    def test(desc: String => String, testFn: A => Boolean)(implicit ev: String =:= E): Point1 =
      test(desc, testFn, strErrorFn)

    def test(desc: String => String, testFn: A => Boolean, error: A => E): Point1 =
      Dsl.this.test(
        desc(focusName),
        testFn compose focusFn,
        error compose focusFn)

    def testAround(descSuffix: String, testFn: (A, A) => Boolean)(implicit ev: String =:= E): Around1 =
      testAround(suffix(descSuffix), testFn)

    def testAround(descSuffix: String, testFn: (A, A) => Boolean, error: (A, A) => E): Around1 =
      testAround(suffix(descSuffix), testFn, error)

    def testAround(desc: String => String, testFn: (A, A) => Boolean)(implicit ev: String =:= E): Around1 =
      testAround(desc, testFn, strErrorFn2)

    def testAround(desc: String => String, testFn: (A, A) => Boolean, error: (A, A) => E): Around1 =
      around(desc(focusName), focusFn)((os, a1) => {
        val a2 = focusFn(os)
        if (testFn(a1, a2)) None else Some(error(a1, a2))
      })

    def assert: AssertOps = new AssertOps(true)
    def assert(positive: Boolean): AssertOps = new AssertOps(positive)

    final class AssertOps(positive: Boolean) {

      def not = new AssertOps(!positive)

      def equal(expect: A)(implicit e: Equal[A], f: SomethingFailures[A, E]): Point1 =
        point(
          NameUtils.equal(focusName, positive, expect),
          i => f.expectMaybeEqual(positive, ex = expect, actual = focusFn(i)))

      def equalF(expect: OS => A)(implicit e: Equal[A], f: SomethingFailures[A, E]): Point1 =
        point(
          NameFn(NameUtils.equalFn(focusName, positive, expect)),
          i => f.expectMaybeEqual(positive, ex = expect(i), actual = focusFn(i)))

      def beforeAndAfter(before: A, after: A)(implicit e: Equal[A], f: SomethingFailures[A, E]): Around1 =
        equal(before).before & equal(after).after

      def beforeAndAfterF(before: OS => A, after: OS => A)(implicit e: Equal[A], f: SomethingFailures[A, E]): Around1 =
        equalF(before).before & equalF(after).after

      private def mkAround(name: NameFn, f: (A, A) => Option[E]) =
        around(name, focusFn)((os, a) => f(a, focusFn(os)))

      def changesTo(expect: A => A)(implicit e: Equal[A], f: SomethingFailures[A, E]): Around1 =
        mkAround(
          NameFn(NameUtils.equalFn(focusName, positive, expect compose focusFn)),
          (a1, a2) => f.expectMaybeEqual(positive, ex = expect(a1), actual = a2))

      def changeOccurs(implicit e: Equal[A], f: SomethingFailures[A, E]) =
        not.changesTo(identity)
    }
  } // FocusValue

  // ===================================================================================================================

  final class FocusColl[C[X] <: TraversableOnce[X], A](focusName: => String, focusFn: OS => C[A]) {

    def map[D[X] <: TraversableOnce[X], B: Show](f: C[A] => D[B]): FocusColl[D, B] =
      new FocusColl(focusName, f compose focusFn)

    def value(implicit s: Show[C[A]]) =
      new FocusValue[C[A]](focusName, focusFn)

    def assert: AssertOps = new AssertOps(true)
    def assert(positive: Boolean): AssertOps = new AssertOps(positive)

    final class AssertOps(positive: Boolean) {
      def not = new AssertOps(!positive)

      import CollectionAssertions._

      def distinct(implicit sa: Show[A], ev: Distinct.Failure[A] => E) = {
        val d = Distinct(positive)
        point(
          d.name(focusName),
          os => d(focusFn(os)).map(ev))
      }

      def containsAll[B <: A](queryNames: => String, query: OS => Set[B])(implicit sb: Show[B], ev: ContainsAll.Failure[B] => E) = {
        val d = ContainsAll(positive)
        point(
          d.name(focusName, queryNames),
          os => d(focusFn(os), query(os)).map(ev))
      }

      def containsAny[B >: A](queryNames: => String, query: OS => Set[B])(implicit sb: Show[B], ev: ContainsAny.Failure[B] => E) = {
        val d = ContainsAny(positive)
        point(
          d.name(focusName, queryNames),
          os => d(focusFn(os), query(os)).map(ev))
      }

      def containsNone[B >: A](queryNames: => String, query: OS => Set[B])(implicit sb: Show[B], ev: ContainsAny.Failure[B] => E) =
        not.containsAny(queryNames, query)

      def containsOnly[B >: A](queryNames: => String, query: OS => Set[B])(implicit sa: Show[A], ev: ContainsOnly.Failure[A] => E) = {
        val d = ContainsOnly(positive)
        point(
          d.name(focusName, queryNames),
          os => d(focusFn(os), query(os)).map(ev))
      }

      def existenceOfAll(allName: => String, expect: OS => Boolean, all: OS => Set[A])
                        (implicit sa: Show[A], ev1: ContainsAny.FoundSome[A] => E, ev2: ContainsAll.Missing[A] => E) =
        point(
          NameFn(_.fold[Name](s"$focusName: Existence of $allName.")(os =>
            ExistenceOfAll.name(expect(os), focusName, allName))),
          os => ExistenceOfAll(expect(os), focusFn(os), all(os)).map(_.fold(ev1, ev2)))

      def equal(expect: OS => TraversableOnce[A])(implicit eq: Equal[A], sa: Show[A], ev: EqualIncludingOrder.Failure[A] => E) = {
        val d = EqualIncludingOrder(positive)
        point(
          NameFn(NameUtils.equalFn(focusName, positive, expect)(sa.coll[TraversableOnce])),
          os => d(source = focusFn(os), expect = expect(os)).map(ev))
      }

      def equalIgnoringOrder(expect: OS => TraversableOnce[A])(implicit sa: Show[A], ev: EqualIgnoringOrder.Failure[A] => E) = {
        val d = EqualIgnoringOrder(positive)
        point(
          NameFn(NameUtils.equalFn(focusName, positive, expect)(sa.coll[TraversableOnce])
            .andThen(_.map(_ + " (ignoring order)"))),
          os => d(source = focusFn(os), expect = expect(os)).map(ev))
      }

      def equalConst(expect: A*)(implicit eq: Equal[A], sa: Show[A], ev: EqualIncludingOrder.Failure[A] => E) =
        equal(Function const expect)(eq, sa, ev)

      def equalIgnoringOrderConst(expect: A*)(implicit sa: Show[A], ev: EqualIgnoringOrder.Failure[A] => E) =
        equalIgnoringOrder(Function const expect)(sa, ev)

    }
  }


  // ===================================================================================================================

  sealed class BiFocus[A](focusName: => String, fa: OS => A, fe: OS => A)(implicit showA: Show[A]) {

    def map[B: Show](f: A => B): BiFocus[B] =
      new BiFocus(focusName, f compose fa, f compose fe)

    def assert: AssertOps = new AssertOps(true)
    def assert(positive: Boolean): AssertOps = new AssertOps(positive)

    final class AssertOps(positive: Boolean) {

      def not = new AssertOps(!positive)

      def equal(implicit e: Equal[A], f: SomethingFailures[A, E]): Point1 =
        point(
          NameFn(NameUtils.equalFn(focusName, positive, i => fe(i))),
          os => f.expectMaybeEqual(positive, ex = fe(os), actual = fa(os)))
    }
  }

  final class ObsAndState[A](focusName: => String, fo: O => A, fs: S => A)(implicit showA: Show[A])
      extends BiFocus(focusName, fa = i => fs(i.state), fe = i => fo(i.obs)) {

    def obs   = new FocusValue(focusName, os => fo(os.obs))
    def state = new FocusValue(focusName, os => fs(os.state))

    override def map[B: Show](f: A => B): ObsAndState[B] =
      new ObsAndState(focusName, f compose fo, f compose fs)
  }
}

// TODO Runner should print state & obs on failure, each assertion needn't. It should print S and/or S' depending on the type of check (pre and/or post) that failed.

/*
object Exampe {
  implicit def sa[A]: Show[A] = ???
  implicit def ea[A]: Equal[A] = ???
  val * = Dsl.sync[Unit, Unit, Unit, String]

  *.focus("stuff").value(_ => 0).assert.changeOccurs

  *.focus("stuff").value(_ => 0).assert.equal(3).before
  *.focus("stuff").value(_ => 0).assert.beforeAndAfter(1, 2)
  *.focus("stuff").value(_ => 0).assert.changesTo(_ + 1)

  *.focus("stuff").value(_ => 0).assert.not.equal(3).before
  *.focus("stuff").value(_ => 0).assert.not.beforeAndAfter(1, 2)
  *.focus("stuff").value(_ => 0).assert.not.changesTo(_ + 1)

//  *.focus("stuff").collection(_ => List(1)).
}
*/