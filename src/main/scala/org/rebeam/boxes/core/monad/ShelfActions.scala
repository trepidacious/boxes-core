package org.rebeam.boxes.core.monad

import scalaz._

object ShelfActions {
  def create[T](t: T): State[RevisionDelta, Box[T]] = State(_.create(t))
  def set[T](box: Box[T], t: T): State[RevisionDelta, Box[T]] = State(_.set(box, t))
  def get[T](box: Box[T]): State[RevisionDelta, T] = State(_.get(box))
  def atomic[A](s: State[RevisionDelta, A]): A = Shelf.atomic(s)
  def observe(observer: Observer): State[RevisionDelta, Observer]  = State(_.observe(observer))
  def unobserve(observer: Observer): State[RevisionDelta, Observer]  = State(_.unobserve(observer))
}
