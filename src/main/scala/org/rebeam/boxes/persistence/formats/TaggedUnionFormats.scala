package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scala.language.implicitConversions

/**
  * Functions for making Formats for union types, e.g. ADTs, by tagging the
  * type with a string
  */
object TaggedUnionFormats {

  /**
    * Combine a tag and value (and an implicit format), to produce something covariant...
    */
  case class Tagged[+T](tag: String, t: T)(implicit f: Format[T]) {
    val write = f.write(t)
    def replace(boxId: Long) = f.replace(t, boxId)
    def modify(boxId: Long) = f.modify(t, boxId)
  }
  
  implicit def readAs[A, B >: A](r: Reads[A]): Reads[B] = new Reads[B] {
    def read: BoxReaderScript[B] = r.read.map((a: A) => a)
  }
  
  def taggedUnionFormat[T](r: (String) => Option[Reads[T]], w: (T) => Tagged[T]): Format[T] = new Format[T] {
    
    def write(t: T): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._

      val vaf = w(t)
      for {
        _ <- put(OpenDict())
        _ <- put(DictEntry(vaf.tag))
        _ <- vaf.write
        _ <- put(CloseDict)
      } yield ()
    }

    def replace(t: T, boxId: Long) = w(t).replace(boxId)
    def modify(t: T, boxId: Long) = w(t).modify(boxId)
    
    def read: BoxReaderScript[T] = {
      import BoxReaderDeltaF._
    
      for {
        _ <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        
        tag <- pull.map(t => t match {
          case DictEntry(s, LinkEmpty) => s
          case x => throw new IncorrectTokenException("Expected a DictEntry(s, LinkEmpty), got " + x)
        })
    
        reads = r(tag).get
    
        t <- reads.read
        
        _ <- pullExpected(CloseDict)
        
      } yield t
    }
    
  }
  
}