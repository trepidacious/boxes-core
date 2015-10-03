package org.rebeam.boxes.core

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._

object BoxObserverScriptInterpreter {
  
  /**
   * Run a script using data from a Revision, supporting only reading of boxes, producing a result
   * @param script        The script to run
   * @param rev           The revision from which to read
   * @tparam A            The result type of the script
   * @return              Script result
   */
  @tailrec final def run[A](script: BoxObserverScript[A], rev: Revision, reads: Set[Long]): (A, Set[Long]) = script.resume match {

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val value = b.get(rev)
      val next = toNext(value)
      run(next, rev, reads + b.id)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      run(next, rev, reads)

    case \/-(x) => (x.asInstanceOf[A], reads)
  }

}
