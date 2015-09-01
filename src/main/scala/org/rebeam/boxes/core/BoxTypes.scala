package org.rebeam.boxes.core

import scalaz.Free

object BoxTypes {
  type BoxScript[A] = Free[BoxDeltaF, A]
  implicit val boxDeltaFFunctor = BoxDeltaF.functor
}