package org.rebeam.boxes.core

import scalaz.Free

object BoxTypes {
  
  type BoxScript[A] = Free[BoxDeltaF, A]
  implicit val boxDeltaFunctor = BoxDeltaF.functor

  //Currently just an alias for BoxScript, but in future should be a script that is
  //known only to read (e.g. when we have compositional scripts)

  /**
   * A script that will read a value from box state.
   *
   * Should NOT change any box state.
   *
   * This should be used instead of a Box wherever that Box only needs to be read,
   * since it allows for greater flexibility - it can return a value calculated from
   * one or more Boxes, or even a plain immutable value using just(value).
   * 
   * Currently just an alias for BoxScript, but in future should be a script that is
   * known only to read (e.g. with we have compositional scripts)
   */
  type BoxR[A] = BoxScript[A]

  /**
   * Accepts a new value, and produces a script that will write that value to box state
   * 
   * This should be used instead of a Box wherever that Box only needs to be written,
   * since it allows for greater flexibility - it can write a value to a Box, or it can
   * for example find one or more Boxes using arbitrary state and then write to them, possibly
   * transforming the value first.
   */
  type BoxW[-A] = A => BoxScript[Unit]

  type BoxReaderScript[A] = Free[BoxReaderDeltaF, A]
  implicit val boxReaderDeltaFunctor = BoxReaderDeltaF.boxReaderDeltaFunctor

  type BoxWriterScript[A] = Free[BoxWriterDeltaF, A]
  implicit val boxWriterDeltaFunctor = BoxWriterDeltaF.boxWriterDeltaFunctor

  type BoxObserverScript[A] = Free[BoxObserverDeltaF, A]
  implicit val boxObserverDeltaFunctor = BoxObserverDeltaF.boxObserverDeltaFunctor

}