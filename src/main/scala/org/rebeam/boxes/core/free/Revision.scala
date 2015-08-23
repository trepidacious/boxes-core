package org.rebeam.boxes.core.free

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.core.Identifiable
import org.rebeam.boxes.core.util.{BiMultiMap, GCWatcher, RWLock, WeakHashSet}

import scala.annotation.tailrec
import scala.collection.immutable._
import scalaz._
import Scalaz._
import Free._

case class BoxChange(revision: Long)

import BoxTypes._

class Box[T](val id: Long) extends Identifiable {

  /**
   * Store changes to this box, as a map from the Change to the State that was
   * set by that Change. Changes that form a revision are retained by RevisionMonad
   * and used to look up States in refs.
   * Since this is a weak map, it does not retain Changes, they are retained only
   * by the revisions they are in.
   * When a Box is GCed, the changes are also GCed, allowing the States
   * of the Box (instances of T) to be removed.
   */
  private val changes = new scala.collection.mutable.WeakHashMap[BoxChange, T]()

  private[core] def addChange(c: BoxChange, t: T) = changes.put(c, t)

  private[core] def getValue(c: BoxChange) = changes.get(c)

  override def toString = "Box(" + id + ")"

  /** Get the value of this box in the context of a BoxScript - can be used in for-comprehensions */
  def get() = BoxDeltaF.get(this)

  /**
   * Set the value of this box in the context of a State - can be used in for-comprehensions
   * to set Box value in associated RevisionDelta
   */
  def set(t: T) = BoxDeltaF.set(this, t)

  def apply() = get()

  def update(t: T) = set(t)

  def get(revision: Revision): T = revision.valueOf(this).getOrElse(throw new RuntimeException("Missing Box(" + this.id + ")"))
  def apply(revision: Revision): T = get(revision)

}

object Box {
  private val nextId = new AtomicInteger(0)
  def apply[T](): Box[T] = new Box[T](nextId.getAndIncrement())
}

case class BoxState[+T](revision: Long, value: T)

case class Reaction(id: Long) extends Identifiable

object Reaction {
  private val nextId = new AtomicInteger(0)
  def apply(): Reaction = Reaction(nextId.getAndIncrement())
}

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


//A Functor based on BoxDeltas that is suitable for use with Free
sealed trait BoxDeltaF[+Next]
case class CreateBoxDeltaF[Next, T](t: T, toNext: Box[T] => Next) extends BoxDeltaF[Next]
case class ReadBoxDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxDeltaF[Next]

case class WriteBoxDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next]
case class CreateReactionDeltaF[Next, T](action: BoxScript[Unit], toNext: Reaction => Next) extends BoxDeltaF[Next]
case class ObserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]
case class UnobserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]

object BoxDeltaF {
  implicit val functor: Functor[BoxDeltaF] = new Functor[BoxDeltaF] {
    override def map[A, B](bdf: BoxDeltaF[A])(f: (A) => B): BoxDeltaF[B] = bdf match {
      case CreateBoxDeltaF(t, toNext) => CreateBoxDeltaF(t, toNext andThen f) //toNext returns the next Free when called with t:T,
                                                                              //then we call f on this next Free to sequence it after
      case ReadBoxDeltaF(b, toNext) => ReadBoxDeltaF(b, toNext andThen f)
      case CreateReactionDeltaF(action, toNext) => CreateReactionDeltaF(action, toNext andThen f)

      case WriteBoxDeltaF(b, t, next) => WriteBoxDeltaF(b, t, f(next))        //Call f on next Free directly, to sequence it after
      case ObserveDeltaF(observer, next) => ObserveDeltaF(observer, f(next))
      case UnobserveDeltaF(observer, next) => UnobserveDeltaF(observer, f(next))
    }
  }

  def create[T](t: T)                     = liftF(CreateBoxDeltaF(t, identity: Box[T] => Box[T]))
  def set[T](box: Box[T], t: T)           = liftF(WriteBoxDeltaF(box, t, ()))
  def get[T](box: Box[T])                 = liftF(ReadBoxDeltaF(box, identity: T => T))
  def observe(observer: Observer)         = liftF(ObserveDeltaF(observer, ()))
  def unobserve(observer: Observer)       = liftF(UnobserveDeltaF(observer, ()))
  def createReaction(action: BoxScript[Unit]) = liftF(CreateReactionDeltaF(action, identity: Reaction => Reaction))
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

/** A sequence of BoxDelta instances in order applied, plus append and a potentially faster way of finding the most recent write on a given box */
trait BoxDeltas {
  def deltas: Vector[BoxDelta]

  def append(d: BoxDeltas): BoxDeltas

  def append(d: BoxDelta): BoxDeltas

