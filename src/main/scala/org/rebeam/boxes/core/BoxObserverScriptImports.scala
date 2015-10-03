package org.rebeam.boxes.core

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._

object BoxObserverScriptImports {
  
  implicit class BoxInObserverScript[T](b: Box[T]) {
    /** Get the value of this box in the context of a BoxScript - can be used in for-comprehensions */
    def get() = BoxObserverDeltaF.get(b)

    def apply() = get()
  }

  def get[T](box: Box[T])                           = BoxObserverDeltaF.get(box)
  def just[T](t: T)                                 = BoxObserverDeltaF.just(t)
  val nothing                                       = BoxObserverDeltaF.nothing

  implicit class BoxObserverScriptPlus[A](s: BoxObserverScript[A]) {
    final def andThen[B](f: => BoxObserverScript[B]): BoxObserverScript[B] = s flatMap (_ => f)
  }
}