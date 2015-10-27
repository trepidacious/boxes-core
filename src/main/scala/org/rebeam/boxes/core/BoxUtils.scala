package org.rebeam.boxes.core

object BoxUtils {
  import BoxTypes._

  def atomic[A](s: BoxScript[A]): A = Shelf.atomic(s)

  def atomicToRevisionAndResult[A](s: BoxScript[A]): (Revision, A) = Shelf.atomicToRevisionAndResult(s)

  def atomicToRevision(s: BoxScript[Unit]): Revision = Shelf.atomicToRevision(s)

  // implicit class NumericBox[N](v: Box[N])(implicit n: Numeric[N]) {

  //   def from(min: N) = v.applyReaction(v.get().map(n.max(min, _)))
  //   def to(max: N) = v.applyReaction(v.get().map(n.min(max, _)))

  //   def from(min: Box[N]) = v.applyReaction(for {
  //     value <- v()
  //     minimum <- min()
  //   } yield n.max(minimum, value))

  //   def to(max: Box[N]) = v.applyReaction(for {
  //     value <- v()
  //     maximum <- max()
  //   } yield n.min(maximum, value))

  //   def clip(min: N, max: N) = v.applyReaction(
  //     v().map(value =>
  //       if (n.compare(value, min) < 0) min
  //       else if (n.compare(value, max) > 0) max
  //       else value
  //     )
  //   )
  // }

}
