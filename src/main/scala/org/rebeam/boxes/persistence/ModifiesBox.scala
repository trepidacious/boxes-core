package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import org.rebeam.boxes.core._

import scala.annotation.implicitNotFound
import scala.language.implicitConversions


/**
 * Provides modification of Box[T] value
 */
@implicitNotFound(msg = "Cannot find ModifiesBox type class for ${T}")
trait ModifiesBox[T] {

  /**
    * Produce a script to modify a given box using the available tokens.
    * If box should not be modified, produce nothing (i.e. script doing nothing)
    * If no tokens are available (EndToken is returned), produce nothing.
    *
    * This is called when a box is successfully located, and so should be
    * modified. Since this is a Box[T], the Modifies[T] typeclass will
    * know how to modify it. This is different to modify function above,
    * which is responsible for finding the box to modify, then calling
    * through to modifyBox to make the actual modification.
    *
    * This is not recursive.
    *
    * @param b  The box to modify
    * @return   A script to modify the box, using available tokens
    */
  def modifyBox(b: Box[T]): BoxReaderScript[Unit]

}


