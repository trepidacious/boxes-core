package org.rebeam.boxes.core.monad

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.core.util.{WeakHashSet, RWLock, GCWatcher, BiMultiMap}

import org.rebeam.boxes.core.Identifiable

import scala.collection.immutable._

object BoxDeltasMonad {
  def boxDeltasMonad[A](f: RevisionAndDeltas => (BoxDeltas, A)) = new BoxDeltasMonad(f)
}

import BoxDeltasMonad._

/**
 * A monad allowing a list of BoxDeltas to be built by flatMapping functions
 * that read a RevisionAndDeltas and produce a new BoxDeltas to be applied
 * to the RevisionAndDeltas, plus a result. This is very similar to State[RevisionAndDeltas, A],
 * but instead of allowing new RevisionAndDeltas to be returned directly, we require that
 * a BoxDeltas is returned instead, and the monad itself will apply these deltas. This
 * means that only valid updates to RevisionAndDeltas are allowed, and also allows us to
 * build the deltas to the state rather than just the final state.
 */
class BoxDeltasMonad[A](f: RevisionAndDeltas => (BoxDeltas, A)) {

  def apply(rad: RevisionAndDeltas) = f(rad)

  def map[B](g: A => B): BoxDeltasMonad[B] = new BoxDeltasMonad[B]({ rad =>
    val (d, a) = f(rad)
    (d, g(a))
  })

  def flatMap[B](g: A => BoxDeltasMonad[B]): BoxDeltasMonad[B] = new BoxDeltasMonad[B]({ rad1 =>
    val (d1, a) = f(rad1)
    //rad2 is the RevisionAndDeltas after appending the new deltas from THIS monad to the incoming RevisionAndDeltas,
    //and will be provided to the new BoxDeltasMonad we are flatMapping
    val rad2 = rad1.appendDeltas(d1)
    val (d2, b) = g(a).apply(rad2)
    //The combined monad will apply deltas from this monad, and the new one
    (d1.append(d2), b)
  })

}

case class BoxChange(revision: Long)

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

  /** Get the value of this box in the context of a BoxDeltasMonad - can be used in for-comprehensions */
  def get(): BoxDeltasMonad[T] = boxDeltasMonad(_.get(this))

  /**
   * Set the value of this box in the context of a State - can be used in for-comprehensions
   * to set Box value in associated RevisionDelta
   */
  def set(t: T): BoxDeltasMonad[Box[T]] = boxDeltasMonad(_.set(this, t))

  def apply(): BoxDeltasMonad[T] = get()

  def update(t: T): BoxDeltasMonad[Box[T]] = set(t)

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
case class CreateBox[T](box: Box[T]) extends BoxDelta
case class ReadBox[T](box: Box[T]) extends BoxDelta
case class WriteBox[T](box: Box[T], t: T) extends BoxDelta
case class CreateReaction(reaction: Reaction, action: BoxDeltasMonad[Unit]) extends BoxDelta
case class Observe(observer: Observer) extends BoxDelta
case class Unobserve(observer: Observer) extends BoxDelta
case class UpdateReactionGraph(sources: BiMultiMap[Long, Long],
                               targets: BiMultiMap[Long, Long]) extends BoxDelta

/** A sequence of BoxDelta instances in order, plus append and a potentially faster way of finding the most recent write on a given box */
trait BoxDeltas {
  def deltas: Vector[BoxDelta]

  def append(d: BoxDeltas): BoxDeltas

  def append(d: BoxDelta): BoxDeltas

  /** Get the most recent write on a given box, if there is one */
  def boxWrite[T](box: Box[T]): Option[T]

  def altersRevision: Boolean

}

/** A BoxDeltas that caches all WriteBox deltas in a map to make boxWrite more efficient */
private class BoxDeltasCachedWrites(val deltas: Vector[BoxDelta], writes: Map[Box[Any], Any]) extends BoxDeltas {

  def append(d: BoxDeltas): BoxDeltas = {
    //Produce new writes cache, updating entries according to any WriteBox deltas in appended BoxDeltas
    val newWrites = d.deltas.foldLeft(writes)((writes, delta) => delta match {
      case WriteBox(box, t) => writes.updated(box.asInstanceOf[Box[Any]], t)
      case _ => writes
    })
    new BoxDeltasCachedWrites(deltas ++ d.deltas, newWrites)
  }

  def append(d: BoxDelta): BoxDeltas = {
    //Produce new writes cache, updating entries according to any WriteBox deltas in appended BoxDeltas
    val newWrites = d match {
      case WriteBox(box, t) => writes.updated(box, t)
      case _ => writes
    }
    new BoxDeltasCachedWrites(deltas ++ Vector(d), newWrites)
  }

  def boxWrite[T](box: Box[T]): Option[T] = writes.get(box.asInstanceOf[Box[Any]]).asInstanceOf[Option[T]]

