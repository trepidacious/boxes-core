package org.rebeam.boxes.core

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._
import scala.language.implicitConversions

import scalaz._
import Scalaz._

object BoxScriptImports {

  //These methods on box only make sense when we are using a BoxScript  
  implicit class BoxInScript[A](b: Box[A]) {

    def attachReaction(reaction: Reaction) = BoxDeltaF.attachReactionToBox(reaction, b)
    def detachReaction(reaction: Reaction) = BoxDeltaF.detachReactionFromBox(reaction, b)

    def applyReaction(rScript: BoxScript[A]) = for {
      r <- BoxScriptImports.createReaction(for {
        t <- rScript
        _ <- set(b, t)
      } yield ())
      _ <- b.attachReaction(r)
    } yield r

    def modify(f: A => A) = BoxScriptImports.modify(b, f)

    final def widen[B >: A]: BoxScript[B] = b.r.map((a: A) => a: B)

    final def partial[B](pf: PartialFunction[A, B]): BoxScript[Option[B]] = optional(pf.lift)
    final def optional[B](f: A => Option[B]): BoxScript[Option[B]] = b.r.map(f)

  }

  implicit class BoxOptionInScript[A](b: Box[Option[A]]) {
    def default[B](d: A): BoxScript[A] = for {
      oa <- b()
    } yield oa.getOrElse(d)
  }

  //Smart constructors for BoxScript
  def create[T](t: T)                               = BoxDeltaF.create(t)
  def set[T](box: Box[T], t: T)                     = BoxDeltaF.set(box, t)
  def get[T](box: Box[T])                           = BoxDeltaF.get(box)
  def observe(observer: Observer)                   = BoxDeltaF.observe(observer)
  def unobserve(observer: Observer)                 = BoxDeltaF.unobserve(observer)
  def createReaction(action: BoxScript[Unit])       = BoxDeltaF.createReaction(action)
  def attachReactionToBox(r: Reaction, b: Box[_])   = BoxDeltaF.attachReactionToBox(r, b)
  def detachReactionFromBox(r: Reaction, b: Box[_]) = BoxDeltaF.detachReactionFromBox(r, b)
  val changedSources                                = BoxDeltaF.changedSources
  def just[T](t: T)                                 = BoxDeltaF.just(t)
  val nothing                                       = BoxDeltaF.nothing
  val revisionIndex                                 = BoxDeltaF.revisionIndex

  def modify[T](b: BoxM[T], f: T => T) = for {
    o <- b.read
    _ <- b.write(f(o))
  } yield o

  implicit class BoxScriptPlus[A](s: BoxScript[A]) {
    final def andThen[B](f: => BoxScript[B]): BoxScript[B] = s flatMap (_ => f)
    final def widen[B >: A]: BoxScript[B] = s.map((a: A) => a: B)
    final def partial[B](pf: PartialFunction[A, B]): BoxScript[Option[B]] = optional(pf.lift)
    final def optional[B](f: A => Option[B]): BoxScript[Option[B]] = s.map(f)
  }

  implicit class BoxScriptOptionPlus[A](s: BoxScript[Option[A]]) {
    def map2[B](f: A => B): BoxScript[Option[B]] = s.map(_.map(f))
    def default[B](d: A): BoxScript[A] = for {
      oa <- s
    } yield oa.getOrElse(d)
  }

  //Simplest path - we have a BoxScript that finds us a BoxM[T], and we will read and write using it.
  def path[T](p: BoxScript[BoxM[T]]): BoxM[T] = BoxM(
    p.flatMap(_.read),          //Get our BoxM, then use its read
    a => p.flatMap(_.write(a))  //Get our BoxM, then use its write
  )

  //Accepting a box directly
  def pathB[T](p: BoxScript[Box[T]]): BoxM[T] = path(p.map(_.m))

  //More complex - we have a BoxScript that may not always point to a BoxM[T]. To represent this we produce a BoxM[Option[T]].
  //When the BoxScript points to None, we will read as None and ignore writes.
  //When the BoxScript points to Some, we will use it for reads and writes.
  def pathViaOption[T](p: BoxScript[Option[BoxM[T]]]): BoxM[Option[T]] = BoxM(
    for {
      obm <- p                      //Get Option[BoxM[T]] from path script
      ot <- obm.traverseU(_.read)   //traverse uses BoxM[T] => X[T] to get us from an Option[BoxM[T]] to an X[Option[T]]. 
                                    //We have read, which is BoxM[T] => BoxScript[T], so we can get from Option[BoxM[T]] to BoxScript[Option[T]]
    } yield ot,                     //And finally yield this to get a BoxScript

    a => a match {
      case None => nothing              //Cannot set source BoxM to None, so do nothing
      case Some(a) => p.flatMap{        
        obm => obm match {
          case Some(bm) => bm.write(a)  //If we currently have a BoxM to use, write to it
          case None => nothing          //If we have no BoxM, do nothing
        }
      }
    }
  )

  //Accepting a box directly
  def pathViaOptionB[T](p: BoxScript[Option[Box[T]]]): BoxM[Option[T]] = pathViaOption(p.map(_.map(_.m)))

  //Most complex case - when the script may not produce a BoxM, and that BoxM itself contains an optional type,
  //we follow the same approach as for pathViaOption, but we additionally flatten
  //the Option[Option]
  def pathToOption[T](p: BoxScript[Option[BoxM[Option[T]]]]): BoxM[Option[T]] = BoxM(
    for {
      obm <- p                      
      ot <- obm.traverseU(_.read).map(_.flatten)    //Note we flatten the Option[Option[T]] to Option[T]
    } yield ot,

    a => p.flatMap {
      obm => obm match {
        case None => nothing
        case Some(bm) => bm.write(a)
      }
    }
  )

  //Accepting a box directly
  def pathToOptionB[T](p: BoxScript[Option[Box[Option[T]]]]): BoxM[Option[T]] = pathToOption(p.map(_.map(_.m)))

  // implicit class omapOnBoxScriptOption[A, B](s: BoxScript[Option[A]]) {
  //   def omap(field: A => Box[B]): BoxScript[Option[Box[B]]] = s.map(_.map(field))
  // }

  implicit def BoxToBoxR[A](box: Box[A]): BoxR[A] = box.r
  implicit def BoxToBoxW[A](box: Box[A]): BoxW[A] = box.w
  implicit def BoxToBoxM[A](box: Box[A]): BoxM[A] = box.m
}