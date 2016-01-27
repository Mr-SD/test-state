package teststate

import Action.{Composite, NonComposite}

sealed trait Action[F[_], Ref, O, S, Err] {
  type This <: Action[F, Ref, O, S, Err]

  def nonCompositeActions: Vector[NonComposite[F, Ref, O, S, Err]]

  def nameMod(f: (=> String) => String): This

  def addCheck(c: Check.Around[O, S, Err]): This

  def when(f: ROS[Ref, O, S] => Boolean): This

  final def unless(f: ROS[Ref, O, S] => Boolean): This =
    when(!f(_))

  final def >>(next: Action[F, Ref, O, S, Err]): Composite[F, Ref, O, S, Err] =
    Composite(nonCompositeActions ++ next.nonCompositeActions)

  final def andThen(next: Action[F, Ref, O, S, Err]): Composite[F, Ref, O, S, Err] =
    this >> next
}

object Action {

  def empty[F[_], Ref, O, S, E] = Composite[F, Ref, O, S, E](Vector.empty)

  sealed trait NonComposite[F[_], Ref, O, S, Err] extends Action[F, Ref, O, S, Err] {
    type This <: NonComposite[F, Ref, O, S, Err]

    def name: Option[OS[O, S]] => String

    final override def nonCompositeActions: Vector[NonComposite[F, Ref, O, S, Err]] =
      vector1(this)

    final def times(n: Int): Group[F, Ref, O, S, Err] =
      Group(i => s"${name(i)} ($n times)", _ => Some(
        (1 to n).iterator
          .map(i => nameMod(s => s"[$i/$n] $s"))
          .foldLeft(empty: Action[F, Ref, O, S, Err])(_ >> _)),
        Check.Around.empty)
  }

  case class Composite[F[_], Ref, O, S, Err](nonCompositeActions: Vector[NonComposite[F, Ref, O, S, Err]])
    extends Action[F, Ref, O, S, Err] {

    override type This = Composite[F, Ref, O, S, Err]

    def map(f: NonComposite[F, Ref, O, S, Err] => NonComposite[F, Ref, O, S, Err]): This =
      Composite(nonCompositeActions map f)

    override def nameMod(f: (=> String) => String) =
      map(_ nameMod f)

    override def addCheck(c: Check.Around[O, S, Err]) =
      map(_ addCheck c)

    override def when(f: ROS[Ref, O, S] => Boolean) =
      map(_ when f)

    def group(name: String): Group[F, Ref, O, S, Err] =
      Group(_ => name, _ => Some(this), Check.Around.empty)

//    def times(n: Int, name: String) =
//      group(name).times(n)
  }

  case class Group[F[_], Ref, O, S, Err](name: Option[OS[O, S]] => String,
                                    action: ROS[Ref, O, S] => Option[Action[F, Ref, O, S, Err]],
                                    check: Check.Around[O, S, Err]) extends NonComposite[F, Ref, O, S, Err] {

    override type This = Group[F, Ref, O, S, Err]

    override def nameMod(f: (=> String) => String) =
      copy(name = o => f(name(o)))

    override def addCheck(c: Check.Around[O, S, Err]) =
      copy(check = check & c)

    override def when(f: ROS[Ref, O, S] => Boolean) =
      copy(action = i => if (f(i)) action(i) else None)
  }

  case class Single[F[_], Ref, O, S, Err](name: Option[OS[O, S]] => String,
                                     run: ROS[Ref, O, S] => Option[() => F[Either[Err, O => S]]],
                                     check: Check.Around[O, S, Err]) extends NonComposite[F, Ref, O, S, Err] {

    override type This = Single[F, Ref, O, S, Err]

    override def nameMod(f: (=> String) => String) =
      copy(name = o => f(name(o)))

    override def addCheck(c: Check.Around[O, S, Err]) =
      copy(check = check & c)

    override def when(f: ROS[Ref, O, S] => Boolean) =
      copy(run = i => if (f(i)) run(i) else None)
  }
}
