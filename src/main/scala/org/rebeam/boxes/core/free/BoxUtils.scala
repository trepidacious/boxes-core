package org.rebeam.boxes.core.free

import scalaz.Free

object BoxTypes {
  type BoxScript[A] = Free[BoxDeltaF, A]
}

object BoxUtils {
  import BoxTypes._

  def atomic[A](s: BoxScript[A]): A = Shelf.atomic(s)

  def create[T](t: T)                     = BoxDeltaF.create(t)
  def set[T](box: Box[T], t: T)           = BoxDeltaF.set(box, t)
  def get[T](box: Box[T])                 = BoxDeltaF.get(box)
  def observe(observer: Observer)         = BoxDeltaF.observe(observer)
  def unobserve(observer: Observer)       = BoxDeltaF.unobserve(observer)
  def createReaction(action: BoxScript[Unit]) = BoxDeltaF.createReaction(action)

}
