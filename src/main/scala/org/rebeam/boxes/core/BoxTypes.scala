package org.rebeam.boxes.core

import scalaz.Free

object BoxTypes {
  
  type BoxScript[A] = Free[BoxDeltaF, A]
  implicit val boxDeltaFunctor = BoxDeltaF.functor

  type BoxReaderScript[A] = Free[BoxReaderDeltaF, A]
  implicit val boxReaderDeltaFunctor = BoxReaderDeltaF.boxReaderDeltaFunctor

  type BoxWriterScript[A] = Free[BoxWriterDeltaF, A]
  implicit val boxWriterDeltaFunctor = BoxWriterDeltaF.boxWriterDeltaFunctor

  type BoxObserverScript[A] = Free[BoxObserverDeltaF, A]
  implicit val boxObserverDeltaFunctor = BoxObserverDeltaF.boxObserverDeltaFunctor

}