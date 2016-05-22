package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scala.language.implicitConversions

object BasicFormats {

  implicit def optionFormat[T](implicit format: Format[T]) = new Format[Option[T]] {
    import BoxReaderDeltaF._    

    override def read: BoxReaderScript[Option[T]] = for {
      t <- peek
      r <- t match {
        case NoneToken => pullExpected(NoneToken) map (_ => None)
        case _ => format.read map (Some(_))
      } 
    } yield r
    
    override def write(option: Option[T]) = {
      option match {
        case Some(v) => format.write(v)
        case None => BoxWriterDeltaF.put(NoneToken)
      }
    }
    
    override def replace(option: Option[T], boxId: Long) = option match {
      case Some(v) => format.replace(v, boxId)
      case None => nothing
    }

    def modify(option: Option[T], id: Long): BoxReaderScript[Unit] = option match {
      case Some(v) => format.modify(v, id)
      case None => nothing
    }

  }

  /**
   * Lazy format for nested/recursive case classes/nodes
   * To use, wrap the normal Format for the recursive data, add a Format[T] annotation, and assign to a lazy val or def, e.g.
   *
   * case class Nested(i: Int, n: Option[Nested])
   * implicit def nestedFormat: Format[Nested] = lazyFormat(productFormat2(Nested.apply)("i", "n"))
   *
   * This breaks the cycle that would otherwise result in a compile time error or runtime stack overflow.
   *
   * @param format A non-lazy format to which to lazily delegate
   */
  def lazyFormat[T](format: => Format[T]) = new Format[T] {
    lazy val delegate = format
    def write(t: T) = delegate.write(t)
    def read = delegate.read
    def replace(t: T, boxId: Long) = delegate.replace(t, boxId)
    def modify(t: T, id: Long) = delegate.modify(t, id)
  }

}