  def altersRevision: Boolean = deltas.find(delta => delta match {
    case ReadBox(_) => false
    case _ => true
  }).isDefined

  override def toString = "BoxDeltasCachedWrites(" + deltas + ", " + writes + ")";
}

object BoxDeltas {
  def empty: BoxDeltas = new BoxDeltasCachedWrites(Vector.empty, Map.empty)
  def single(d: BoxDelta): BoxDeltas = empty.append(d)
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
    val different = _get(box) != t
    //If box value would not be changed, skip write
    val deltas = if (different) {
      BoxDeltas.single(WriteBox(box, t))
    } else {
      BoxDeltas.empty
    }
    //TODO handle reactions
    //      withReactor(_.afterSet(box, t, different))
    (deltas, box)
  }

  def get[T](box: Box[T]): (BoxDeltas, T) = {
    val v = _get(box)

    //TODO handle reactions
    //Only need to use a reactor if one is active
    //      currentReactor.foreach(_.afterGet(box))
    (BoxDeltas.single(ReadBox(box)), v)
  }

  def observe(observer: Observer): (BoxDeltas, Observer) = (BoxDeltas.single(Observe(observer)), observer)
  def unobserve(observer: Observer): (BoxDeltas, Observer) = (BoxDeltas.single(Unobserve(observer)), observer)

  /**
   * Return true if this revision delta alters the revision it is applied to
   */
  def altersRevision = deltas.altersRevision

  /** Create a new RevisionAndDeltas with same revision, but with new deltas appended to our own */
  def appendDeltas(d: BoxDeltas) = RevisionAndDeltas(revision, deltas.append(d))

  override def toString = "RevisionAndDeltas(" + revision.toString + "," + deltas + ")"
}

class Revision(val index: Long, val map: Map[Long, BoxChange], reactionMap: Map[Long, BoxDeltasMonad[Unit]], val sources: BiMultiMap[Long, Long], val targets: BiMultiMap[Long, Long], val boxReactions: Map[Long, Set[Reaction]]) {

  def stateOf[T](box: Box[T]): Option[BoxState[T]] = for {
    change <- map.get(box.id)
    value <- box.getValue(change)
  } yield BoxState(change.revision, value)

  def indexOf(box: Box[_]): Option[Long] = map.get(box.id).map(_.revision)

  def valueOf[T](box: Box[T]): Option[T] = {
    val change = map.get(box.id)
    change.flatMap(c => box.getValue(c))
  }

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

    //At this point we have to use the updates to reaction graph to update sources and targets. This will
    //need some work - UpdateReactionGraph is not currently correct, it needs to represent the new knowledge
    //about the reaction graph got from running a reaction, with a function to use it to incrementally update
    //sources and targets

    //Do not track sources and targets of removed reactions
    val prunedSources = sources.removedKeys(reactionDeletes.toSet)
    val prunedTargets = targets.removedKeys(reactionDeletes.toSet)

    new Revision(newIndex, newMap, newReactionMap, prunedSources, prunedTargets, prunedBoxReactions)
  }

  def canApplyDelta(rad: RevisionAndDeltas) = {
    val start = rad.revision.index
    //We conflict if the deltas call for reading or writing a box that has changed since the start revision of the
    //RevisionAndDeltas. Note that there is no conflict for reading/writing boxes that don't exist in this revision,
    //since they must be created in the delta
    val conflicts = rad.deltas.deltas.exists(delta => delta match {
      case ReadBox(box) => indexOf(box).map(_ > start).getOrElse(false)
      case WriteBox(box, _) => indexOf(box).map(_ > start).getOrElse(false)
      case _ => false
    })

    !conflicts
  }

  override def toString = "Revision(" + index + ")"
}

object Shelf {

  private val lock = RWLock()
  private var current = new Revision(0, Map.empty, Map.empty, BiMultiMap.empty, BiMultiMap.empty, Map.empty)

  private val retries = 10000

  private val watcher = new GCWatcher()
  private val reactionWatcher = new GCWatcher()

  private val observers = new WeakHashSet[Observer]()

  def currentRevision = lock.read { current }

  private def createBox[T]() = Box[T]()

  def run[A](s: BoxDeltasMonad[A]): Option[(Revision, A)] = {
    val baseRevision = currentRevision
    val (deltas, a) = s.apply(RevisionAndDeltas(baseRevision, BoxDeltas.empty))
    commit(RevisionAndDeltas(baseRevision, deltas)).map((_, a))
  }

  def runRepeated[A](s: BoxDeltasMonad[A]): (Revision, A) =
    Range(0, retries).view.map(_ => run(s)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))

  def atomic[A](s: BoxDeltasMonad[A]): A = runRepeated(s)._2

  def commit(rad: RevisionAndDeltas): Option[Revision] = {
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

        //TODO we could do this outside lock. We would want to ensure that observers still receive revisions in strict
        //order.
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
