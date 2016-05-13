package org.rebeam.boxes.persistence

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._


/**
* Provides replacing of type T
*/
@implicitNotFound(msg = "Cannot find Replaces type class for ${T}")
trait Replaces[T] {
  /**
   * Inspect an object of type T, and if it "contains" the Box with specified
   * boxId, return a script that will attempt to replace the contents of that
   * Box with the content read from available tokens, unless the next available
   * token is EndToken, in which case the script may immediately return Unit.
   * This handling of EndToken allows us to provide the script
   * with tokens representing exactly one instance of T, so that the contents of
   * the Box will be replaced exactly once, using up the tokens and resulting
   * in no additional attempts to replace the Box contents if the same Box is
   * found elsewhere in the object graph, and resulting in early return from the
   * script if possible.
   *
   * If object does NOT contain the Box with specified boxId, recursively
   * call appropriate Replace typeclass on all objects accessible from the object,
   * until it is found.
   *
   * This is analogous to writing an object, in that we traverse the object graph,
   * but we don't produce any output, then when we find a specific box we become
   * a reader script that just replaces the box contents in-place, and returns
   * Unit.
   *
   * @param t      The object in which to replace
   * @param boxId  The id of the Box to be replaced
   * @return       A script to replace box contents from read tokens
   */
  def replace(t: T, boxId: Long): BoxReaderScript[Unit]
}


