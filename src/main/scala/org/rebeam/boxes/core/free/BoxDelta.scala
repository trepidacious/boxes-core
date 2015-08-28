package org.rebeam.boxes.core.free

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.core.Identifiable
import org.rebeam.boxes.core.free.BoxTypes._
import org.rebeam.boxes.core.util.{BiMultiMap, GCWatcher, RWLock, WeakHashSet}

import scala.annotation.tailrec
import scala.collection.immutable._
import scalaz._
import Scalaz._
import Free._

//Describe the basic operations that can be applied to a Revision to produce
//an udpated Revision. These are the valid operations of a "transaction".
sealed trait BoxDelta

/** Create and return a new Box[T] */
case class CreateBox[T](box: Box[T]) extends BoxDelta
/** Read and return contents of a Box[T] */
case class ReadBox[T](box: Box[T]) extends BoxDelta
case class WriteBox[T](box: Box[T], t: T) extends BoxDelta
case class CreateReaction(reaction: Reaction, action: BoxScript[Unit]) extends BoxDelta
case class Observe(observer: Observer) extends BoxDelta
case class Unobserve(observer: Observer) extends BoxDelta
case class UpdateReactionGraph(reactionGraph: ReactionGraph) extends BoxDelta
case class AttachReactionToBox[T](r: Reaction, b: Box[T]) extends BoxDelta
case class DetachReactionFromBox[T](r: Reaction, b: Box[T]) extends BoxDelta


//A Functor based on BoxDeltas that is suitable for use with Free
sealed trait BoxDeltaF[+Next]
case class CreateBoxDeltaF[Next, T](t: T, toNext: Box[T] => Next) extends BoxDeltaF[Next]
case class ReadBoxDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxDeltaF[Next]

case class WriteBoxDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next]
case class CreateReactionDeltaF[Next, T](action: BoxScript[Unit], toNext: Reaction => Next) extends BoxDeltaF[Next]
case class ObserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]
case class UnobserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]

case class AttachReactionToBoxF[Next, T](r: Reaction, b: Box[T], next: Next) extends BoxDeltaF[Next]
case class DetachReactionFromBoxF[Next, T](r: Reaction, b: Box[T], next: Next) extends BoxDeltaF[Next]

case class ChangedSourcesF[Next, T](next: Set[Box[_]] => Next) extends BoxDeltaF[Next]

case class JustF[Next, T](t: T, toNext: T => Next) extends BoxDeltaF[Next]

object BoxDeltaF {
  val functor: Functor[BoxDeltaF] = new Functor[BoxDeltaF] {
    override def map[A, B](bdf: BoxDeltaF[A])(f: (A) => B): BoxDeltaF[B] = bdf match {
      case CreateBoxDeltaF(t, toNext) => CreateBoxDeltaF(t, toNext andThen f) //toNext returns the next Free when called with t:T,
      //then we call f on this next Free to sequence it after
      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
      case CreateReactionDeltaF(action, toNext) => CreateReactionDeltaF(action, toNext andThen f)

      case WriteBoxDeltaF(b, t, next) => WriteBoxDeltaF(b, t, f(next))        //Call f on next Free directly, to sequence it after
      case ObserveDeltaF(observer, next) => ObserveDeltaF(observer, f(next))
      case UnobserveDeltaF(observer, next) => UnobserveDeltaF(observer, f(next))
      case AttachReactionToBoxF(r, b, next) => AttachReactionToBoxF(r, b, f(next))
      case DetachReactionFromBoxF(r, b, next) => DetachReactionFromBoxF(r, b, f(next))

      case ChangedSourcesF(toNext) => ChangedSourcesF(toNext andThen f)

      case JustF(t, toNext) => JustF(t, toNext andThen f)
    }
  }

  def create[T](t: T)                     = liftF(CreateBoxDeltaF(t, identity: Box[T] => Box[T]))
  def set[T](box: Box[T], t: T)           = liftF(WriteBoxDeltaF(box, t, ()))
  def get[T](box: Box[T])                 = liftF(ReadBoxDeltaF(box, identity: T => T))
  def observe(observer: Observer)         = liftF(ObserveDeltaF(observer, ()))
  def unobserve(observer: Observer)       = liftF(UnobserveDeltaF(observer, ()))
  def createReaction(action: BoxScript[Unit]) = liftF(CreateReactionDeltaF(action, identity: Reaction => Reaction))
  def attachReactionToBox(r: Reaction, b: Box[_]) = liftF(AttachReactionToBoxF(r, b, ()))
  def detachReactionFromBox(r: Reaction, b: Box[_]) = liftF(DetachReactionFromBoxF(r, b, ()))
  def just[T](t: T)                       = liftF(JustF(t, identity: T => T))

  def changedSources() = liftF(ChangedSourcesF(identity))
}

////Another functor with a restricted set of operations suitable for use in reactions
//sealed trait BoxReactionDeltaF[+Next]
//case class ReadBoxReactionDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxReactionDeltaF[Next]
//case class WriteBoxReactionDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxReactionDeltaF[Next]
//
//object BoxReactionDeltaF {
//  implicit val functor: Functor[BoxReactionDeltaF] = new Functor[BoxReactionDeltaF] {
//    override def map[A, B](bdf: BoxReactionDeltaF[A])(f: (A) => B): BoxReactionDeltaF[B] = bdf match {
//      case ReadBoxReactionDeltaF(b, toNext) => ReadBoxReactionDeltaF(b, toNext andThen f)
//      case WriteBoxReactionDeltaF(b, t, next) => WriteBoxReactionDeltaF(b, t, f(next))
//    }
//  }
//
//  def set[T](box: Box[T], t: T)           = liftF(WriteBoxReactionDeltaF(box, t, ()))
//  def get[T](box: Box[T])                 = liftF(ReadBoxReactionDeltaF(box, identity: T => T))
//}