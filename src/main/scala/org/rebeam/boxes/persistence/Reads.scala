package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import scala.annotation.implicitNotFound

/**
* Provides reading of type T
*/
@implicitNotFound(msg = "Cannot find Reads or Format type class for ${T}")
trait Reads[T] {
 /**
  * Read an object from the context, returning the object read
  * @param context The context from which to read
  * @return        The object we've read
  */
 def read: BoxReaderScript[T]
}