  /** Get the most recent write on a given box, if there is one */
  def boxWrite[T](box: Box[T]): Option[T]

  def altersRevision: Boolean

  def reactionGraph: Option[ReactionGraph]

  /** Get created reaction script for a given id, if there is one */
  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]]
}

case class ReactionGraph(sources: BiMultiMap[Long, Long], targets: BiMultiMap[Long, Long]) {
  def removedReactions(reactionDeletes: Set[Long]) = {
    val prunedSources = sources.removedKeys(reactionDeletes)
    val prunedTargets = targets.removedKeys(reactionDeletes)
    ReactionGraph(prunedSources, prunedTargets)
  }

  def targetsOfReaction(rid: Long) = targets.valuesFor(rid)
  def sourcesOfReaction(rid: Long) = sources.valuesFor(rid)

  def reactionsTargettingBox(bid: Long) = targets.keysFor(bid)
  def reactionsSourcingBox(bid: Long) = sources.keysFor(bid)

  def updatedForReactionId(rid: Long, sourceBoxes: Set[Long], targetBoxes: Set[Long]) =
    ReactionGraph(sources.updated(rid, sourceBoxes), targets.updated(rid, targetBoxes))

}
object ReactionGraph {
  val empty = new ReactionGraph(BiMultiMap.empty, BiMultiMap.empty)
}

/** A BoxDeltas that caches all WriteBox deltas in a map to make boxWrite more efficient */
private class BoxDeltasCachedWrites(val deltas: Vector[BoxDelta], writes: Map[Box[Any], Any], val reactionGraph: Option[ReactionGraph], val idToScriptMap: Map[Long, BoxScript[Unit]]) extends BoxDeltas {

  def append(d: BoxDeltas): BoxDeltas = {
    //Produce new writes cache, updating entries according to any WriteBox deltas in appended BoxDeltas
    val newWrites = d.deltas.foldLeft(writes)((writes, delta) => delta match {
      case WriteBox(box, t) => writes.updated(box.asInstanceOf[Box[Any]], t)
      case _ => writes
    })
    val newReactionGraph = d.deltas.foldLeft(reactionGraph)((rg, delta) => delta match {
      case UpdateReactionGraph(newGraph) => Some(newGraph)
      case _ => rg
    })
    val newIdToScript = d.deltas.foldLeft(idToScriptMap)((m, delta) => delta match {
      case CreateReaction(reaction, script) => m.updated(reaction.id, script)
      case _ => m
    })
    new BoxDeltasCachedWrites(deltas ++ d.deltas, newWrites, newReactionGraph, newIdToScript)
  }

  def append(d: BoxDelta): BoxDeltas = {
    //Produce new writes cache, updating entries according to any WriteBox deltas in appended BoxDeltas
    val newWrites = d match {
      case WriteBox(box, t) => writes.updated(box, t)
      case _ => writes
    }
    val newReactionGraph = d match {
      case UpdateReactionGraph(newGraph) => Some(newGraph)
      case _ => reactionGraph
    }
    val newIdToScript = d match {
      case CreateReaction(reaction, script) => idToScriptMap.updated(reaction.id, script)
      case _ => idToScriptMap
    }
    new BoxDeltasCachedWrites(deltas ++ Vector(d), newWrites, newReactionGraph, newIdToScript)
  }

  def boxWrite[T](box: Box[T]): Option[T] = writes.get(box.asInstanceOf[Box[Any]]).asInstanceOf[Option[T]]

  def altersRevision: Boolean = deltas.exists{
    case ReadBox(_) => false
    case _ => true
  }

  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]] = idToScriptMap.get(rid)

  override def toString = "BoxDeltasCachedWrites(" + deltas + ", " + writes + ")";
}

object BoxDeltas {
  def empty: BoxDeltas = new BoxDeltasCachedWrites(Vector.empty, Map.empty, None: Option[ReactionGraph], Map.empty)
  def single(d: BoxDelta): BoxDeltas = empty.append(d)
}

