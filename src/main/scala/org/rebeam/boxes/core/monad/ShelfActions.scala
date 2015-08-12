package org.rebeam.boxes.core.monad

import BoxDeltasMonad._

object ShelfActions {
  def create[T](t: T): BoxDeltasMonad[Box[T]] = boxDeltasMonad(_.create(t))
  def set[T](box: Box[T], t: T): BoxDeltasMonad[Box[T]] = boxDeltasMonad(_.set(box, t))
  def get[T](box: Box[T]): BoxDeltasMonad[T] = boxDeltasMonad(_.get(box))
  def atomic[A](s: BoxDeltasMonad[A]): A = Shelf.atomic(s)
  def observe(observer: Observer): BoxDeltasMonad[Observer]  = boxDeltasMonad(_.observe(observer))
  def unobserve(observer: Observer): BoxDeltasMonad[Observer]  = boxDeltasMonad(_.unobserve(observer))
}
