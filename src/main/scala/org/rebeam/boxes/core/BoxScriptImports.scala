package org.rebeam.boxes.core

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._
import scala.language.implicitConversions

object BoxScriptImports {
  
  implicit class BoxInScript[T](b: Box[T]) {
    // /** Get the value of this box in the context of a BoxScript - can be used in for-comprehensions */
    // def get() = BoxDeltaF.get(b)

    // /**
    //  * Set the value of this box in the context of a State - can be used in for-comprehensions
    //  * to set Box value in associated RevisionDelta
    //  */
    // def set(t: T) = BoxDeltaF.set(b, t)

    // def apply() = get()

    // def update(t: T) = set(t)

    def attachReaction(reaction: Reaction) = BoxDeltaF.attachReactionToBox(reaction, b)
    def detachReaction(reaction: Reaction) = BoxDeltaF.detachReactionFromBox(reaction, b)

    def applyReaction(rScript: BoxScript[T]) = for {
      r <- BoxScriptImports.createReaction(for {
        t <- rScript
        _ <- set(b, t)
      } yield ())
      _ <- b.attachReaction(r)
    } yield r

    def modify(f: T => T) = BoxScriptImports.modify(b, f)

  }

  def create[T](t: T)                               = BoxDeltaF.create(t)
  def set[T](box: Box[T], t: T)                     = BoxDeltaF.set(box, t)
  def get[T](box: Box[T])                           = BoxDeltaF.get(box)
  def observe(observer: Observer)                   = BoxDeltaF.observe(observer)
  def unobserve(observer: Observer)                 = BoxDeltaF.unobserve(observer)
  def createReaction(action: BoxScript[Unit])       = BoxDeltaF.createReaction(action)
  def attachReactionToBox(r: Reaction, b: Box[_])   = BoxDeltaF.attachReactionToBox(r, b)
  def detachReactionFromBox(r: Reaction, b: Box[_]) = BoxDeltaF.detachReactionFromBox(r, b)
  def changedSources()                              = BoxDeltaF.changedSources()
  def just[T](t: T)                                 = BoxDeltaF.just(t)
  val nothing                                       = BoxDeltaF.nothing

  def modify[T](b: Box[T], f: T => T) = for {
    o <- get(b)
    _ <- set(b, f(o))
  } yield o

  def cal[T](script: BoxScript[T]) = for {
    initial <- script
    box <- create(initial)
    reaction <- createReaction{
      for {
        result <- script
        _ <- set(box, result)
      } yield ()
    }
    _ <- box.attachReaction(reaction) //Attach the reaction to the box it updates, so that it will
                                      //not be GCed as long as the box is around. Remember that reactions
                                      //are not retained just by reading from or writing to boxes.
  } yield box

  implicit class BoxScriptPlus[A](s: BoxScript[A]) {
    final def andThen[B](f: => BoxScript[B]): BoxScript[B] = s flatMap (_ => f)
  }

  implicit def BoxToBoxR[A](box: Box[A]): BoxR[A] = box.r
  implicit def BoxToBoxW[A](box: Box[A]): BoxW[A] = box.w
  implicit def BoxToBoxM[A](box: Box[A]): BoxM[A] = box.m
}