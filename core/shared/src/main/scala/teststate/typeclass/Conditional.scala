package teststate.typeclass

import acyclic.file
import teststate.data.{Or, Skipped, Tri}

trait Conditional[M, I] {
  def when(m: M, f: I => Boolean): M
}

object Conditional {

  final class Ops[M, I](m: M)(implicit c: Conditional[M, I]) {

    def when(f: I => Boolean): M =
      c.when(m, f)

    def skip: M =
      when(_ => false)
  }

  trait Instances {
    implicit def conditionalFnToTri[I, E, A]: Conditional[I => Tri[E, A], I] =
      new Conditional[I => Tri[E, A], I] {
        override def when(m: I => Tri[E, A], f: I => Boolean) =
          i => if (f(i)) m(i) else Skipped
      }

    implicit def conditionalFnToOption[I, A]: Conditional[I => Option[A], I] =
      new Conditional[I => Option[A], I] {
        override def when(m: I => Option[A], f: I => Boolean) =
          i => if (f(i)) m(i) else None
      }

    implicit def conditionalRight[L, R, I](implicit c: Conditional[R, I]): Conditional[L Or R, I] =
      new Conditional[L Or R, I] {
        override def when(m: L Or R, f: I => Boolean) =
          m.map(c.when(_, f))
      }
  }

  trait ToOps {
    implicit def toConditionalOps[M, I](m: M)(implicit c: Conditional[M, I]): Ops[M, I] =
      new Ops(m)(c)
  }

  trait Implicits extends Instances with ToOps

  object Implicits extends Implicits
}

