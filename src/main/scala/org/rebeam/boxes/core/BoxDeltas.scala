package org.rebeam.boxes.core

import org.rebeam.boxes.core.BoxTypes._
import BoxDelta._

import scala.collection.immutable.{Map, Vector}


/** A sequence of BoxDelta instances in order applied, plus append and a potentially faster way of finding the most recent write on a given box */
trait BoxDeltas {
  def deltas: Vector[BoxDelta]

  def append(d: BoxDeltas): BoxDeltas

  def append(d: BoxDelta): BoxDeltas

  /** Get the most recent write on a given box, if there is one, including the initial value of created boxes */
  def boxWrite[T](box: Box[T]): Option[T]

  def altersRevision: Boolean

  def reactionGraph: Option[ReactionGraph]

  /** Get created reaction script for a given id, if there is one */
  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]]
}

/** A BoxDeltas that caches all WriteBox deltas in a map to make boxWrite more efficient */
private class BoxDeltasCachedWrites(val deltas: Vector[BoxDelta], writes: Map[Box[Any], Any], val reactionGraph: Option[ReactionGraph], val idToScriptMap: Map[Long, BoxScript[Unit]]) extends BoxDeltas {

  def append(d: BoxDeltas): BoxDeltas = {
    //Produce new writes cache, updating entries according to any BoxWritten deltas in appended BoxDeltas
    //Note that we also count creation as a write, so that we can retrieve the value
    val newWrites = d.deltas.foldLeft(writes)((writes, delta) => delta match {
      case BoxCreated(box, t) => writes.updated(box, t)
      case BoxWritten(box, t, o) => writes.updated(box, t)
      case _ => writes
    })
    val newReactionGraph = d.deltas.foldLeft(reactionGraph)((rg, delta) => delta match {
      case ReactionGraphUpdated(newGraph) => Some(newGraph)
      case _ => rg
    })
    val newIdToScript = d.deltas.foldLeft(idToScriptMap)((m, delta) => delta match {
      case ReactionCreated(reaction, script) => m.updated(reaction.id, script)
      case _ => m
    })
    new BoxDeltasCachedWrites(deltas ++ d.deltas, newWrites, newReactionGraph, newIdToScript)
  }

  def append(d: BoxDelta): BoxDeltas = {
    //Produce new writes cache, updating entries according to any WriteBox deltas in appended BoxDeltas
    val newWrites = d match {
      case BoxCreated(box, t) => writes.updated(box, t)
      case BoxWritten(box, t, o) => writes.updated(box, t)
      case _ => writes
    }
    val newReactionGraph = d match {
      case ReactionGraphUpdated(newGraph) => Some(newGraph)
      case _ => reactionGraph
    }
    val newIdToScript = d match {
      case ReactionCreated(reaction, script) => idToScriptMap.updated(reaction.id, script)
      case _ => idToScriptMap
    }
    new BoxDeltasCachedWrites(deltas ++ Vector(d), newWrites, newReactionGraph, newIdToScript)
  }

  def boxWrite[T](box: Box[T]): Option[T] = writes.get(box.asInstanceOf[Box[Any]]).asInstanceOf[Option[T]]

  def altersRevision: Boolean = deltas.exists{
    case BoxRead(_, _) => false
    case _ => true
  }

  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]] = idToScriptMap.get(rid)

  override def toString = "BoxDeltasCachedWrites(" + deltas + ", " + writes + ")";
}

object BoxDeltas {
  def empty: BoxDeltas = new BoxDeltasCachedWrites(Vector.empty, Map.empty, None: Option[ReactionGraph], Map.empty)
  def single(d: BoxDelta): BoxDeltas = empty.append(d)
}