package org.rebeam.boxes.persistence.formats

import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

//See http://stackoverflow.com/questions/4403906/is-it-possible-in-scala-to-force-the-caller-to-specify-a-type-parameter-for-a-po for
//an explanation of why we structure implicits this way.
//As a brief summary, we want the compiler to prefer the most specific implicits (e.g. the Writes[Map[String, V]] function should be
//preferred to the Writes[Map[K, V]] one since it only exists to make this specific case neater). Hence we define the fallback general
//Writes and Reads here, and the more specific ones later.
trait LowPriorityCollectionFormats {

  import BoxReaderDeltaF._
  import BoxWriterDeltaF.put

  val openEntryDict = OpenDict(PresentationName("Entry"), LinkEmpty)

  //This is the lower-priority format for maps, works in all cases but needs to use more boiler-plate in representation of non-string keys.
  implicit def mapFormat[K, V](implicit formatK: Format[K], formatV: Format[V]): Format[Map[K, V]] = new Format[Map[K, V]] {

    //Replaces

    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor //TODO why do we need this? It should be in BoxTypes

    def replaceEntry(entry: (K, V), boxId: Long): BoxReaderScript[Unit] = for {
      _ <- formatK.replace(entry._1, boxId)
      _ <- formatV.replace(entry._2, boxId)
    } yield ()
  
    def replace(map: Map[K, V], boxId: Long): BoxReaderScript[Unit] = for {
      _ <- map.toList traverseU (e => replaceEntry(e, boxId))
    } yield ()

    def modifyEntry(entry: (K, V), id: Long): BoxReaderScript[Unit] = for {
      _ <- formatK.modify(entry._1, id)
      _ <- formatV.modify(entry._2, id)
    } yield ()

    def modify(map: Map[K, V], id: Long): BoxReaderScript[Unit] = for {
      _ <- map.toList traverseU (e => modifyEntry(e, id))
    } yield ()

    //Writes

    def writeEntry(entry: (K, V)) = for {
      _ <- put(openEntryDict)
      _ <- put(DictEntry("key"))
      _ <- formatK.write(entry._1)
      _ <- put(DictEntry("value"))
      _ <- formatV.write(entry._2)
      _ <- put(CloseDict)      
    } yield ()

    def write(map: Map[K, V]) = for {
      _ <- put(OpenArr(PresentationName("Map")))
      _ <- map.toList traverseU (writeEntry(_))
      _ <- put(CloseArr)
    } yield ()


    //Reads

    val readKeyOrValue: BoxReaderScript[Either[K, V]] = pull flatMap {
      case DictEntry("key", LinkEmpty) => formatK.read.map(Left(_))
      case DictEntry("value", LinkEmpty) => formatV.read.map(Right(_))
      case _ => throw new IncorrectTokenException("Expected DictEntry(value, LinkEmpty) in Map[_, _] entry dict")
    }

    val readKeyAndValue: BoxReaderScript[(K, V)] = for {
      first <- readKeyOrValue
      second <- readKeyOrValue
    } yield (first, second) match {
        case (Left(key), Right(value)) => (key, value)
        case (Right(value), Left(key)) => (key, value)
        case _ => throw new IncorrectTokenException("Expected dictionary of key and value in Map[_, _] entry")      
    }

    //FIXME make this tail recursive, or trampoline, or something?
    def readEntries(entries: Vector[(K, V)]): BoxReaderScript[Vector[(K, V)]] = for {
      t <- peek
      newEntries <- if (t == CloseArr) {
        pullExpected(CloseArr) flatMap ( _ => just(entries))
      } else {
        for {
          _ <- pullExpected(openEntryDict)
          entry <- readKeyAndValue
          _ <- pullExpected(CloseDict)
          entries <- readEntries(entries :+ entry)
        } yield entries
      }
    } yield newEntries

    def read = pull flatMap {
      case OpenArr(_) => readEntries(Vector.empty)
      case _ => throw new IncorrectTokenException("Expected OpenArr at start of Map[_, _]")
    } map (entries => Map(entries: _*))
  
  }

}

object CollectionFormats extends LowPriorityCollectionFormats {

  import BoxReaderDeltaF._
  import BoxWriterDeltaF.put

