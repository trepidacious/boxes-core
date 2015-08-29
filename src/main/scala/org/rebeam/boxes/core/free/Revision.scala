package org.rebeam.boxes.core.free

import scala.collection.immutable._

import BoxTypes._

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
        val newChange = new BoxChange(newIndex)
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

    //Apply attach/detach deltas to update boxReactions
    val newBoxReactions = deltas.deltas.foldLeft(boxReactions){
      case (br, AttachReactionToBox(r, b)) => br.updated(b.id, boxReactions.getOrElse(b.id, Set.empty) + r)
      case (br, DetachReactionFromBox(r, b)) => br.updated(b.id, boxReactions.getOrElse(b.id, Set.empty) - r)
      case (br, _) => br
    }

    //Where boxes have been GCed, also remove the entry in boxReactions for that box - we only want the
    //boxReactions maps to retain reactions for revisions while the boxes are still reachable
    val prunedBoxReactions = deletes.foldLeft(newBoxReactions) { case (m, id) => m - id }

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
