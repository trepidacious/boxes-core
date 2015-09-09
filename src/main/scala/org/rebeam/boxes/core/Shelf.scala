package org.rebeam.boxes.core

import BoxTypes._
import util.{WeakHashSet, GCWatcher, RWLock}

import scala.collection.immutable.{Range, Map}
import BoxDelta._

import org.rebeam.boxes.persistence._

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

  def runReader[A](s: BoxReaderScript[A], reader: TokenReader): Option[(Revision, A)] = {
    val baseRevision = currentRevision

    val baseRad = RevisionAndDeltas(baseRevision, BoxDeltas.empty)

    val (finalRad, result, _, _) = BoxReaderScript.run(s, baseRad, BoxDeltas.empty, reader)

    commit(RevisionAndDeltas(baseRevision, finalRad.deltas)).map((_, result))    
  }

  def runReaderOrException[A](s: BoxReaderScript[A], reader: TokenReader): A = runReader(s, reader) map (_._2) getOrElse (
    throw new RuntimeException("Shelf.runReaderOrException failed - should not occur for valid tokens and formats")
  )

  def runWriter[A](s: BoxWriterScript[A], writer: TokenWriter): A = BoxWriterScript.run(s, currentRevision, writer)._1

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
          case BoxCreated(box, _) => watcher.watch(Set(box))
          case ReactionCreated(reaction, _) => reactionWatcher.watch(Set(reaction))
          case Observed(observer) => observers.add(observer)
          case Unobserved(observer) => observers.remove(observer)
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
