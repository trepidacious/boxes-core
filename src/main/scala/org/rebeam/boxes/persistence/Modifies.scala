package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import org.rebeam.boxes.core._

import scala.annotation.implicitNotFound
import scala.language.implicitConversions


/**
 * Provides modification of Box[T], recursively
 */
@implicitNotFound(msg = "Cannot find Modifies type class for ${T}")
trait Modifies[-T] {
  /**
    * Inspect an object of type T, and if it "contains" the Box with specified
    * boxId, return a script that will attempt to modify that box using the
    * content read from available tokens, unless the next available
    * token is EndToken, in which case the script may immediately return Unit.
    * This handling of EndToken allows us to provide the script
    * with tokens representing exactly one instance of T, so that the contents of
    * the Box will be replaced exactly once, using up the tokens and resulting
    * in no additional attempts to replace the Box contents if the same Box is
    * found elsewhere in the object graph, and resulting in early return from the
    * script if possible.
    *
    * The choice of how to modify the box is left to the implementer of this
    * function, however a common pattern will be to ensure that for any bu: Box[U]
    * we have an mu: Modifies[U] available, and we can then simply call mu.modifyBox(bu).
    * This allows the handling of modifications of a Box[U] to be handled by the
    * Modifies typeclass for the type of box contents, rather than the typeclass for
    * the item containing that box (e.g. a Node). This normally makes sense, but
    * as stated above if it makes more sense to perform the modification "one level
    * up" in the data graph then Modifies implementations may choose to do
    * so, performing the modification in this modify method, and ignoring the
    * modifyBox method of the typeclass for the box contents.
    *
    * If object does NOT contain the Box with specified boxId, recursively
    * call approprite Modifies typeclass modify methods on all objects accessible from the object,
    * until it is found.
    *
    * This is analogous to writing an object, in that we traverse the object graph,
    * but we don't produce any output, then when we find a specific box we become
    * a reader script that modifies the box contents in-place, and returns
    * Unit.
    *
    * @param t      The object in which to modify a box
    * @param boxId  The id of the Box to be modified
    * @return       A script to modify box contents from read tokens
    */
  def modify(t: T, boxId: Long): BoxReaderScript[Unit]

}


