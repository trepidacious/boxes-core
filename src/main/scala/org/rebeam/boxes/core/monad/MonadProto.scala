package org.rebeam.boxes.core.monad

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.core.util.{RWLock, GCWatcher, BiMultiMap}
import org.rebeam.boxes.core._

import scala.collection.immutable._
import scala.collection.mutable.WeakHashMap
import scalaz.State

class ShelfMonad() {

  def create[T](t: T): State[this.RevisionDelta, Box[T]] = State(_.create(t))
  def set[T](box: Box[T], t: T): State[this.RevisionDelta, Box[T]] = State(_.set(box, t))
  def get[T](box: Box[T]): State[this.RevisionDelta, T] = State(_.get(box))

  private val lock = RWLock()
  private var current = new RevisionMonad(0, Map.empty, Map.empty, BiMultiMap.empty, BiMultiMap.empty, Map.empty)

  private val retries = 10000

  private val watcher = new GCWatcher()
  private val reactionWatcher = new GCWatcher()

//  private val views = new WeakHashSet[ViewDefault]()
//  private val autos = new WeakHashSet[AutoDefault[_]]()

  def currentRevision = lock.read { current }

  def currentRevisionDelta = RevisionDelta(currentRevision)

  private def createBox[T]() = BoxMonad[T]()

  private def revise(updated: RevisionMonad) {
    current = updated

    //TODO this can be done outside the lock by just passing the new revision to a queue to be
    //consumed by another thread that actually updates views
//    views.foreach(_.add(updated))
//    autos.foreach(_.add(updated))
  }

  def commit(delta: RevisionDelta): Option[RevisionMonad] = {
    lock.write{
      if (!current.conflictsWith(delta)) {
        //Watch new boxes, make new revision with GCed boxes deleted, and make the new updated revision
        watcher.watch(delta.creates)
        reactionWatcher.watch(delta.newReactions.keySet)
        val updated = current.updated(delta, watcher.deletes(), reactionWatcher.deletes())

        //Add new views and autos - they will get the update revision as their first revision
//        for (view <- t.viewsToAdd) views.add(view)
//        for (auto <- t.autosToAdd) autos.add(auto)

        //TODO there must be a neater way to handle the View/ViewDefault thing - maybe View should have an add method,
        //but then ViewDefault would have to accept Revision not RevisionDefault
        //Remove unwanted views and autos - they will not see this new revision
//        for (view <- t.viewsToRemove) view match {case v: ViewDefault => views.remove(v)}
//        for (auto <- t.autosToRemove) auto match {case a: AutoDefault[_] => autos.remove(a)}

        //Move to the new revision
        revise(updated)

        //Return the new revision
        Some(updated)
      } else {
        None
      }
    }
  }

  //Path-dependent types

  private class BoxMonad[T](val id: Long) extends Box[T] {
    /**
     * Store changes to this box, as a map from the Change to the State that was
     * set by that Change. Changes that form a revision are retained by RevisionMonad
     * and used to look up States in refs.
     * Since this is a weak map, it does not retain Changes, they are retained only
     * by the revisions they are in.
     * When a BoxMonad is GCed, the changes are also GCed, allowing the States
     * of the Box (instances of T) to be removed.
     */
    private val changes = new WeakHashMap[BoxMonadChange, T]()

    private[core] def addChange(c: BoxMonadChange, t: T) = changes.put(c, t)

    private[core] def getValue(c: BoxMonadChange) = changes.get(c)

    override def toString = "BoxMonad(id = " + id + ", changes = " + changes + ")"
  }

  private object BoxMonad {
    private val nextId = new AtomicInteger(0)

    def apply[T](): Box[T] = new BoxMonad[T](nextId.getAndIncrement())
  }

  private case class ReactionMonad(val id: Long) extends Reaction

  private object ReactionMonad {
    private val nextId = new AtomicInteger(0)

    def apply(): ReactionMonad = ReactionMonad(nextId.getAndIncrement())
  }

  case class BoxMonadChange(val revision: Long)

