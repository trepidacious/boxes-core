package org.rebeam.boxes.core.free

import BoxDeltaF.BoxScript

object ShelfActions {
  def atomic[A](s: BoxScript[A]): A = Shelf.atomic(s)
}
