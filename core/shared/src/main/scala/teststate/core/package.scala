package teststate

import teststate.data.{BeforeAfter, NameFn}

package object core {

  @inline implicit class TestateCoreAnyExt[A](private val self: A) extends AnyVal {
    @inline def |>[B](f: A => B): B =
      f(self)
  }

  @inline implicit class TestateNFEBA[A](private val f: NameFn[BeforeAfter[A]] => NameFn[BeforeAfter[A]]) extends AnyVal {
    def thruBefore: NameFn[A] => NameFn[A] =
      n => f(n.cmap(_.before)).cmap(BeforeAfter.same)

    def thruAfter: NameFn[A] => NameFn[A] =
      n => f(n.cmap(_.before)).cmap(BeforeAfter.same)
  }
}
