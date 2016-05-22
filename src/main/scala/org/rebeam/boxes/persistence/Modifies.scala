package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import org.rebeam.boxes.core._

import scala.annotation.implicitNotFound
import scala.language.implicitConversions


/**
 * Provides modification of objects, recursively
 */
@implicitNotFound(msg = "Cannot find Modifies type class for ${T}")
trait Modifies[-T] {
  /**
    * Inspect an object of type T, and if it has the specified id, as found
    * using getId, return a script that will attempt to modify that object using the
    * content read from available tokens, unless the next available
    * token is EndToken, in which case the script may immediately return Unit.
    * This handling of EndToken allows us to provide the script
    * with tokens representing exactly one instance of T, so that the modification
    * will be performed exactly once, using up the tokens and resulting
    * in no additional attempts to perform modifications if the same object is
    * found elsewhere in the object graph, and resulting in early return from the
    * script if possible.
    *
    * If object does NOT match the specified id, recursively call approprite 
    * Modifies typeclass modify methods on all objects accessible from the object,
    * until it is found.
    *
    * This is analogous to writing an object, in that we traverse the object graph,
    * but we don't produce any output, then when we find a specific object we become
    * a reader script that modifies the object, and returns Unit.
    *
    * @param t      The object in which to search for the object to modify
    * @param id     The id of the item to be modified (as found using getId)
    * @return       A script to modify object from read tokens
    */
  def modify(t: T, id: Long): BoxReaderScript[Unit]

}