object RevisionAndDeltas {
  /**
   * Run a script and append the deltas it generates to an existing RevisionAndDeltas, creating a new
   * RevisionAndDeltas and a script result
   * @param script    The script to run
   * @param rad       The initial RevisionAndDeltas
   * @param runReactions  True to run reactions when they are created, or when boxes are written. False to ignore reactions
   * @tparam A        The result type of the script
   * @return          (new RevisionAndDeltas, script result)
   */
  @tailrec final def appendScript[A](script: BoxScript[A], rad: RevisionAndDeltas, boxDeltas: BoxDeltas, runReactions: Boolean = true): (RevisionAndDeltas, A, BoxDeltas) = script.resume match {

    case -\/(CreateBoxDeltaF(t, toNext)) =>
      val (deltas, box) = rad.create(t)
      val next = toNext(box)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions)

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val (deltas, value) = rad.get(b)
      val next = toNext(value)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions)

    case -\/(WriteBoxDeltaF(b, t, next)) =>
      val (deltas, box) = rad.set(b, t)
      val rad2 = rad.appendDeltas(deltas)
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      appendScript(next, rad3, boxDeltas.append(deltas), runReactions)

    case -\/(CreateReactionDeltaF(action, toNext)) =>
      val (deltas, reaction) = rad.createReaction(action)
      val next = toNext(reaction)
      val rad2 = rad.appendDeltas(deltas)
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      appendScript(next, rad3, boxDeltas.append(deltas), runReactions)

    case -\/(ObserveDeltaF(obs, next)) =>
      val (deltas, _) = rad.observe(obs)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions)

    case -\/(UnobserveDeltaF(obs, next)) =>
      val (deltas, _) = rad.unobserve(obs)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions)

    case \/-(x) => (rad, x.asInstanceOf[A], boxDeltas)
  }
}

case class RevisionAndDeltas(revision: Revision, deltas: BoxDeltas) {

  def create[T](t: T): (BoxDeltas, Box[T]) = {
    val box = Box[T]()
    (BoxDeltas.empty.append(CreateBox(box)).append(WriteBox(box, t)), box)
  }

  private def _get[T](box: Box[T]): T = deltas.boxWrite(box).getOrElse(revision.valueOf(box).getOrElse(
    throw new RuntimeException("Missing Box for id " + box.id)
  ))

  def set[T](box: Box[T], t: T): (BoxDeltas, Box[T]) = {
    //We need to record the write to have reactions work properly etc., however the interpreter
    //is free to ignore writes to same value to optimise when they would make no difference
    (BoxDeltas.single(WriteBox(box, t)), box)
  }

  def get[T](box: Box[T]): (BoxDeltas, T) = {
    val v = _get(box)
    (BoxDeltas.single(ReadBox(box)), v)
  }

  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]] = deltas.scriptForReactionId(rid).orElse(revision.scriptForReactionId(rid))

  def observe(observer: Observer): (BoxDeltas, Observer) = (BoxDeltas.single(Observe(observer)), observer)
  def unobserve(observer: Observer): (BoxDeltas, Observer) = (BoxDeltas.single(Unobserve(observer)), observer)

  def createReaction(action: BoxScript[Unit]): (BoxDeltas, Reaction) = {
    val reaction = Reaction()
    (BoxDeltas.single(CreateReaction(reaction, action)), reaction)
  }

  def reactionGraph: ReactionGraph = deltas.reactionGraph.getOrElse(revision.reactionGraph)

  /**
   * Return true if this revision delta alters the revision it is applied to
   */
  def altersRevision = deltas.altersRevision

  /** Create a new RevisionAndDeltas with same revision, but with new deltas appended to our own */
  def appendDeltas(d: BoxDeltas) = RevisionAndDeltas(revision, deltas.append(d))

  /** Run a script and append the deltas it generates to this instance, creating a new RevisionAndDeltas, a script result and the new deltas added by the script */
  def appendScript[A](script: BoxScript[A], runReactions: Boolean = true): (RevisionAndDeltas, A, BoxDeltas) = RevisionAndDeltas.appendScript[A](script, this, BoxDeltas.empty, runReactions)

  override def toString = "RevisionAndDeltas(" + revision.toString + "," + deltas + ")"
}




class Revision(val index: Long, val map: Map[Long, BoxChange], reactionMap: Map[Long, BoxScript[Unit]], val reactionGraph: ReactionGraph, val boxReactions: Map[Long, Set[Reaction]]) {

  def stateOf[T](box: Box[T]): Option[BoxState[T]] = for {
    change <- map.get(box.id)
    value <- box.getValue(change)
  } yield BoxState(change.revision, value)

  def indexOf(box: Box[_]): Option[Long] = map.get(box.id).map(_.revision)

