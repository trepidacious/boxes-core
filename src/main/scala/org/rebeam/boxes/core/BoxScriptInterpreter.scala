package org.rebeam.boxes.core

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._

object BoxScriptInterpreter {
  
  /**
   * Run a script and append the deltas it generates to an existing RevisionAndDeltas, creating a new
   * RevisionAndDeltas and a script result
   * @param script    The script to run
   * @param rad       The initial RevisionAndDeltas
   * @param runReactions  True to run reactions when they are created, or when boxes are written. False to ignore reactions
   * @tparam A        The result type of the script
   * @return          (new RevisionAndDeltas, script result, all deltas applied directly by the script - excluding those from reactions)
   */
  @tailrec final def runReadOnly[A](script: BoxScript[A], r: Revision, reads: Set[Long] = Set.empty): (A, Set[Long]) = script.resume match {

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val value = b.get(r)
      val next = toNext(value)
      runReadOnly(next, r, reads + b.id)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      runReadOnly(next, r, reads)

    case -\/(RevisionIndexF(toNext)) =>
      val next = toNext(r.index)
      runReadOnly(next, r, reads)

    case -\/(s) => throw new RuntimeException("Invalid operation in read-only script: " + s)

    case \/-(x) => (x.asInstanceOf[A], reads)
  }

  /**
   * Run a script and append the deltas it generates to an existing RevisionAndDeltas, creating a new
   * RevisionAndDeltas and a script result
   * @param script    The script to run
   * @param rad       The initial RevisionAndDeltas
   * @param runReactions  True to run reactions when they are created, or when boxes are written. False to ignore reactions
   * @tparam A        The result type of the script
   * @return          (new RevisionAndDeltas, script result, all deltas applied directly by the script - excluding those from reactions)
   */
  @tailrec final def run[A](script: BoxScript[A], rad: RevisionAndDeltas, boxDeltas: BoxDeltas, runReactions: Boolean = true, changedSources: Set[Box[_]] = Set.empty): (RevisionAndDeltas, A, BoxDeltas) = script.resume match {

    case -\/(CreateBoxDeltaF(t, toNext)) =>
      val (deltas, box) = rad.create(t)
      val next = toNext(box)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val (deltas, value) = rad.get(b)
      val next = toNext(value)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(WriteBoxDeltaF(b, t, next)) =>
      val (deltas, box) = rad.set(b, t)
      val rad2 = rad.appendDeltas(deltas)
      //TODO - we can probably just skip calling react for efficiency if the write does not
      //change box state. Note that the reactor itself uses its own mechanism
      //to observe writes when applying reactions, unlike in old mutable-txn
      //system
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      run(next, rad3, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(CreateReactionDeltaF(action, toNext)) =>
      val (deltas, reaction) = rad.createReaction(action)
      val next = toNext(reaction)
      val rad2 = rad.appendDeltas(deltas)
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      run(next, rad3, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ObserveDeltaF(obs, next)) =>
      val deltas = rad.observe(obs)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(UnobserveDeltaF(obs, next)) =>
      val deltas = rad.unobserve(obs)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(AttachReactionToBoxF(r, b, next)) =>
      val deltas = rad.attachReactionToBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(DetachReactionFromBoxF(r, b, next)) =>
      val deltas = rad.detachReactionFromBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ChangedSourcesF(toNext)) =>
      val next = toNext(changedSources)
      run(next, rad, boxDeltas, runReactions, changedSources)

    case -\/(RevisionIndexF(toNext)) =>
      val next = toNext(rad.revision.index)
      run(next, rad, boxDeltas, runReactions, changedSources)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      run(next, rad, boxDeltas, runReactions, changedSources)

    case \/-(x) => (rad, x.asInstanceOf[A], boxDeltas)
  }
}