package org.rebeam.boxes.core

import org.rebeam.boxes.core.BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scalaz.{\/-, -\/}
import BoxDelta._

case class RevisionAndDeltas(revision: Revision, deltas: BoxDeltas) {

  def create[T](t: T): (BoxDeltas, Box[T]) = {
    val box = Box.newInstance[T]()
    (BoxDeltas.single(BoxCreated(box, t)), box)
  }

  private def _get[T](box: Box[T]): T = deltas.boxWrite(box).getOrElse(revision.valueOf(box).getOrElse(
    throw new RuntimeException("Missing Box for id " + box.id)
  ))

  def set[T](box: Box[T], t: T): (BoxDeltas, Box[T]) = {
    //We need to record the write to have reactions work properly etc., however the interpreter
    //is free to ignore writes to same value to optimise when they would make no difference
    (BoxDeltas.single(BoxWritten(box, t, _get(box))), box)
  }

  def get[T](box: Box[T]): (BoxDeltas, T) = {
    val v = _get(box)
    (BoxDeltas.single(BoxRead(box, v)), v)
  }

  def scriptForReactionId(rid: Long): Option[BoxScript[Unit]] = deltas.scriptForReactionId(rid).orElse(revision.scriptForReactionId(rid))

  def observe(observer: Observer): BoxDeltas = BoxDeltas.single(Observed(observer))
  def unobserve(observer: Observer): BoxDeltas = BoxDeltas.single(Unobserved(observer))

  def attachReactionToBox[T](r: Reaction, b: Box[T]): BoxDeltas = BoxDeltas.single(ReactionAttachedToBox(r, b))
  def detachReactionFromBox[T](r: Reaction, b: Box[T]): BoxDeltas = BoxDeltas.single(ReactionDetachedFromBox(r, b))

  def createReaction(action: BoxScript[Unit]): (BoxDeltas, Reaction) = {
    val reaction = Reaction()
    (BoxDeltas.single(ReactionCreated(reaction, action)), reaction)
  }

  def reactionGraph: ReactionGraph = deltas.reactionGraph.getOrElse(revision.reactionGraph)

  /**
   * Return true if this revision delta alters the revision it is applied to
   */
  def altersRevision = deltas.altersRevision

  /** Create a new RevisionAndDeltas with same revision, but with new deltas appended to our own */
  def appendDeltas(d: BoxDeltas) = RevisionAndDeltas(revision, deltas.append(d))

  /** Run a script and append the deltas it generates to this instance, creating a new RevisionAndDeltas, a script result and the new deltas added by the script */
  def appendScript[A](script: BoxScript[A], runReactions: Boolean = true, changedSources: Set[Box[_]] = Set.empty): (RevisionAndDeltas, A, BoxDeltas) = BoxScript.run[A](script, this, BoxDeltas.empty, runReactions, changedSources)

  def deltasWouldChange(newDeltas: BoxDeltas): Boolean = newDeltas.deltas.exists{
    case BoxWritten(b, t, _) => get(b) != t
    case ReactionCreated(_, _) => true
    case BoxCreated(_, _) => true
    case Observed(_) => true
    case Unobserved(_) => true
    case BoxRead(_, _) => false
    case ReactionGraphUpdated(_) => false
    case ReactionAttachedToBox(_, _) => false
    case ReactionDetachedFromBox(_, _) => false
  }

  override def toString = "RevisionAndDeltas(" + revision.toString + "," + deltas + ")"
}

