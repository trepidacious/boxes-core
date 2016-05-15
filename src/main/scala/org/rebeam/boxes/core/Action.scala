package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import org.rebeam.boxes.core._

import scala.annotation.implicitNotFound
import scala.language.implicitConversions


/**
 * Provides a BoxScript that acts on a T
 */
trait Action[T] {
  def act(b: T): BoxScript[Unit]
}


