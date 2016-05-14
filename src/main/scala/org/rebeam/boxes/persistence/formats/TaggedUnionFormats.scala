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
  
  def taggedUnionFormat[T](writesSource: (T) => (String, Format[T]), r: (String) => Option[Format[T]]): Format[T] = new Format[T] {
    
    def write(t: T): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._

      val (tag, writes) = writesSource(t)
      for {
        _ <- put(OpenDict())
        _ <- put(DictEntry(tag))
        _ <- writes.write(t)
        _ <- put(CloseDict)
      } yield ()
    }
    
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
      } yield t
    }
    
    def replace(t: T, boxId: Long) = writesSource(t)._2.replace(t, boxId)
    def modify(t: T, boxId: Long) = writesSource(t)._2.modify(t, boxId)
    def modifyBox(b: Box[T]) = BoxReaderDeltaF.nothing
  }
  
}