package org.rebeam.boxes.core

import java.util.concurrent.atomic.AtomicInteger

import BoxTypes._
import util.{BiMultiMap, GCWatcher, RWLock, WeakHashSet}

import scala.annotation.tailrec
import scala.collection.immutable._
import scalaz._
import Scalaz._
import Free._

//Describe the basic operations that can be applied to a Revision to produce
//an updated Revision. These are the valid operations of a "transaction".
sealed trait BoxDelta

case class BoxCreated[T](box: Box[T], t: T) extends BoxDelta
case class BoxRead[T](box: Box[T], t: T) extends BoxDelta
case class BoxWritten[T](box: Box[T], now: T, was: T) extends BoxDelta
case class ReactionCreated(reaction: Reaction, action: BoxScript[Unit]) extends BoxDelta
case class Observed(observer: Observer) extends BoxDelta
case class Unobserved(observer: Observer) extends BoxDelta
case class ReactionGraphUpdated(reactionGraph: ReactionGraph) extends BoxDelta
case class ReactionAttachedToBox[T](r: Reaction, b: Box[T]) extends BoxDelta
case class ReactionDetachedFromBox[T](r: Reaction, b: Box[T]) extends BoxDelta

//These cases form a Functor based on BoxDeltas that is suitable for use with Free, during normal transactions
sealed trait BoxDeltaF[+Next]

//BoxDeltaF elements except observe/unobserve with additional cases for reading tokens during deserialisation of boxes data
sealed trait BoxReaderDeltaF[+Next]

//BoxDeltaF elements except observe/unobserve with additional cases for writing tokens during serialisation of boxes data
sealed trait BoxWriterDeltaF[+Next]

//Cases usable in any delta functors - do not modify any box data
case class ReadBoxDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next] with BoxWriterDeltaF[Next]
case class JustF[Next, T](t: T, toNext: T => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next] with BoxWriterDeltaF[Next]

//Cases usable in any delta functor allowing modification of Box State
case class CreateBoxDeltaF[Next, T](t: T, toNext: Box[T] => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next]
case class WriteBoxDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next]
case class CreateReactionDeltaF[Next, T](action: BoxScript[Unit], toNext: Reaction => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next]
case class AttachReactionToBoxF[Next, T](r: Reaction, b: Box[T], next: Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next]
case class DetachReactionFromBoxF[Next, T](r: Reaction, b: Box[T], next: Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next]

//Cases useful only in standard transactions with BoxDeltaF. Note that ChangedSources will return empty set except when running reactions
case class ObserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]
case class UnobserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]
case class ChangedSourcesF[Next, T](next: Set[Box[_]] => Next) extends BoxDeltaF[Next]

//Cases only usable for persistence - reader (Tokens -> Box)
case class PeekTokenF[Next](toNext: Token => Next) extends BoxReaderDeltaF[Next]
case class PullTokenF[Next](toNext: Token => Next) extends BoxReaderDeltaF[Next]

case class GetCachedF[Next](id: Long, toNext: Any => Next) extends BoxReaderDeltaF[Next]
case class PutCachedF[Next](id: Long, thing: Any, next: Next) extends BoxReaderDeltaF[Next]

case class GetCachedBoxF[Next](id: Long, toNext: Box[Any] => Next) extends BoxReaderDeltaF[Next]
case class PutCachedBoxF[Next](id: Long, box: Box[Any], next: Next) extends BoxReaderDeltaF[Next]

//Cases only usable for persistence - writer (Box -> Tokens)
case class PutTokenF[Next](t: Token, next: Next) extends BoxWriterDeltaF[Next]

case class CacheF[Next](t: Any, toNext: CacheResult => Next) extends BoxWriterDeltaF[Next]
case class CacheBoxF[Next](box: Box[Any], toNext: CacheResult => Next) extends BoxWriterDeltaF[Next]

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

  def create[T](t: T)                     = liftF(CreateBoxDeltaF(t, identity[Box[T]]))(functor)
  def set[T](box: Box[T], t: T)           = liftF(WriteBoxDeltaF(box, t, ()))(functor)
  def get[T](box: Box[T])                 = liftF(ReadBoxDeltaF(box, identity[T]))(functor)
  def observe(observer: Observer)         = liftF(ObserveDeltaF(observer, ()))(functor)
  def unobserve(observer: Observer)       = liftF(UnobserveDeltaF(observer, ()))(functor)
  def createReaction(action: BoxScript[Unit]) = liftF(CreateReactionDeltaF(action, identity[Reaction]))(functor)
  def attachReactionToBox(r: Reaction, b: Box[_]) = liftF(AttachReactionToBoxF(r, b, ()))(functor)
  def detachReactionFromBox(r: Reaction, b: Box[_]) = liftF(DetachReactionFromBoxF(r, b, ()))(functor)
  def just[T](t: T)                       = liftF(JustF(t, identity: T => T))(functor)
  val nothing                             = just(())
  def changedSources()                    = liftF(ChangedSourcesF(identity))(functor)

}

