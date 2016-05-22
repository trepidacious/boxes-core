package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

import scala.language.implicitConversions

private class BoxFormat[T](linkStrategy: LinkStrategy)(implicit format: Format[T]) extends Format[Box[T]] {

  override def write(box: Box[T]) = {
    import BoxWriterDeltaF._
    for {
      id <- getId(box)
      _ <- put(BoxToken(linkStrategy.link(id)))
      v <- get(box)
      _ <- format.write(v)
    } yield ()
  }

  override def read = {
    import BoxReaderDeltaF._
    pull flatMap {
      case BoxToken(_) =>
        for {
          v <- format.read
          b <- create(v)
        } yield b

      case _ => throw new IncorrectTokenException("Expected BoxToken at start of Box[_]")
    }
  }
  
  override def replace(box: Box[T], boxId: Long) = {
    import BoxReaderDeltaF._
    //If this is our box, read a new value for it from tokens, set that new 
    //value and we are done
    for {
      id <- getId(box)
      _ <- if (id == boxId) {
        for {
          t <- peek
          //If we have some data to read, read it and use values
          _ <- if (t != EndToken) {
            for {
              newT <- format.read
              _ <- set(box, newT)
            } yield ()
            
          //There is no data left, so nothing to do - just return immediately
          } else {
            nothing            
          }
        } yield ()
        
      //If this is not our box, recurse to its contents
      } else {
        for {
          t <- get(box)
          _ <- format.replace(t, boxId)
        } yield ()
      }
    } yield ()
  }
  
  //We don't support modification of the box itself, so just go to contents
  override def modify(box: Box[T], id: Long) = {
    import BoxReaderDeltaF._
    for {
      t <- get(box)
      _ <- format.modify(t, id)
    } yield ()
  }

}

object BoxFormatsEmptyLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new BoxFormat[T](EmptyLinks)
}

object BoxFormatsIdLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new BoxFormat[T](IdLinks)
}
