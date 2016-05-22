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
    linkStrategy match {
      case AllLinks => cache(box) flatMap {
        case ExistingId(id) => put(BoxToken(LinkRef(id)))
        case NewId(id) => for {
          _ <- put(BoxToken(LinkId(id)))
          v <- get(box)
          _ <- format.write(v)
        } yield ()
      }

      case EmptyLinks => cache(box) flatMap {
        case ExistingId(id) => throw new BoxCacheException("Box id " + id + " was already cached, but boxLinkStrategy is EmptyLinks")
        case NewId(id) => for {
          _ <- put(BoxToken(LinkEmpty))
          v <- get(box)
          _ <- format.write(v)
        } yield ()
      }

      case IdLinks => cache(box) flatMap {
        case ExistingId(id) => throw new BoxCacheException("Box id " + id + " was already cached, but boxLinkStrategy is IdLinks")
        case NewId(id) => for {
          _ <- put(BoxToken(LinkId(id)))
          v <- get(box)
          _ <- format.write(v)
        } yield ()
      }
    }
  }

  override def read = {
    import BoxReaderDeltaF._
    pull flatMap {
      case BoxToken(link) =>
        link match {
          case LinkEmpty =>
            if (linkStrategy == IdLinks || linkStrategy == AllLinks) {
              throw new BoxCacheException("Found a Box LinkEmpty but boxLinkStrategy is " + linkStrategy)
            }
            for {
              v <- format.read
              b <- create(v)
            } yield b

          case LinkId(id) =>
            if (linkStrategy == EmptyLinks) {
              throw new BoxCacheException("Found a Box LinkId (" + id + ") but boxLinkStrategy is " + linkStrategy)
            }
            for {
              v <- format.read
              b <- create(v)              
              _ <- putCachedBox(id, b)
            } yield b

          case LinkRef(id) =>
            if (linkStrategy == IdLinks || linkStrategy == EmptyLinks) {
              throw new BoxCacheException("Found a Box LinkRef( " + id + ") but boxLinkStrategy is " + linkStrategy)
            }
            getCachedBox(id) map (_.asInstanceOf[Box[T]])
        }

      case _ => throw new IncorrectTokenException("Expected BoxToken at start of Box[_]")
    }
  }
  
  override def replace(box: Box[T], boxId: Long) = {
    import BoxReaderDeltaF._
    //If this is our box, read a new value for it from tokens, set that new 
    //value and we are done
    if (box.id == boxId) {
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
  }

  override def modify(box: Box[T], boxId: Long) = {
    import BoxReaderDeltaF._
    // //If this is our box, delegate to modifyBox based on its type
    // if (box.id == boxId) {
    //   format.modifyBox(box)
    // 
    // //If this is not our box, recurse to its contents
    // } else {
      for {
        t <- get(box)
        _ <- format.modify(t, boxId)
      } yield ()
    // }
  }

  //No modification for a Box[Box[T]] - firstly, probably don't use this type,
  //secondly, just use replace if you have to change contents
  // override def modifyBox(b: Box[Box[T]]): BoxReaderScript[Unit] = BoxReaderDeltaF.nothing

}

object BoxFormatsEmptyLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new BoxFormat[T](EmptyLinks)
}

object BoxFormatsIdLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new BoxFormat[T](IdLinks)
}

object BoxFormatsAllLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new BoxFormat[T](AllLinks)
}
