package teststate.cp3

import teststate._
import language.reflectiveCalls
import scala.annotation.implicitNotFound

abstract class AbstractTest {
  trait A
  trait O {
    def bool: Boolean
  }
  trait O2
  trait S
  trait S2
  trait E
  trait E2
  type OS1 = teststate.OS[O, S]
  def o12: O => O2
  def o21: O2 => O
  def s12: S => S2
  def s21: S2 => S
  def e12: E => E2
  def e21: E2 => E

//  def pl: (E2 Or OS[O2, S2]) => (E Or OS[O, S])
//  def pr[C[-a, +b] <: Check[a, b]]: C[OS[O, S], E] => C[OS[O2, S2], E2]
//  def prA: Check.Delta.Aux[OS[O, S], E, A] => Check.Delta.Aux[OS[O2, S2], E2, A]

  @implicitNotFound(msg = "\n\nExpected: ${From}\n  Actual: ${To}\n\n")
  sealed abstract class =:=[From, To] extends (From => To) with Serializable
  private[this] final val singleton_=:= = new =:=[Any,Any] { def apply(x: Any): Any = x }
  object =:= {
     implicit def tpEquals[A]: A =:= A = singleton_=:=.asInstanceOf[A =:= A]
  }

  def test[A] = new {
    def apply[R](f: A => R) = new {
      def expect[E] (implicit ev: R =:= E) = ()
      def expectSelf(implicit ev: R =:= A) = ()
    }
  }

  def test2[A, B] = new {
    def apply[R](f: (A, B) => R) = new {
      def expect[E](implicit ev: R =:= E) = ()
      def expectA  (implicit ev: R =:= A) = ()
      def expectB  (implicit ev: R =:= B) = ()
    }
  }
}

abstract class Test extends AbstractTest {
  import Types._
  import CheckOps.Instances._
  import CheckOps.ToOps._
  import Conditional.Implicits._

  // mapO
  test[Points    [O, S, E]](_ mapO o21).expect[Points    [O2, S, E]]
  test[Arounds   [O, S, E]](_ mapO o21).expect[Arounds   [O2, S, E]]
  test[Invariants[O, S, E]](_ mapO o21).expect[Invariants[O2, S, E]]

  // when
  test[Points    [O, S, E]](_.when(_.obs.bool)).expect[Points    [O, S, E]]
  test[Arounds   [O, S, E]](_.when(_.obs.bool)).expect[Arounds   [O, S, E]]
  test[Invariants[O, S, E]](_.when(_.obs.bool)).expect[Invariants[O, S, E]]

}
