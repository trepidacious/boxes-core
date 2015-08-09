package org.rebeam.boxes.core.monad

import org.rebeam.boxes.core.Box

import scalaz.State

object OmitShelf {
  def create[T](t: T)(implicit s: ShelfMonad): State[s.RevisionDelta, Box[T]] = s.create(t)
  def set[T](box: Box[T], t: T)(implicit s: ShelfMonad): State[s.RevisionDelta, Box[T]] = s.set(box, t)
  def get[T](box: Box[T])(implicit s: ShelfMonad): State[s.RevisionDelta, T] = s.get(box)
}
