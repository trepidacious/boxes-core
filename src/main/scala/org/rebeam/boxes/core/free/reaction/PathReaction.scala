package org.rebeam.boxes.core.free.reaction

import org.rebeam.boxes.core.util.{OptionTConverter, TConverter, GConverter}

import org.rebeam.boxes.core.free._
import BoxTypes._
import BoxUtils._

import scalaz._
import Scalaz._

object PathReaction {

  def react[T, G](v: Box[G], path: BoxScript[Option[Box[T]]], defaultValue: G, c: GConverter[G, T]): BoxScript[Unit] = for {
    pathEnd <- path
    _ <- pathEnd match {
      //If the path is disconnected, revert to default
      case None => v() = defaultValue

      //Otherwise do mirroring
      case Some(e) => for {
        eContents <- e().map(c.toG(_))
        vContents <- v()
        cs <- changedSources()

        //Only propagate from v to e if only v has changed - otherwise, propagate from e to v.
        //This means that only changes directly to v that have NOT altered the path OR the endpoint
        //will be applied back to the endpoint.
        _ <- if (cs != Set(v)) {
          v() = eContents
        } else {
          //Take vContents of type G up to a definite Option, then we
          //can check whether it is None, and otherwise extract its
          //value as type T to copy to e
          c.toOption(vContents) match {
            case None => v() = eContents
            case Some(vValue) => e() = vValue
          }
        }
      } yield ()
    }
  } yield ()
}

object Path {
  def apply[T](path : BoxScript[Option[Box[T]]]) = PathViaOption.apply(path)
  def apply[T](path : BoxScript[Box[T]])(implicit d: DummyImplicit) = PathToBox.apply(path)
  def apply[T](path : BoxScript[Option[Box[Option[T]]]])(implicit d1: DummyImplicit, d2: DummyImplicit) = PathToOption.apply(path)
}

object PathToBox {
  def apply[T](path: BoxScript[Box[T]]): BoxScript[Box[T]] = for {
    e <- path
    eValue <- e()
    box <- create(eValue)
    //Here we have both v and endpoint as parametric type T, so no need for
    //any conversion - use a TConverter. We do raise the path to an Option, but
    //since it always works we just use Some(path). Default value doesn't matter since
    //it is never used. Apologies for the null, but it really is NEVER used. Could
    //use eVal instead, but this potentially creates a memory leak.
    r <- createReaction(PathReaction.react[T, T](box, path.map(Some(_)), null.asInstanceOf[T], new TConverter[T]))
    _ <- box.attachReaction(r)
  } yield box
}

/**
 * Creates paths that go TO a Var[Option[T]], AND may also go VIA an option.
 * If the path goes via an option, it may yield None, in which case the
 * produced Var will contain None.
 * If the path yields Some(Var[Option[T]]) then the produced Var will
 * contain the value of the Var[Option[T]], which may be Some(tValue) or
 * None.
 *
 * Note this is (slightly confusingly) equivalent to PathWithDefault(path, None),
 * it just makes explicit that T from pathWithDefault is now Option[T], and the
 * defaultValue is None. This is probably the most common way of using a path
 * that leads to a Var[Option[T]].
 */
object PathToOption {
  def apply[T](path: BoxScript[Option[Box[Option[T]]]]): BoxScript[Box[Option[T]]] = for {
    box <- create[Option[T]](None)
    //Not going to pretend this isn't confusing... here we use a TConverter
    //because we are producing v with parametric type Option[T], and using
    //a path to an endpoint with parametric type Option[T]. There is hence no
    //mismatch in levels of Option, and we don't need to convert anything. So
    //the "T" in "TConverter" is actually "Option[T]" in this case.
    r <- createReaction(PathReaction.react[Option[T], Option[T]](box, path, None, new TConverter[Option[T]]))
    _ <- box.attachReaction(r)
  } yield box
}

/**
 * Creates paths that go VIA an option, but lead to a Var that contains a
 * nonoptional value. So this covers the case where following the path
 * may yield either None, OR a Var[T] for some non-optional type T.
 * To allow for this, the returned Var is a Var[Option[T]], which contains
 * Some(tValue) when the path yields a Var[T], and None when the
 * path yields None.
 */
object PathViaOption {
  def apply[T](path: BoxScript[Option[Box[T]]]): BoxScript[Box[Option[T]]] = for {
    box <- create[Option[T]](None)
    //Here we have to raise values in our endpoint Var (of parametric type T)
    //up to Option[T], so we use an OptionTConverter.
    r <- createReaction(PathReaction.react[T, Option[T]](box, path, None, new OptionTConverter[T]))
    _ <- box.attachReaction(r)
  } yield box
}
