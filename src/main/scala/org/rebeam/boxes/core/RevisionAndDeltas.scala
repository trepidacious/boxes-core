package org.rebeam.boxes.core

import org.rebeam.boxes.core.BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scalaz.{\/-, -\/}
import BoxDelta._

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
  @tailrec final def appendScript[A](script: BoxScript[A], rad: RevisionAndDeltas, boxDeltas: BoxDeltas, runReactions: Boolean = true, changedSources: Set[Box[_]] = Set.empty): (RevisionAndDeltas, A, BoxDeltas) = script.resume match {

    case -\/(CreateBoxDeltaF(t, toNext)) =>
      val (deltas, box) = rad.create(t)
      val next = toNext(box)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val (deltas, value) = rad.get(b)
      val next = toNext(value)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(WriteBoxDeltaF(b, t, next)) =>
      val (deltas, box) = rad.set(b, t)
      val rad2 = rad.appendDeltas(deltas)
      //TODO - we can probably just skip calling react for efficiency if the write does not
      //change box state. Note that the reactor itself uses its own mechanism
      //to observe writes when applying reactions, unlike in old mutable-txn
      //system
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      appendScript(next, rad3, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(CreateReactionDeltaF(action, toNext)) =>
      val (deltas, reaction) = rad.createReaction(action)
      val next = toNext(reaction)
      val rad2 = rad.appendDeltas(deltas)
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      appendScript(next, rad3, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ObserveDeltaF(obs, next)) =>
      val deltas = rad.observe(obs)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(UnobserveDeltaF(obs, next)) =>
      val deltas = rad.unobserve(obs)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(AttachReactionToBoxF(r, b, next)) =>
      val deltas = rad.attachReactionToBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(DetachReactionFromBoxF(r, b, next)) =>
      val deltas = rad.detachReactionFromBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      appendScript(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ChangedSourcesF(toNext)) =>
      val next = toNext(changedSources)
      appendScript(next, rad, boxDeltas, runReactions, changedSources)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      appendScript(next, rad, boxDeltas, runReactions, changedSources)

    case \/-(x) => (rad, x.asInstanceOf[A], boxDeltas)
  }
}

case class RevisionAndDeltas(revision: Revision, deltas: BoxDeltas) {

  def create[T](t: T): (BoxDeltas, Box[T]) = {
    val box = Box[T]()
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
  def appendScript[A](script: BoxScript[A], runReactions: Boolean = true, changedSources: Set[Box[_]] = Set.empty): (RevisionAndDeltas, A, BoxDeltas) = RevisionAndDeltas.appendScript[A](script, this, BoxDeltas.empty, runReactions, changedSources)

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

