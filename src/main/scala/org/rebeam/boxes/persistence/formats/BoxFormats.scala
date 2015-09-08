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
}

object BoxFormatsEmptyLinks {
  implicit def writesBox[T](implicit writes: Writes[T]): Writes[Box[T]] = new Writes[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, EmptyLinks, writes)
  }
  implicit def readsBox[T](implicit reads: Reads[T]): Reads[Box[T]] = new Reads[Box[T]] {
    def read = BoxFormatUtils.read(EmptyLinks, reads)
  }
  implicit def boxFormat[T](implicit reads: Reads[T], writes: Writes[T]): Format[Box[T]] = new Format[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, EmptyLinks, writes)
    def read = BoxFormatUtils.read(EmptyLinks, reads)
  }
}

object BoxFormatsIdLinks {
  implicit def writesBox[T](implicit writes: Writes[T]): Writes[Box[T]] = new Writes[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, IdLinks, writes)
  }
  implicit def readsBox[T](implicit reads: Reads[T]): Reads[Box[T]] = new Reads[Box[T]] {
    def read = BoxFormatUtils.read(IdLinks, reads)
  }
  implicit def boxFormat[T](implicit reads: Reads[T], writes: Writes[T]): Format[Box[T]] = new Format[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, IdLinks, writes)
    def read = BoxFormatUtils.read(IdLinks, reads)
  }
}

object BoxFormatsAllLinks {
  implicit def writesBox[T](implicit writes: Writes[T]): Writes[Box[T]] = new Writes[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, AllLinks, writes)
  }

  implicit def readsBox[T](implicit reads: Reads[T]): Reads[Box[T]] = new Reads[Box[T]] {
    def read = BoxFormatUtils.read(AllLinks, reads)
  }

  implicit def boxFormat[T](implicit reads: Reads[T], writes: Writes[T]): Format[Box[T]] = new Format[Box[T]] {
    def write(box: Box[T]) = BoxFormatUtils.write(box, AllLinks, writes)
    def read = BoxFormatUtils.read(AllLinks, reads)
  }
}