  def valueOf[T](box: Box[T]): Option[T] = {
    val change = map.get(box.id)
    change.flatMap(c => box.getValue(c))
  }

  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]] = reactionMap.get(rid)

  private[core] def updated(deltas: BoxDeltas, deletes: List[Long], reactionDeletes: List[Long]) = {
    val newIndex = index + 1

    //Remove boxes that have been GCed, then add new ones
    val prunedMap = deletes.foldLeft(map) { case (m, id) => m - id }

    //Apply WriteBox deltas
    val newMap = deltas.deltas.foldLeft(prunedMap) {
      case (m, WriteBox(box, value)) =>
        val newChange = BoxChange(newIndex)
        box.asInstanceOf[Box[Any]].addChange(newChange, value)
        m.updated(box.id, newChange)
      case (m, _) => m
    }

    //Remove reactions that have been GCed, then add new ones
    val prunedReactionMap = reactionDeletes.foldLeft(reactionMap) { case (m, id) => m - id }
    val newReactionMap = deltas.deltas.foldLeft(prunedReactionMap) {
      case (m, CreateReaction(reaction, f)) => m.updated(reaction.id, f)
      case (m, _) => m
    }

    //Where boxes have been GCed, also remove the entry in boxReactions for that box - we only want the
    //boxReactions maps to retain reactions for revisions while the boxes are still reachable
    val prunedBoxReactions = deletes.foldLeft(boxReactions) { case (m, id) => m - id }

    //Get new reaction graph if there is one in the deltas, otherwise keep our old one, then remove deleted reactions
    val newReactionGraph = deltas.reactionGraph.getOrElse(reactionGraph).removedReactions(reactionDeletes.toSet)

    new Revision(newIndex, newMap, newReactionMap, newReactionGraph, prunedBoxReactions)
  }

  def canApplyDelta(rad: RevisionAndDeltas) = {
    val start = rad.revision.index
    //We conflict if the deltas call for reading or writing a box that has changed since the start revision of the
    //RevisionAndDeltas. Note that there is no conflict for reading/writing boxes that don't exist in this revision,
    //since they must be created in the delta
    val conflicts = rad.deltas.deltas.exists{
      case ReadBox(box) => indexOf(box).exists(_ > start)
      case WriteBox(box, _) => indexOf(box).exists(_ > start)
      case _ => false
    }

    !conflicts
  }

  override def toString = "Revision(" + index + ")"
}

object Shelf {

  private val lock = RWLock()
  private var current = new Revision(0, Map.empty, Map.empty, ReactionGraph.empty, Map.empty)

  private val retries = 10000

  private val watcher = new GCWatcher()
  private val reactionWatcher = new GCWatcher()

  private val observers = new WeakHashSet[Observer]()

  def currentRevision = lock.read { current }

  def run[A](s: BoxScript[A]): Option[(Revision, A)] = {
    val baseRevision = currentRevision

    val baseRad = RevisionAndDeltas(baseRevision, BoxDeltas.empty)

    val (finalRad, result, _) = baseRad.appendScript(s)

    commit(RevisionAndDeltas(baseRevision, finalRad.deltas)).map((_, result))
  }

  def runRepeated[A](s: BoxScript[A]): (Revision, A) =
    Range(0, retries).view.map(_ => run(s)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))

  def atomic[A](s: BoxScript[A]): A = runRepeated(s)._2

  def commit(rad: RevisionAndDeltas): Option[Revision] = {
    //TODO: Note we can be more optimistic here - get the current revision, try to apply it and if that works, lock
    //and if the current revision is still the same, commit, otherwise have another try. This would reduce the
    //time lock is held to almost nothing - just long enough to check revision has not changed, and update watcher and
    //observer lists. These could be updated with sets to add/remove calculated outside lock as well.
    lock.write{
      //if the delta does nothing, don't create a new revision, just return the current one
      if (!rad.altersRevision) {
        Some(current)

      //If delta doesn't conflict, commit it
      } else if (current.canApplyDelta(rad)) {

        //Watch new boxes and reactions, and add/remove observers as requested (new observers will see the updated revision
        //as their first)
        for (d <- rad.deltas.deltas) d match {
          case CreateBox(box) => watcher.watch(Set(box))
          case CreateReaction(reaction, _) => reactionWatcher.watch(Set(reaction))
          case Observe(observer) => observers.add(observer)
          case Unobserve(observer) => observers.remove(observer)
          case _ => ()
        }

        //Make updated revision based on deltas plus GCed boxes and reactions
        val updated = current.updated(rad.deltas, watcher.deletes(), reactionWatcher.deletes())

        //Move to the new revision
        current = updated

        //TODO we should do this outside lock. We would want to ensure that observers still receive revisions in strict
        //order, for this we could use a queue. We are currently really relying on observers being quick...
        //Notify observers.
        observers.foreach(_.observe(updated))

        //Return the new revision
        Some(updated)

      //Delta conflicts, do nothing and return no new revision
      } else {
        None
      }
    }
  }
}

trait Observer {
  def observe(r: Revision): Unit
}

class BoxException(message: String = "") extends Exception(message)
class FailedReactionsException(message: String = "") extends BoxException(message)
class ConflictingReactionException(message: String = "") extends BoxException(message)
class ReactionAppliedTooManyTimesInCycle(message: String = "") extends BoxException(message)
