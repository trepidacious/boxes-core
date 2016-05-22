package org.rebeam.boxes.core

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.persistence._

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

//BoxDeltaF elements with only reading of state, suitable for use in tracked observers
sealed trait BoxObserverDeltaF[+Next]

//Cases usable in any delta functors - do not modify any box data
case class ReadBoxDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next] with BoxWriterDeltaF[Next] with BoxObserverDeltaF[Next]
case class JustF[Next, T](t: T, toNext: T => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next] with BoxWriterDeltaF[Next] with BoxObserverDeltaF[Next]
case class RevisionIndexF[Next, T](toNext: Long => Next) extends BoxDeltaF[Next] with BoxReaderDeltaF[Next] with BoxWriterDeltaF[Next] with BoxObserverDeltaF[Next]

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

case class PullBooleanF[Next](toNext: Boolean => Next) extends BoxReaderDeltaF[Next]
case class PullIntF[Next](toNext: Int => Next) extends BoxReaderDeltaF[Next]
case class PullLongF[Next](toNext: Long => Next) extends BoxReaderDeltaF[Next]
case class PullFloatF[Next](toNext: Float => Next) extends BoxReaderDeltaF[Next]
case class PullDoubleF[Next](toNext: Double => Next) extends BoxReaderDeltaF[Next]
case class PullBigIntF[Next](toNext: BigInt => Next) extends BoxReaderDeltaF[Next]
case class PullBigDecimalF[Next](toNext: BigDecimal => Next) extends BoxReaderDeltaF[Next]
case class PullStringF[Next](toNext: String => Next) extends BoxReaderDeltaF[Next]

case class GetCachedF[Next](id: Long, toNext: Any => Next) extends BoxReaderDeltaF[Next]
case class PutCachedF[Next](id: Long, thing: Any, next: Next) extends BoxReaderDeltaF[Next]

//FIXME note this is possibly not necessary in the long term, however it's fairly easy to implement, 
//and allows for use cases like Node reading, where we want to run a BoxScript to build a default instance
//within a BoxReaderScript. We could use a BoxReaderScript for the default, but then we can't use it in normal
//transactions.
//If we could compose capabilities (e.g. as in http://functionaltalks.org/2014/11/23/runar-oli-bjarnason-free-monad/)
//we might not need this.
case class EmbedBoxScript[Next, T](script: BoxScript[T], toNext: T => Next) extends BoxReaderDeltaF[Next]

//Cases only usable for persistence - writer (Box -> Tokens)
case class PutTokenF[Next](t: Token, next: Next) extends BoxWriterDeltaF[Next]

case class AssignIdF[Next](t: Any, toNext: IdResult => Next) extends BoxWriterDeltaF[Next]

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
      case RevisionIndexF(toNext) => RevisionIndexF(toNext andThen f)
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

  val changedSources: BoxScript[Set[Box[_]]] = 
    liftF(ChangedSourcesF(identity): BoxDeltaF[Set[Box[_]]])(functor)

  val revisionIndex: BoxScript[Long] =
    liftF(RevisionIndexF(identity))(functor)

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
      case PullTokenF(toNext) => PullTokenF(toNext andThen f)

      case PullBooleanF(toNext) => PullBooleanF(toNext andThen f)
      case PullIntF(toNext) => PullIntF(toNext andThen f)
      case PullLongF(toNext) => PullLongF(toNext andThen f)
      case PullFloatF(toNext) => PullFloatF(toNext andThen f)
      case PullDoubleF(toNext) => PullDoubleF(toNext andThen f)
      case PullBigIntF(toNext) => PullBigIntF(toNext andThen f)
      case PullBigDecimalF(toNext) => PullBigDecimalF(toNext andThen f)
      case PullStringF(toNext) => PullStringF(toNext andThen f)

      case PutCachedF(id, thing, next) => PutCachedF(id, thing, f(next))

      case GetCachedF(id, toNext) => GetCachedF(id, toNext andThen f)

      case EmbedBoxScript(script, toNext) => EmbedBoxScript(script, toNext andThen f)

      case RevisionIndexF(toNext) => RevisionIndexF(toNext andThen f)
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

  val revisionIndex: BoxReaderScript[Long] =
    liftF(RevisionIndexF(identity): BoxReaderDeltaF[Long])(boxReaderDeltaFunctor)

  val peek: BoxReaderScript[Token] 
    = liftF(PeekTokenF(identity[Token]): BoxReaderDeltaF[Token])(boxReaderDeltaFunctor)
  val pull: BoxReaderScript[Token]
    = liftF(PullTokenF(identity[Token]): BoxReaderDeltaF[Token])(boxReaderDeltaFunctor)

  val pullBoolean: BoxReaderScript[Boolean]
    = liftF(PullBooleanF(identity[Boolean]): BoxReaderDeltaF[Boolean])(boxReaderDeltaFunctor)
  val pullInt: BoxReaderScript[Int]
    = liftF(PullIntF(identity[Int]): BoxReaderDeltaF[Int])(boxReaderDeltaFunctor)
  val pullLong: BoxReaderScript[Long]
    = liftF(PullLongF(identity[Long]): BoxReaderDeltaF[Long])(boxReaderDeltaFunctor)
  val pullFloat: BoxReaderScript[Float]
    = liftF(PullFloatF(identity[Float]): BoxReaderDeltaF[Float])(boxReaderDeltaFunctor)
  val pullDouble: BoxReaderScript[Double]
    = liftF(PullDoubleF(identity[Double]): BoxReaderDeltaF[Double])(boxReaderDeltaFunctor)
  val pullBigInt: BoxReaderScript[BigInt]
    = liftF(PullBigIntF(identity[BigInt]): BoxReaderDeltaF[BigInt])(boxReaderDeltaFunctor)
  val pullBigDecimal: BoxReaderScript[BigDecimal]
    = liftF(PullBigDecimalF(identity[BigDecimal]): BoxReaderDeltaF[BigDecimal])(boxReaderDeltaFunctor)
  val pullString: BoxReaderScript[String]
    = liftF(PullStringF(identity[String]): BoxReaderDeltaF[String])(boxReaderDeltaFunctor)

  @throws [IncorrectTokenException]
  def pullExpected(expected: Token) = pull map {
    case actual if actual == expected => actual
    case actual => throw new IncorrectTokenException("Expected " + expected + " but got " + actual)
  }

  @throws [IncorrectTokenException]
  def pullFiltered(filter: Token => Boolean, message: String = "as expected") = pull map {
    case actual if filter(actual) => actual
    case actual => throw new IncorrectTokenException("Got token " + actual + ", not " + message)
  }

  def getCached(id: Long): BoxReaderScript[Any]
    = liftF(GetCachedF(id, identity[Any]): BoxReaderDeltaF[Any])(boxReaderDeltaFunctor)
  def putCached(id: Long, thing: Any): BoxReaderScript[Unit]
    = liftF(PutCachedF(id, thing, ()): BoxReaderDeltaF[Unit])(boxReaderDeltaFunctor)

  def embedBoxScript[T](script: BoxScript[T]): BoxReaderScript[T]
    = liftF(EmbedBoxScript(script, identity[T]))(boxReaderDeltaFunctor)
}

