package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import scala.annotation.implicitNotFound

/**
* Provides reading of type T
*/
@implicitNotFound(msg = "Cannot find Reads or Format type class for ${T}")
trait Reads[T] {
 /**
  * Produce a script that will read an object of type T from available
  * tokens.
  * @return        The object we've read
  */
 def read: BoxReaderScript[T]
}
