package org.rebeam.boxes.core

import scala.collection.immutable._

import BoxTypes._
import BoxDelta._

class Revision(val index: Long, val map: Map[Long, BoxChange], reactionMap: Map[Long, BoxScript[Unit]], val reactionGraph: ReactionGraph, val boxReactions: Map[Long, Set[Reaction]]) {

  def stateOf[T](box: Box[T]): Option[BoxState[T]] = for {
    change <- map.get(box.id)
    value <- box.getValue(change)
  } yield BoxState(change.revision, value)

  def indexOf(box: Box[_]): Option[Long] = map.get(box.id).map(_.revision)
  def indexOfId(id: Long): Option[Long] = map.get(id).map(_.revision)

  def valueOf[T](box: Box[T]): Option[T] = {
    val change = map.get(box.id)
    change.flatMap(c => box.getValue(c))
  }

  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]] = reactionMap.get(rid)

  private[core] def updated(deltas: BoxDeltas, deletes: List[Long], reactionDeletes: List[Long]): Option[Revision] = {
    val newIndex = index + 1

    //Remove boxes that have been GCed, then add new ones
    val prunedMap = deletes.foldLeft(map) { case (m, id) => m - id }

    //Apply BoxWritten or BoxCreated deltas - we need to make sure values are in map
    def mWithNewValue(m: Map[Long, BoxChange], box: Box[Any], value: Any) = {
        val newChange = new BoxChange(newIndex)
        box.addChange(newChange, value)
        m.updated(box.id, newChange)      
    }
    val newMap = deltas.deltas.foldLeft(prunedMap) {
      //We can skip writes that do not change box contents
      case (m, BoxWritten(box, value, oldValue)) => if (value != oldValue) mWithNewValue(m, box, value) else m
      case (m, BoxCreated(box, value)) => mWithNewValue(m, box, value)
      case (m, _) => m
    }

    //Remove reactions that have been GCed, then add new ones
    val prunedReactionMap = reactionDeletes.foldLeft(reactionMap) { case (m, id) => m - id }
    val newReactionMap = deltas.deltas.foldLeft(prunedReactionMap) {
      case (m, ReactionCreated(reaction, f)) => m.updated(reaction.id, f)
      case (m, _) => m
    }

    //Apply attach/detach deltas to update boxReactions
    val newBoxReactions = deltas.deltas.foldLeft(boxReactions){
      case (br, ReactionAttachedToBox(r, b)) => br.updated(b.id, boxReactions.getOrElse(b.id, Set.empty) + r)
      case (br, ReactionDetachedFromBox(r, b)) => br.updated(b.id, boxReactions.getOrElse(b.id, Set.empty) - r)
      case (br, _) => br
    }

    //Where boxes have been GCed, also remove the entry in boxReactions for that box - we only want the
    //boxReactions maps to retain reactions for revisions while the boxes are still reachable
    val prunedBoxReactions = deletes.foldLeft(newBoxReactions) { case (m, id) => m - id }

    //Get new reaction graph if there is one in the deltas, otherwise keep our old one, then remove deleted reactions
    val newReactionGraph = deltas.reactionGraph.getOrElse(reactionGraph).removedReactions(reactionDeletes.toSet)

    //Only create an actual new revision if something other than index has changed - otherwise return None to indicate no real changes
    if (newMap == map && newReactionMap == reactionMap && newReactionGraph == reactionGraph && prunedBoxReactions == boxReactions) {
      None
    } else {
      Some(new Revision(newIndex, newMap, newReactionMap, newReactionGraph, prunedBoxReactions))
    }
  }

  def canApplyDelta(rad: RevisionAndDeltas) = {
    val start = rad.revision.index
    //We conflict if the deltas call for reading or writing a box that has changed since the start revision of the
    //RevisionAndDeltas. Note that there is no conflict for reading/writing boxes that don't exist in this revision,
    //since they must be created in the delta
    val conflicts = rad.deltas.deltas.exists{
      case BoxRead(box, _) => indexOf(box).exists(_ > start)
      case BoxWritten(box, _, _) => indexOf(box).exists(_ > start)
      case _ => false
    }

    !conflicts
  }

  override def toString = "Revision(" + index + ")"
}
