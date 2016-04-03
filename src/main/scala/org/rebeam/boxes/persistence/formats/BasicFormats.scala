package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

object BasicFormats {

  implicit def writesOption[T](implicit writes: Writes[T]): Writes[Option[T]] = new Writes[Option[T]] {
    import BoxWriterDeltaF._

    def write(option: Option[T]) = {
      option match {
        case Some(v) => writes.write(v)
        case None => put(NoneToken)
      }
    }
  }

  implicit def readsOption[T](implicit reads: Reads[T]) = new Reads[Option[T]] {
    import BoxReaderDeltaF._

    //Alternative form
    // def read: BoxReaderScript[Option[T]] = peek flatMap (token => token match {
    //   case NoneToken => pullExpected(NoneToken) map (_ => None)
    //   case _ => reads.read map (Some(_))
    // })

    def read: BoxReaderScript[Option[T]] = for {
      t <- peek
      r <- t match {
        case NoneToken => pullExpected(NoneToken) map (_ => None)
        case _ => reads.read map (Some(_))
      } 
    } yield r

  }

  implicit def replacesOption[T](implicit replaces: Replaces[T]): Replaces[Option[T]] = new Replaces[Option[T]] {
    import BoxReaderDeltaF._    
    def replace(option: Option[T], boxId: Long) = option match {
      case Some(v) => replaces.replace(v, boxId)
      case None => nothing
    }
  }

  implicit def optionFormat[T](implicit format: Format[T]) = new Format[Option[T]] {
    override def read = readsOption[T].read
    override def write(obj: Option[T]) = writesOption[T].write(obj)
    override def replace(option: Option[T], boxId: Long) = replacesOption[T].replace(option, boxId)
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
  }

}