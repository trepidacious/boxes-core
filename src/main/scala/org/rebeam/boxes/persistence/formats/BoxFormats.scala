package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

import scala.language.implicitConversions

private object BoxFormatUtils {

  def write[T](box: Box[T], linkStrategy: LinkStrategy, writes: Writes[T]) = {
    import BoxWriterDeltaF._
    linkStrategy match {
      case AllLinks => cacheBox(box) flatMap {
        case Cached(id) => put(BoxToken(LinkRef(id)))
        case New(id) => for {
          _ <- put(BoxToken(LinkId(id)))
          v <- get(box)
          _ <- writes.write(v)
        } yield ()
      }

      case EmptyLinks => cacheBox(box) flatMap {
        case Cached(id) => throw new BoxCacheException("Box id " + id + " was already cached, but boxLinkStrategy is EmptyLinks")
        case New(id) => for {
          _ <- put(BoxToken(LinkEmpty))
          v <- get(box)
          _ <- writes.write(v)
        } yield ()
      }

      case IdLinks => cacheBox(box) flatMap {
        case Cached(id) => throw new BoxCacheException("Box id " + id + " was already cached, but boxLinkStrategy is IdLinks")
        case New(id) => for {
          _ <- put(BoxToken(LinkId(id)))
          v <- get(box)
          _ <- writes.write(v)
        } yield ()
      }
    }
  }

  def read[T](linkStrategy: LinkStrategy, reads: Reads[T]) = {
    import BoxReaderDeltaF._
    pull flatMap {
      case BoxToken(link) =>
        link match {
          case LinkEmpty =>
            if (linkStrategy == IdLinks || linkStrategy == AllLinks) {
              throw new BoxCacheException("Found a Box LinkEmpty but boxLinkStrategy is " + linkStrategy)
            }
            for {
              v <- reads.read
              b <- create(v)
            } yield b

          case LinkId(id) =>
            if (linkStrategy == EmptyLinks) {
              throw new BoxCacheException("Found a Box LinkId (" + id + ") but boxLinkStrategy is " + linkStrategy)
            }
            for {
              v <- reads.read
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
  
  def replace[T](box: Box[T], boxId: Long, format: Format[T]) = {
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
  
}

object BoxFormatsEmptyLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new Format[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, EmptyLinks, format)
    def read = BoxFormatUtils.read(EmptyLinks, format)
    def replace(box: Box[T], boxId: Long) = BoxFormatUtils.replace(box, boxId, format)
  }
}

object BoxFormatsIdLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new Format[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, IdLinks, format)
    def read = BoxFormatUtils.read(IdLinks, format)
    def replace(box: Box[T], boxId: Long) = BoxFormatUtils.replace(box, boxId, format)
  }
}

object BoxFormatsAllLinks {
  implicit def boxFormat[T](implicit format: Format[T]): Format[Box[T]] = new Format[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, AllLinks, format)
    def read = BoxFormatUtils.read(AllLinks, format)
    def replace(box: Box[T], boxId: Long) = BoxFormatUtils.replace(box, boxId, format)
  }
}
