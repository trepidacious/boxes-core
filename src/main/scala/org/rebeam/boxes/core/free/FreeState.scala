package org.rebeam.boxes.core.free

import scala.language.higherKinds
import scala.annotation.tailrec
import scalaz._
import scalaz.Scalaz._
import scalaz.Free._

trait EffectWorld {

  /** Our world's state. Subclasses may choose to make this type public. */
  protected type State

  /** An action in this `World`, returning a result of type `A`. */
  type Action[A]

  /** Run an `Action` with some initial `State`, returning the final state and result. */
  protected def runWorld[A](a: Action[A], w: State): (State, A)

  /** Construct an `Action` for a computation that transitions the `State` and produces a result of type `A`. */
  protected def action[A](f: State => (State, A)): Action[A]

  /** Construct an `Action` for a computation consumes the `State` and produces a result of type `A`. */
  protected final def effect[A](f: State => A): Action[A] =
    action(s => (s, f(s)))

  /** Construct an `Action` for a computation that simply returns a value of type `A`. */
  protected final def unit[A](a: => A): Action[A] =
    action(s => (s, a))

}

/** An `EffectWorld` implemented in terms of `Free`. */
trait FreeWorld extends EffectWorld {

  /** Our operations are simple state transitions. */
  case class Op[+A] private[FreeWorld] (f: State => (State, A))

  /** Operation must have a `Functor` in order to gain a `Free` monad. */
  implicit val OpFunctor: Functor[Op] =
    new Functor[Op] {
      def map[A, B](op: Op[A])(g: A => B) = //Op(op.f(_).rightMap(g))
      //Equivalent to the following - rightMap applies g to the right element of the (newState, a) pair.
        Op(state => {
          val (newState, a) = op.f(state)
          (newState, g(a))
        })
    }

  type Action[A] = Free[Op, A]

  protected def action[A](f: State => (State, A)): Action[A] = liftF(Op(f))

  @tailrec protected final def runWorld[A](a: Action[A], s: State): (State, A) =
    a.resume match { // N.B. resume.fold() doesn't permit TCO
      case -\/(Op(f)) =>
        val (s0, a) = f(s)
        runWorld(a, s0)
      case \/-(a) => (s, a)
    }

}

object FreeMapWorld extends FreeWorld with App {
  type State = Map[String, String]

  val s: State = Map.empty

  def set(key: String, value: String): Action[Unit] = action((s: State) => {
    val newState = s.updated(key, value)
    (newState, ())
  })

  def get(key: String): Action[String] = action((s: State) => {
    (s, s.apply(key))
  })

  val test = for {
    _ <- set("a", "1")
    a <- get("a")
    _ <- set("b", a)
  } yield (a)

  println(runWorld(test, s))
}
