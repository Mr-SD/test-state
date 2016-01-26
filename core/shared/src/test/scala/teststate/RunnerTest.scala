package teststate

import utest._
import TestUtil._

object RunnerTest extends TestSuite {

  val * = Dsl[Unit, Unit, Unit, String]

  val options = Options.uncolored.alwaysShowChildren

  val action = *.action("Press button.").act(_ => ())
  val action2 = *.action("Pull lever.").act(_ => ())
  val actionF = *.action("Press button!").actTry(_ => Some("BUTTON'S BROKEN"))
  val actionG = (action >> action2).group("Groupiness.")
  val actionGF1 = (actionF >> action2).group("Groupiness.")
  val actionGF2 = (action >> actionF).group("Groupiness.")

  val checkPoint = *.point("Check stuff.", _ => None)
  val checkPoint2 = *.point("Check more stuff.", _ => None)
  val checkPointF = *.point("Check failure.", _ => Some("Shit broke!"))

  implicit def autoName(s: String): *.Name = _ => s

  def test(a: *.Action, i: *.Check)(expect: String): Unit = {
    val h = Test0(a, i).observe(_ => ()).run((), ())
    val actual = formatHistory(h, options).trim
    assertEq(actual = actual, expect.trim)
  }

  override def tests = TestSuite {

    'empty - test(Action.empty, Check.empty)("✓ All pass.")

    'invariants {
      def t(i: *.Check)(expect: String): Unit = test(Action.empty, i)(expect)
      'pass {
        'simplest - t(checkPoint)(
          """
            |✓ Initial state.
            |  ✓ Check stuff.
            |✓ All pass.
          """.stripMargin)

        'multiple - t(checkPoint & checkPoint2)(
          """
            |✓ Initial state.
            |  ✓ Check stuff.
            |  ✓ Check more stuff.
            |✓ All pass.
          """.stripMargin)
      }
      'fail {
        'single - t(checkPointF)(
          """
            |✘ Initial state.
            |  ✘ Check failure. -- Shit broke!
          """.stripMargin)

        'first - t(checkPointF & checkPoint2)(
          """
            |✘ Initial state.
            |  ✘ Check failure. -- Shit broke!
            |  ✓ Check more stuff.
          """.stripMargin)

        'second - t(checkPoint & checkPointF)(
          """
            |✘ Initial state.
            |  ✓ Check stuff.
            |  ✘ Check failure. -- Shit broke!
          """.stripMargin)
      }
    }

    'action {
      def t(a: *.Action)(expect: String): Unit = test(a, Check.empty)(expect)
      'pass {
        'simplest - t(action)(
          """
            |✓ Press button.
            |✓ All pass.
          """.stripMargin)

        'before - t(action addCheck checkPoint.before)(
          """
            |✓ Press button.
            |  ✓ Check stuff.
            |  ✓ Action
            |✓ All pass.
          """.stripMargin)

        'after - t(action addCheck checkPoint.after)(
          """
            |✓ Press button.
            |  ✓ Action
            |  ✓ Check stuff.
            |✓ All pass.
          """.stripMargin)

        'around - t(action addCheck checkPoint.after addCheck checkPoint2.before)(
          """
            |✓ Press button.
            |  ✓ Check more stuff.
            |  ✓ Action
            |  ✓ Check stuff.
            |✓ All pass.
          """.stripMargin)
      }
      'fail {
        'simplest - t(actionF)(
          """
            |✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'beforeA - t(actionF addCheck checkPoint.before)(
          """
            |✘ Press button!
            |  ✓ Check stuff.
            |  ✘ Action -- BUTTON'S BROKEN
          """.stripMargin)

        'beforeC - t(action addCheck checkPointF.before)(
          """
            |✘ Press button.
            |  ✘ Check failure. -- Shit broke!
            |  - Action
          """.stripMargin)

        'afterA - t(actionF addCheck checkPoint.after)(
          """
            |✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'afterC - t(action addCheck checkPointF.after)(
          """
            |✘ Press button.
            |  ✓ Action
            |  ✘ Check failure. -- Shit broke!
          """.stripMargin)

        'around1 - t(action addCheck checkPoint.after addCheck checkPointF.before)(
          """
            |✘ Press button.
            |  ✘ Check failure. -- Shit broke!
            |  - Action
          """.stripMargin)

        'around2 - t(actionF addCheck checkPoint.after addCheck checkPoint2.before)(
          """
            |✘ Press button!
            |  ✓ Check more stuff.
            |  ✘ Action -- BUTTON'S BROKEN
          """.stripMargin)

        'around3 - t(action addCheck checkPointF.after addCheck checkPoint2.before)(
          """
            |✘ Press button.
            |  ✓ Check more stuff.
            |  ✓ Action
            |  ✘ Check failure. -- Shit broke!
          """.stripMargin)
      }
    }

    'actionG {
      def t(a: *.Action)(expect: String): Unit = test(a, Check.empty)(expect)
      'pass {
        'simple - t(actionG)(
          """
            |✓ Groupiness.
            |  ✓ Press button.
            |  ✓ Pull lever.
            |✓ All pass.
          """.stripMargin)

        'before - t(actionG addCheck checkPoint.before)(
          """
            |✓ Groupiness.
            |  ✓ Check stuff.
            |  ✓ Action
            |    ✓ Press button.
            |    ✓ Pull lever.
            |✓ All pass.
          """.stripMargin)

        'after - t(actionG addCheck checkPoint.after)(
          """
            |✓ Groupiness.
            |  ✓ Action
            |    ✓ Press button.
            |    ✓ Pull lever.
            |  ✓ Check stuff.
            |✓ All pass.
          """.stripMargin)

        'around - t(actionG addCheck checkPoint.before addCheck checkPoint2.after)(
          """
            |✓ Groupiness.
            |  ✓ Check stuff.
            |  ✓ Action
            |    ✓ Press button.
            |    ✓ Pull lever.
            |  ✓ Check more stuff.
            |✓ All pass.
          """.stripMargin)
      }
      'failA1 {
        def a = actionGF1
        'simple - t(a)(
          """
            |✘ Groupiness.
            |  ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'before - t(a addCheck checkPoint.before)(
          """
            |✘ Groupiness.
            |  ✓ Check stuff.
            |  ✘ Action
            |    ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'after - t(a addCheck checkPoint.after)(
          """
            |✘ Groupiness.
            |  ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'around - t(a addCheck checkPoint.before addCheck checkPoint2.after)(
          """
            |✘ Groupiness.
            |  ✓ Check stuff.
            |  ✘ Action
            |    ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)
      }
      'fail2 {
        def a = actionGF2
        'simple - t(a)(
          """
            |✘ Groupiness.
            |  ✓ Press button.
            |  ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'before - t(a addCheck checkPoint.before)(
          """
            |✘ Groupiness.
            |  ✓ Check stuff.
            |  ✘ Action
            |    ✓ Press button.
            |    ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'after - t(a addCheck checkPoint.after)(
          """
            |✘ Groupiness.
            |  ✓ Press button.
            |  ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

        'around - t(a addCheck checkPoint.before addCheck checkPoint2.after)(
          """
            |✘ Groupiness.
            |  ✓ Check stuff.
            |  ✘ Action
            |    ✓ Press button.
            |    ✘ Press button! -- BUTTON'S BROKEN
          """.stripMargin)

      }
    }

    // action
    //   - pre [1,n]
    //   - post [1,n]
    // invariants
    //   - point [1,n]

    // action combinators
    // action groups
    // skipping

  }
}