object BoxWriterDeltaF {
  val boxWriterDeltaFunctor: Functor[BoxWriterDeltaF] = new Functor[BoxWriterDeltaF] {
    override def map[A, B](bdf: BoxWriterDeltaF[A])(f: (A) => B): BoxWriterDeltaF[B] = bdf match {
      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
      case JustF(t, toNext) => JustF(t, toNext andThen f)

      case PutTokenF(t, next) => PutTokenF(t, f(next))

      case AssignIdF(t, toNext) => AssignIdF(t, toNext andThen f)

      case RevisionIndexF(toNext) => RevisionIndexF(toNext andThen f)
    }
  }

  def get[T](box: Box[T]): BoxWriterScript[T]
    = liftF(ReadBoxDeltaF(box, identity[T]): BoxWriterDeltaF[T])(boxWriterDeltaFunctor)

  def just[T](t: T): BoxWriterScript[T]
    = liftF(JustF(t, identity: T => T): BoxWriterDeltaF[T])(boxWriterDeltaFunctor)

  def put(t: Token): BoxWriterScript[Unit]
    = liftF(PutTokenF(t, ()): BoxWriterDeltaF[Unit])(boxWriterDeltaFunctor)

  def assignId(thing: Any): BoxWriterScript[IdResult]
    = liftF(AssignIdF(thing, identity[IdResult]): BoxWriterDeltaF[IdResult])(boxWriterDeltaFunctor)

  val nothing = just(())

  val revisionIndex: BoxWriterScript[Long] =
    liftF(RevisionIndexF(identity): BoxWriterDeltaF[Long])(boxWriterDeltaFunctor)

}

object BoxObserverDeltaF {
  val boxObserverDeltaFunctor: Functor[BoxObserverDeltaF] = new Functor[BoxObserverDeltaF] {
    override def map[A, B](bdf: BoxObserverDeltaF[A])(f: (A) => B): BoxObserverDeltaF[B] = bdf match {
      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
      case JustF(t, toNext) => JustF(t, toNext andThen f)
      case RevisionIndexF(toNext) => RevisionIndexF(toNext andThen f)
    }
  }

  def get[T](box: Box[T]): BoxObserverScript[T]
    = liftF(ReadBoxDeltaF(box, identity[T]): BoxObserverDeltaF[T])(boxObserverDeltaFunctor)

  def just[T](t: T): BoxObserverScript[T]
    = liftF(JustF(t, identity: T => T): BoxObserverDeltaF[T])(boxObserverDeltaFunctor)

  val nothing = just(())

  val revisionIndex: BoxObserverScript[Long] =
    liftF(RevisionIndexF(identity): BoxObserverDeltaF[Long])(boxObserverDeltaFunctor)

}