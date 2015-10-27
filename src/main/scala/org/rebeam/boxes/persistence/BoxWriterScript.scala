package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._

object BoxWriterScript {
  
  /**
   * Run a script using data from a Revision, and writing data to a mutable TokenWriter, producing a result
   * @param script    The script to run
   * @param rad       The initial RevisionAndDeltas
   * @param runReactions  True to run reactions when they are created, or when boxes are written. False to ignore reactions
   * @tparam A        The result type of the script
   * @return          (new RevisionAndDeltas, script result)
   */
  @tailrec final def run[A](script: BoxWriterScript[A], rev: Revision, writer: TokenWriter): (A, TokenWriter) = script.resume match {

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val value = b.get(rev)
      val next = toNext(value)
      run(next, rev, writer)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      run(next, rev, writer)

    case -\/(PutTokenF(t, next)) =>
      writer.write(t)
      run(next, rev, writer)      

    case -\/(CacheF(thing, toNext)) =>
      val cr = writer.cache(thing)
      val next = toNext(cr)
      run(next, rev, writer)      

    case -\/(CacheBoxF(box, toNext)) =>
      val cr = writer.cacheBox(box.id)
      val next = toNext(cr)
      run(next, rev, writer)      

    case -\/(RevisionIndexF(toNext)) =>
      val next = toNext(rev.index)
      run(next, rev, writer)

    case \/-(x) => (x.asInstanceOf[A], writer)
  }

}