//object BoxReaderDeltaF {
//  val boxReaderDeltaFunctor: Functor[BoxReaderDeltaF] = new Functor[BoxReaderDeltaF] {
//    override def map[A, B](bdf: BoxReaderDeltaF[A])(f: (A) => B): BoxReaderDeltaF[B] = bdf match {
//      case CreateBoxDeltaF(t, toNext) => CreateBoxDeltaF(t, toNext andThen f)
//      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
//      case CreateReactionDeltaF(action, toNext) => CreateReactionDeltaF(action, toNext andThen f)
//      case WriteBoxDeltaF(b, t, next) => WriteBoxDeltaF(b, t, f(next))
//      case AttachReactionToBoxF(r, b, next) => AttachReactionToBoxF(r, b, f(next))
//      case DetachReactionFromBoxF(r, b, next) => DetachReactionFromBoxF(r, b, f(next))
//      case JustF(t, toNext) => JustF(t, toNext andThen f)
//
//      case PeekTokenF(toNext) => PeekTokenF(toNext andThen f)
//      case PullTokenF(toNext) => PeekTokenF(toNext andThen f)
//      //
//      //      case PutTokenF(t, next) => PutTokenF(t, f(next))
//      //
//      case PutCachedF(id, thing, next) => PutCachedF(id, thing, f(next))
//      case PutCachedBoxF(id, box, next) => PutCachedBoxF(id, box, f(next))
//
//      case GetCachedF(id, toNext) => GetCachedF(id, toNext andThen f)
//      case GetCachedBoxF(id, toNext) => GetCachedBoxF(id, toNext andThen f)
//    }
//  }
//
//  def create[T](t: T)                       = liftF(CreateBoxDeltaF(t, identity[Box[T]]))(boxReaderDeltaFunctor)
//  def set[T](box: Box[T], t: T)           = liftF(WriteBoxDeltaF(box, t, ()))(boxReaderDeltaFunctor)
//  def get[T](box: Box[T])                 = liftF(ReadBoxDeltaF(box, identity[T]))(boxReaderDeltaFunctor)
//  def createReaction(action: BoxScript[Unit]) = liftF(CreateReactionDeltaF(action, identity[Reaction]))(boxReaderDeltaFunctor)
//  def attachReactionToBox(r: Reaction, b: Box[_]) = liftF(AttachReactionToBoxF(r, b, ()))(boxReaderDeltaFunctor)
//  def detachReactionFromBox(r: Reaction, b: Box[_]) = liftF(DetachReactionFromBoxF(r, b, ()))(boxReaderDeltaFunctor)
//  def just[T](t: T)                       = liftF(JustF(t, identity: T => T))(boxReaderDeltaFunctor)
//  val nothing                             = just(())
//
//  def peek()                              = liftF(PeekTokenF(identity[Token]))(boxReaderDeltaFunctor)
//  def pull()                              = liftF(PullTokenF(identity[Token]))(boxReaderDeltaFunctor)
//
//  def getCached(id: Long)                 = liftF(GetCachedF(id, identity[Any]))(boxReaderDeltaFunctor)
//  def putCached(id: Long, thing: Any)     = liftF(PutCachedF(id, thing, ()))(boxReaderDeltaFunctor)
//
//  def getCachedBox(id: Long)                 = liftF(GetCachedBoxF(id, identity[Box[Any]]))(boxReaderDeltaFunctor)
//  def putCachedBox(id: Long, box: Box[Any])  = liftF(PutCachedBoxF(id, box, ()))(boxReaderDeltaFunctor)
//}
//
//object BoxWriterDeltaF {
//  val functor: Functor[BoxWriterDeltaF] = new Functor[BoxWriterDeltaF] {
//    override def map[A, B](bdf: BoxWriterDeltaF[A])(f: (A) => B): BoxWriterDeltaF[B] = bdf match {
//      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
//      case JustF(t, toNext) => JustF(t, toNext andThen f)
//
//      case PutTokenF(t, next) => PutTokenF(t, f(next))
//
//      case CacheF(t, toNext) => CacheF(t, toNext andThen f)
//      case CacheBoxF(box, toNext) => CacheF(box, toNext andThen f)
//    }
//  }
//}

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