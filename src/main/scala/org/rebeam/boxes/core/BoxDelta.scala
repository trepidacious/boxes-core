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
object BoxDelta {
  case class BoxCreated[T](box: Box[T], t: T) extends BoxDelta
  case class BoxRead[T](box: Box[T], t: T) extends BoxDelta
  case class BoxWritten[T](box: Box[T], now: T, was: T) extends BoxDelta
  case class ReactionCreated(reaction: Reaction, action: BoxScript[Unit]) extends BoxDelta
  case class Observed(observer: Observer) extends BoxDelta
  case class Unobserved(observer: Observer) extends BoxDelta
  case class ReactionGraphUpdated(reactionGraph: ReactionGraph) extends BoxDelta
  case class ReactionAttachedToBox[T](r: Reaction, b: Box[T]) extends BoxDelta
  case class ReactionDetachedFromBox[T](r: Reaction, b: Box[T]) extends BoxDelta  
}

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

  def create[T](t: T): BoxScript[Box[T]] = 
    liftF(CreateBoxDeltaF(t, identity[Box[T]]): BoxDeltaF[Box[T]])(functor)

  def set[T](box: Box[T], t: T): BoxScript[Unit] = 
    liftF(WriteBoxDeltaF(box, t, ()): BoxDeltaF[Unit])(functor)

  def get[T](box: Box[T]): BoxScript[T] = 
    liftF(ReadBoxDeltaF(box, identity[T]): BoxDeltaF[T])(functor)

  def observe(observer: Observer): BoxScript[Unit] = 
    liftF(ObserveDeltaF(observer, ()): BoxDeltaF[Unit])(functor)

  def unobserve(observer: Observer): BoxScript[Unit] = 
    liftF(UnobserveDeltaF(observer, ()): BoxDeltaF[Unit])(functor)

  def createReaction(action: BoxScript[Unit]): BoxScript[Reaction] = 
    liftF(CreateReactionDeltaF(action, identity[Reaction]): BoxDeltaF[Reaction])(functor)

  def attachReactionToBox(r: Reaction, b: Box[_]): BoxScript[Unit] = 
    liftF(AttachReactionToBoxF(r, b, ()): BoxDeltaF[Unit])(functor)

  def detachReactionFromBox(r: Reaction, b: Box[_]): BoxScript[Unit] = 
    liftF(DetachReactionFromBoxF(r, b, ()): BoxDeltaF[Unit])(functor)

  def just[T](t: T): BoxScript[T] = 
    liftF(JustF(t, identity: T => T): BoxDeltaF[T])(functor)

  val nothing = just(())

  def changedSources(): BoxScript[Set[Box[_]]] = 
    liftF(ChangedSourcesF(identity): BoxDeltaF[Set[Box[_]]])(functor)

}

object BoxReaderDeltaF {
  val boxReaderDeltaFunctor: Functor[BoxReaderDeltaF] = new Functor[BoxReaderDeltaF] {
    override def map[A, B](bdf: BoxReaderDeltaF[A])(f: (A) => B): BoxReaderDeltaF[B] = bdf match {
      case CreateBoxDeltaF(t, toNext) => CreateBoxDeltaF(t, toNext andThen f)
      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
      case CreateReactionDeltaF(action, toNext) => CreateReactionDeltaF(action, toNext andThen f)
      case WriteBoxDeltaF(b, t, next) => WriteBoxDeltaF(b, t, f(next))
      case AttachReactionToBoxF(r, b, next) => AttachReactionToBoxF(r, b, f(next))
      case DetachReactionFromBoxF(r, b, next) => DetachReactionFromBoxF(r, b, f(next))
      case JustF(t, toNext) => JustF(t, toNext andThen f)

      case PeekTokenF(toNext) => PeekTokenF(toNext andThen f)
      case PullTokenF(toNext) => PeekTokenF(toNext andThen f)

      case PutCachedF(id, thing, next) => PutCachedF(id, thing, f(next))
      case PutCachedBoxF(id, box, next) => PutCachedBoxF(id, box, f(next))

      case GetCachedF(id, toNext) => GetCachedF(id, toNext andThen f)
      case GetCachedBoxF(id, toNext) => GetCachedBoxF(id, toNext andThen f)
    }
  }

  def create[T](t: T): BoxReaderScript[Box[T]]                       
    = liftF(CreateBoxDeltaF(t, identity[Box[T]]): BoxReaderDeltaF[Box[T]])(boxReaderDeltaFunctor)