  implicit def listFormat[T](implicit format: Format[T]): Format[List[T]] = new Format[List[T]] {
    //FIXME make this tail recursive, or trampoline, or something?
    private def readEntries(entries: Vector[T]): BoxReaderScript[Vector[T]] = for {
      t <- peek
      newEntries <- if (t == CloseArr) {
        pullExpected(CloseArr) flatMap ( _ => just(entries))
      } else {
        for {
          entry <- format.read
          entries <- readEntries(entries :+ entry)
        } yield entries
      }
    } yield newEntries

    override def read =  pull flatMap {
      case OpenArr(_) => readEntries(Vector.empty)
      case _ => throw new IncorrectTokenException("Expected OpenArr at start of List[_]")
    } map (_.toList)
    
    override def write(list: List[T]) = for {
      _ <- put(OpenArr(PresentationName("List")))
      _ <- list traverseU (format.write(_))
      _ <- put(CloseArr)
    } yield ()
    
    //TODO why do we need this? It should be in BoxTypes
    private implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    override def replace(list: List[T], boxId: Long) = for {
      _ <- list traverseU (format.replace(_, boxId))
    } yield ()

    override def modify(list: List[T], id: Long) = for {
      _ <- list traverseU (format.modify(_, id))
    } yield ()

  }


  //FIXME refactor to use same code as for list, and then add any additional versions, e.g. Vector
  implicit def setFormat[T](implicit format: Format[T]): Format[Set[T]] = new Format[Set[T]] {

    //FIXME make this tail recursive, or trampoline, or something?
    private def readEntries(entries: Vector[T]): BoxReaderScript[Vector[T]] = for {
      t <- peek
      newEntries <- if (t == CloseArr) {
        pullExpected(CloseArr) flatMap ( _ => just(entries))
      } else {
        for {
          entry <- format.read
          entries <- readEntries(entries :+ entry)
        } yield entries
      }
    } yield newEntries

    override def read =  pull flatMap {
      case OpenArr(_) => readEntries(Vector.empty)
      case _ => throw new IncorrectTokenException("Expected OpenArr at start of List[_]")
    } map (_.toSet)

    override def write(set: Set[T]) = for {
      _ <- put(OpenArr(PresentationName("Set")))
      _ <- set.toList traverseU (format.write(_))
      _ <- put(CloseArr)
    } yield ()

    
    private implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor  //TODO why do we need this? It should be in BoxTypes
    override def replace(set: Set[T], boxId: Long) = for {
      _ <- set.toList traverseU (format.replace(_, boxId))
    } yield ()

    override def modify(set: Set[T], id: Long) = for {
      _ <- set.toList traverseU (format.modify(_, id))
    } yield ()

  }

  //This is the higher-priority format for Maps that have string keys, it can use a more compact representation
  //that should also be more idiomatic in e.g. JSON and XML
  implicit def stringKeyedMapFormat[V](implicit formatV: Format[V]): Format[Map[String, V]] = new Format[Map[String, V]] {

    private def writeEntry(entry: (String, V)) = for {
      _ <- put(DictEntry(entry._1))
      _ <- formatV.write(entry._2)
    } yield ()

    def write(map: Map[String, V]) = for {
      _ <- put(OpenDict(PresentationName("Map")))
      _ <- map.toList traverseU (writeEntry(_))
      _ <- put(CloseDict)
    } yield ()

    private val readEntry: BoxReaderScript[(String, V)] = for {
      first <- pull
      value <- formatV.read
    } yield (first, value) match {
        case (DictEntry(key, LinkEmpty), value) => (key, value)
        case _ => throw new IncorrectTokenException("Expected DictEntry(key, LinkEmpty) at start of Map[String, _] entry")      
    }

    //FIXME make this tail recursive, or trampoline, or something?
    private def readEntries(entries: Vector[(String, V)]): BoxReaderScript[Vector[(String, V)]] = for {
      t <- peek
      newEntries <- if (t == CloseDict) {
        pullExpected(CloseDict) flatMap ( _ => just(entries))
      } else {
        for {
          _ <- pullExpected(openEntryDict)
          entry <- readEntry
          entries <- readEntries(entries :+ entry)
        } yield entries
      }
    } yield newEntries

    def read = pull flatMap {
      case OpenArr(_) => readEntries(Vector.empty)
      case _ => throw new IncorrectTokenException("Expected OpenArr at start of Map[_, _]")
    } map (entries => Map(entries: _*))
    
    
    private implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor  //TODO why do we need this? It should be in BoxTypes
    private def replaceEntry(entry: (String, V), boxId: Long): BoxReaderScript[Unit] = for {
      _ <- formatV.replace(entry._2, boxId)
    } yield ()
  
    def replace(map: Map[String, V], boxId: Long): BoxReaderScript[Unit] = for {
      _ <- map.toList traverseU (e => replaceEntry(e, boxId))
    } yield ()

    private def modifyEntry(entry: (String, V), id: Long): BoxReaderScript[Unit] = for {
      _ <- formatV.modify(entry._2, id)
    } yield ()

    def modify(map: Map[String, V], id: Long): BoxReaderScript[Unit] = for {
      _ <- map.toList traverseU (e => modifyEntry(e, id))
    } yield ()

  }

}