  case class RevisionDelta(
                            base: RevisionMonad,
                            creates: Set[Box[_]],
                            reads: Set[Long],
                            writes: Map[Box[_], _],
                            newReactions: Map[Reaction, ReactionFunc],
                            sources: BiMultiMap[Long, Long],
                            targets: BiMultiMap[Long, Long],
                            boxReactions: Map[Long, Set[Reaction]]) {

    def create[T](t: T) = {
      val box = BoxMonad[T]()
      (this.copy(creates = creates + box).copy(writes = writes.updated(box, t)), box)
    }

    private def _get[T](box: BoxR[T]): T = writes.get(box.asInstanceOf[Box[_]]).asInstanceOf[Option[T]].getOrElse(base.valueOf(box).getOrElse(
      throw new RuntimeException("Missing Box for id " + box.id)
    ))

    def set[T](box: Box[T], t: T) = {
      val different = _get(box) != t
      //If box value would not be changed, skip write
      val delta = if (different) {
        this.copy(writes = writes.updated(box, t))
      } else {
        this
      }
      //TODO handle reactions
//      withReactor(_.afterSet(box, t, different))
      (delta, box)
    }

    def get[T](box: BoxR[T]) = {
      val v = _get(box)

      //TODO handle reactions
      //Only need to use a reactor if one is active
//      currentReactor.foreach(_.afterGet(box))
      (this.copy(reads = reads + box.id), v)
    }
  }

  object RevisionDelta {
    def apply(revision: RevisionMonad): RevisionDelta = RevisionDelta(revision, Set.empty, Set.empty, Map.empty, Map.empty, BiMultiMap.empty, BiMultiMap.empty, Map.empty)
  }

  class RevisionMonad(val index: Long, val map: Map[Long, BoxMonadChange], reactionMap: Map[Long, ReactionFunc], val sources: BiMultiMap[Long, Long], val targets: BiMultiMap[Long, Long], val boxReactions: Map[Long, Set[Reaction]]) {

    def stateOf[T](box: BoxR[T]): Option[BoxState[T]] = for {
      change <- map.get(box.id)
      value <- box.asInstanceOf[BoxMonad[T]].getValue(change)
    } yield BoxState(change.revision, value)

    def indexOf(box: BoxR[_]): Option[Long] = map.get(box.id).map(_.revision)

    def valueOf[T](box: BoxR[T]): Option[T] = {
      val change = map.get(box.id)
      change.flatMap(c => box.asInstanceOf[BoxMonad[T]].getValue(c))
    }

    private def indexOfId(id: Long): Option[Long] = map.get(id).map(_.revision)

    private def reactionOfId(id: Long) = reactionMap.get(id)

    def updated(delta: RevisionDelta, deletes: List[Long], reactionDeletes: List[Long]) = {
      val newIndex = index + 1

      //Remove boxes that have been GCed, then add new ones
      val prunedMap = deletes.foldLeft(map) { case (m, id) => m - id }

      val newMap = delta.writes.foldLeft(prunedMap) { case (m, (box, value)) =>
        val newChange = BoxMonadChange(newIndex)
        box.asInstanceOf[BoxMonad[Any]].addChange(newChange, value)
        m.updated(box.id, newChange)
      }

      //Remove reactions that have been GCed, then add new ones
      val prunedReactionMap = reactionDeletes.foldLeft(reactionMap) { case (m, id) => m - id }
      val newReactionMap = delta.newReactions.foldLeft(prunedReactionMap) { case (m, (reaction, f)) => m.updated(reaction.id, f) }

      //Where boxes have been GCed, also remove the entry in boxReactions for that box - we only want the
      //boxReactions maps to retain reactions for revisions while the boxes are still reachable
      val prunedBoxReactions = deletes.foldLeft(boxReactions) { case (m, id) => m - id }

      //Do not track sources and targets of removed reactions
      val prunedSources = sources.removedKeys(reactionDeletes.toSet)
      val prunedTargets = targets.removedKeys(reactionDeletes.toSet)

      new RevisionMonad(newIndex, newMap, newReactionMap, prunedSources, prunedTargets, prunedBoxReactions)
    }

    def conflictsWith(d: RevisionDelta) = {
      val start = d.base.index
      d.reads.iterator.flatMap(indexOfId).exists(_ > start) || d.writes.keysIterator.flatMap(indexOf).exists(_ > start)
    }
  }

}