  def set[T](box: Box[T], t: T): BoxReaderScript[Unit]
    = liftF(WriteBoxDeltaF(box, t, ()): BoxReaderDeltaF[Unit])(boxReaderDeltaFunctor)

  def get[T](box: Box[T]): BoxReaderScript[T]
    = liftF(ReadBoxDeltaF(box, identity[T]): BoxReaderDeltaF[T])(boxReaderDeltaFunctor)

  def createReaction(action: BoxScript[Unit]): BoxReaderScript[Reaction] 
    = liftF(CreateReactionDeltaF(action, identity[Reaction]): BoxReaderDeltaF[Reaction])(boxReaderDeltaFunctor)

  def attachReactionToBox(r: Reaction, b: Box[_]): BoxReaderScript[Unit] 
    = liftF(AttachReactionToBoxF(r, b, ()): BoxReaderDeltaF[Unit])(boxReaderDeltaFunctor)

  def detachReactionFromBox(r: Reaction, b: Box[_]): BoxReaderScript[Unit] 
    = liftF(DetachReactionFromBoxF(r, b, ()): BoxReaderDeltaF[Unit])(boxReaderDeltaFunctor)

  def just[T](t: T): BoxReaderScript[T]
    = liftF(JustF(t, identity: T => T): BoxReaderDeltaF[T])(boxReaderDeltaFunctor)

  val nothing = just(())

  def peek(): BoxReaderScript[Token] 
    = liftF(PeekTokenF(identity[Token]): BoxReaderDeltaF[Token])(boxReaderDeltaFunctor)
  def pull(): BoxReaderScript[Token]
    = liftF(PullTokenF(identity[Token]): BoxReaderDeltaF[Token])(boxReaderDeltaFunctor)

  def getCached(id: Long): BoxReaderScript[Any]
    = liftF(GetCachedF(id, identity[Any]): BoxReaderDeltaF[Any])(boxReaderDeltaFunctor)
  def putCached(id: Long, thing: Any): BoxReaderScript[Unit]
    = liftF(PutCachedF(id, thing, ()): BoxReaderDeltaF[Unit])(boxReaderDeltaFunctor)

  def getCachedBox(id: Long): BoxReaderScript[Box[Any]]
    = liftF(GetCachedBoxF(id, identity[Box[Any]]): BoxReaderDeltaF[Box[Any]])(boxReaderDeltaFunctor)
  def putCachedBox(id: Long, box: Box[Any]): BoxReaderScript[Unit] 
    = liftF(PutCachedBoxF(id, box, ()): BoxReaderDeltaF[Unit])(boxReaderDeltaFunctor)
}

object BoxWriterDeltaF {
  val boxWriterDeltaFunctor: Functor[BoxWriterDeltaF] = new Functor[BoxWriterDeltaF] {
    override def map[A, B](bdf: BoxWriterDeltaF[A])(f: (A) => B): BoxWriterDeltaF[B] = bdf match {
      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
      case JustF(t, toNext) => JustF(t, toNext andThen f)

      case PutTokenF(t, next) => PutTokenF(t, f(next))

      case CacheF(t, toNext) => CacheF(t, toNext andThen f)
      case CacheBoxF(box, toNext) => CacheF(box, toNext andThen f)
    }
  }

  def get[T](box: Box[T]): BoxWriterScript[T]
    = liftF(ReadBoxDeltaF(box, identity[T]): BoxWriterDeltaF[T])(boxWriterDeltaFunctor)

  def just[T](t: T): BoxWriterScript[T]
    = liftF(JustF(t, identity: T => T): BoxWriterDeltaF[T])(boxWriterDeltaFunctor)

  def put(t: Token): BoxWriterScript[Unit]
    = liftF(PutTokenF(t, ()): BoxWriterDeltaF[Unit])(boxWriterDeltaFunctor)

  def cache(thing: Any): BoxWriterScript[CacheResult]
    = liftF(CacheF(thing, identity[CacheResult]): BoxWriterDeltaF[CacheResult])(boxWriterDeltaFunctor)

  def putCached(box: Box[Any]): BoxWriterScript[CacheResult]
    = liftF(CacheBoxF(box, identity[CacheResult]): BoxWriterDeltaF[CacheResult])(boxWriterDeltaFunctor)

}
