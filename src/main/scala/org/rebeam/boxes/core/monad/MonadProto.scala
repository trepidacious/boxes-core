package org.rebeam.boxes.core.monad

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.core.util.{RWLock, GCWatcher, BiMultiMap}

import org.rebeam.boxes.core.Identifiable

import scala.collection.immutable._
import scalaz.State

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

  override def toString = "Box(id = " + id + ", changes = " + changes + ")"
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

case class RevisionDelta(
                          base: Revision,
                          creates: Set[Box[_]],
                          reads: Set[Long],
                          writes: Map[Box[_], _],
                          newReactions: Map[Reaction, State[RevisionDelta, Unit]],
                          sources: BiMultiMap[Long, Long],
                          targets: BiMultiMap[Long, Long],
                          boxReactions: Map[Long, Set[Reaction]]) {

  def create[T](t: T) = {
    val box = Box[T]()
    (this.copy(creates = creates + box).copy(writes = writes.updated(box, t)), box)
  }

  private def _get[T](box: Box[T]): T = writes.get(box.asInstanceOf[Box[_]]).asInstanceOf[Option[T]].getOrElse(base.valueOf(box).getOrElse(
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

  def get[T](box: Box[T]) = {
    val v = _get(box)

    //TODO handle reactions
    //Only need to use a reactor if one is active
    //      currentReactor.foreach(_.afterGet(box))
    (this.copy(reads = reads + box.id), v)
  }

  override def toString = "RevisionDelta(" + base.toString + ")"
}

object RevisionDelta {
  def apply(revision: Revision): RevisionDelta = RevisionDelta(
    revision,
    //Empty creates, reads, writes and new reactions
    Set.empty,
    Set.empty,
    Map.empty,
    Map.empty,
    //Inherit reaction sources and targets, and boxes retaining reactions, from revision
    revision.sources,
    revision.targets,
    revision.boxReactions)
}

class Revision(val index: Long, val map: Map[Long, BoxChange], reactionMap: Map[Long, State[RevisionDelta, Unit]], val sources: BiMultiMap[Long, Long], val targets: BiMultiMap[Long, Long], val boxReactions: Map[Long, Set[Reaction]]) {

  def stateOf[T](box: Box[T]): Option[BoxState[T]] = for {
    change <- map.get(box.id)
    value <- box.getValue(change)
  } yield BoxState(change.revision, value)

  def indexOf(box: Box[_]): Option[Long] = map.get(box.id).map(_.revision)

  def valueOf[T](box: Box[T]): Option[T] = {
    val change = map.get(box.id)
    change.flatMap(c => box.getValue(c))
  }

  private def indexOfId(id: Long): Option[Long] = map.get(id).map(_.revision)

  private def reactionOfId(id: Long) = reactionMap.get(id)

  private[core] def updated(delta: RevisionDelta, deletes: List[Long], reactionDeletes: List[Long]) = {
    val newIndex = index + 1

    //Remove boxes that have been GCed, then add new ones
    val prunedMap = deletes.foldLeft(map) { case (m, id) => m - id }

    val newMap = delta.writes.foldLeft(prunedMap) { case (m, (box, value)) =>
      val newChange = BoxChange(newIndex)
      box.asInstanceOf[Box[Any]].addChange(newChange, value)
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

    new Revision(newIndex, newMap, newReactionMap, prunedSources, prunedTargets, prunedBoxReactions)
  }

  def conflictsWith(d: RevisionDelta) = {
    val start = d.base.index
    d.reads.iterator.flatMap(indexOfId).exists(_ > start) || d.writes.keysIterator.flatMap(indexOf).exists(_ > start)
  }

  override def toString = "Revision(" + index + ")"
}

object Shelf {

  private val lock = RWLock()
  private var current = new Revision(0, Map.empty, Map.empty, BiMultiMap.empty, BiMultiMap.empty, Map.empty)

  private val retries = 10000

  private val watcher = new GCWatcher()
  private val reactionWatcher = new GCWatcher()

//  private val views = new WeakHashSet[ViewDefault]()
//  private val autos = new WeakHashSet[AutoDefault[_]]()

  def currentRevision = lock.read { current }

  def currentRevisionDelta = RevisionDelta(currentRevision)

  private def createBox[T]() = Box[T]()

  private def revise(updated: Revision) {
    current = updated

    //TODO this can be done outside the lock by just passing the new revision to a queue to be
    //consumed by another thread that actually updates views
//    views.foreach(_.add(updated))
//    autos.foreach(_.add(updated))
  }

  def run[A](s: State[RevisionDelta, A]): Option[(Revision, A)] = {
    val (delta, a) = s.run(currentRevisionDelta)
    commit(delta).map((_, a))
  }

  def runRepeated[A](s: State[RevisionDelta, A]): (Revision, A) =
    Range(0, retries).view.map(_ => run(s)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))

  def atomic[A](s: State[RevisionDelta, A]): A = runRepeated(s)._2

  def commit(delta: RevisionDelta): Option[Revision] = {
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
}
