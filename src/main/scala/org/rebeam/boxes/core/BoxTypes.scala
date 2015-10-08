package org.rebeam.boxes.core

import scalaz.Free

object BoxTypes {
  
  type BoxScript[A] = Free[BoxDeltaF, A]
  implicit val boxDeltaFunctor = BoxDeltaF.functor

  //Currently just an alias for BoxScript, but in future should be a script that is
  //known only to read (e.g. with we have compositional scripts)

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
  type BoxW[A] = A => BoxScript[Unit]

  /**
   * Provides a BoxR and BoxW. Will often read and write the "same" state, however doesn't need to.
   * 
   * This should be used instead of a Box wherever that Box needs to be read and written, but
   * no other features of the box are needed (for example the box id). This covers nearly all uses
   * of Boxes.
   *
   * In summary, Box itself should be used when we want to create an actual data storage location,
   * and BoxM (or BoxR/BoxW) when all we want to do is read/write that data. This means the primary
   * use of Box is to create Nodes - case classes where (some or all) fields are Boxes.
   */
  case class BoxM[A](read: BoxScript[A], write: BoxW[A]) {
    def apply() = read
    def update(a: A) = write(a)
  }

  type BoxReaderScript[A] = Free[BoxReaderDeltaF, A]
  implicit val boxReaderDeltaFunctor = BoxReaderDeltaF.boxReaderDeltaFunctor

  type BoxWriterScript[A] = Free[BoxWriterDeltaF, A]
  implicit val boxWriterDeltaFunctor = BoxWriterDeltaF.boxWriterDeltaFunctor

  type BoxObserverScript[A] = Free[BoxObserverDeltaF, A]
  implicit val boxObserverDeltaFunctor = BoxObserverDeltaF.boxObserverDeltaFunctor

}