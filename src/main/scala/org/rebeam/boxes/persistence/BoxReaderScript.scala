package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._

object BoxReaderScript {
  
  /**
   * Run a script using data from a Revision, and writing data to a mutable TokenWriter, producing a result
   * @param script    The script to run
   * @param rad       The initial RevisionAndDeltas
   * @param runReactions  True to run reactions when they are created, or when boxes are written. False to ignore reactions
   * @tparam A        The result type of the script
   * @return          (new RevisionAndDeltas, script result)
   */
  @tailrec final def run[A](
    script: BoxReaderScript[A], 
    rad: RevisionAndDeltas, boxDeltas: BoxDeltas, 
    reader: TokenReader,
    ids: Ids): (RevisionAndDeltas, A, BoxDeltas, TokenReader, Ids) = script.resume match {

    case -\/(CreateBoxDeltaF(t, toNext)) =>
      val (deltas, box) = rad.create(t)
      val next = toNext(box)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val (deltas, value) = rad.get(b)
      val next = toNext(value)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(WriteBoxDeltaF(b, t, next)) =>
      val (deltas, box) = rad.set(b, t)
      val rad2 = rad.appendDeltas(deltas)
      //Note we don't run reactions during reading, since we may have
      //transient inconsistent states - we run one pass of all reactions
      //at the end
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(CreateReactionDeltaF(action, toNext)) =>
      val (deltas, reaction) = rad.createReaction(action)
      val next = toNext(reaction)
      val rad2 = rad.appendDeltas(deltas)
      //No reactions here either, run at end
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(AttachReactionToBoxF(r, b, next)) =>
      val deltas = rad.attachReactionToBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(DetachReactionFromBoxF(r, b, next)) =>
      val deltas = rad.detachReactionFromBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)

    case -\/(PeekTokenF(toNext)) =>
      val t = reader.peek
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullTokenF(toNext)) =>
      val t = reader.pull
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullBooleanF(toNext)) =>
      val t = reader.pullBoolean
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullIntF(toNext)) =>
      val t = reader.pullInt
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullLongF(toNext)) =>
      val t = reader.pullLong
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullFloatF(toNext)) =>
      val t = reader.pullFloat
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullDoubleF(toNext)) =>
      val t = reader.pullDouble
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullBigIntF(toNext)) =>
      val t = reader.pullBigInt
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullBigDecimalF(toNext)) =>
      val t = reader.pullBigDecimal
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)      

    case -\/(PullStringF(toNext)) =>
      val t = reader.pullString
      val next = toNext(t)
      run(next, rad, boxDeltas, reader, ids)

    case -\/(EmbedBoxScript(script, toNext)) =>
      val (rad2, t, deltas) = rad.appendScript(script, false, Set.empty)
      val next = toNext(t)
      run(next, rad2, boxDeltas.append(deltas), reader, ids)

    case -\/(RevisionIndexF(toNext)) =>
      val next = toNext(rad.revision.index)
      run(next, rad, boxDeltas, reader, ids)

    case -\/(GetIdF(thing, toNext)) =>
      val id = ids.idFor(thing)
      val next = toNext(id)
      run(next, rad, boxDeltas, reader, ids)

    case \/-(x) => 
      //We apply all pending reactions at the end, using the entire accumulated
      //set of deltas from the entire BoxReaderScript, to get all newly created
      //reactions in place (plus potentially update any pre-existing) reactions
      //sourcing boxes we may have changed.
      val rad2 = Reactor.react(rad, boxDeltas)

      (rad2, x.asInstanceOf[A], boxDeltas, reader, ids)
  }

}
