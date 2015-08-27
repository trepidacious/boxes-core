package org.rebeam.boxes.core.free

import scalaz.Free

object BoxTypes {
  type BoxScript[A] = Free[BoxDeltaF, A]
}