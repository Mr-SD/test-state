package teststate.core

import acyclic.file
import teststate.data.Sack
import teststate.typeclass.PolyComposable
import PolyComposable.CanAnd
import Types.CheckShapeA

/**
  * P = Point
  * @ = Around
  * I = Invariant
  * C = Check
  * A = Action
  *
  * P & P = P
  * @ & @ = @
  * I & I = I
  * C & C = I
  */
object CoreComposition {

  trait P0 {

    implicit def checksPolyComposable[C[-_, _], D[-_, _], A, E](implicit
                                                                  c: ToInvariant[CheckShapeA, C],
                                                                  d: ToInvariant[CheckShapeA, D],
                                                                  i: PolyComposable.Mono[CheckShapeA[Invariant, A, E]])
        : PolyComposable[CheckShapeA[C, A, E], CheckShapeA[D, A, E], CheckShapeA[Invariant, A, E]] =
      PolyComposable((fc, fd) => i.compose(c toInvariant fc, d toInvariant fd))
  }

  trait Implicits extends P0 {

    implicit def checksMonoComposable[A, B]: PolyComposable.Mono[Sack[A, B]] =
      PolyComposable(Sack.append)

    implicit def checksCanAnd[C[-_, _], A, B]: CanAnd[CheckShapeA[C, A, B]] = CanAnd
  }

}