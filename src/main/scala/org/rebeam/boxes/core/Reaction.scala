package org.rebeam.boxes.core

import java.util.concurrent.atomic.AtomicInteger

import util.BiMultiMap

import scala.collection.immutable.Set

case class Reaction(id: Long) extends Identifiable

object Reaction {
  private val nextId = new AtomicInteger(0)
  def apply(): Reaction = Reaction(nextId.getAndIncrement())
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