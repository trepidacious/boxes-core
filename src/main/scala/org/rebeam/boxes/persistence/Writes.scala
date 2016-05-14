package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import scala.annotation.implicitNotFound

/**
* Provides writing of type T (or a anything <: T, contravariant)
*/
@implicitNotFound(msg = "Cannot find Writes or Format type class for ${T}")
trait Writes[-T] {
 /**
  * Write an object to the context
  * @param obj     The object to write
  * @return A script performing the writing
  */
 def write(obj: T): BoxWriterScript[Unit]
}
