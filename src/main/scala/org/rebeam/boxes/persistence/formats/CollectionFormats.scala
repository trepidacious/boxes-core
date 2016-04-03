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
    def write(map: Map[K, V]) = writesMap[K, V].write(map)
    def read = readsMap[K, V].read
    def replace(map: Map[K, V], boxId: Long) = replacesMap[K, V].replace(map, boxId)
  }

  implicit def replacesMap[K, V](implicit replacesK: Replaces[K], replacesV: Replaces[V]): Replaces[Map[K, V]] = new Replaces[Map[K, V]] {
    import BoxReaderDeltaF._
    //TODO why do we need this? It should be in BoxTypes
    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    def replaceEntry(entry: (K, V), boxId: Long): BoxReaderScript[Unit] = for {
      _ <- replacesK.replace(entry._1, boxId)
      _ <- replacesV.replace(entry._2, boxId)
    } yield ()
  
    def replace(map: Map[K, V], boxId: Long): BoxReaderScript[Unit] = for {
      _ <- map.toList traverseU (e => replaceEntry(e, boxId))
    } yield ()
  }


  implicit def writesMap[K, V](implicit writesK: Writes[K], writesV: Writes[V]): Writes[Map[K, V]] = new Writes[Map[K, V]] {
    
    def writeEntry(entry: (K, V)) = for {
      _ <- put(openEntryDict)
      _ <- put(DictEntry("key"))
      _ <- writesK.write(entry._1)
      _ <- put(DictEntry("value"))
      _ <- writesV.write(entry._2)
      _ <- put(CloseDict)      
    } yield ()

    def write(map: Map[K, V]) = for {
      _ <- put(OpenArr(PresentationName("Map")))
      _ <- map.toList traverseU (writeEntry(_))
      _ <- put(CloseArr)
    } yield ()
  }

  implicit def readsMap[K, V](implicit readsK: Reads[K], readsV: Reads[V]) = new Reads[Map[K, V]] {

    val readKeyOrValue: BoxReaderScript[Either[K, V]] = pull flatMap {
      case DictEntry("key", LinkEmpty) => readsK.read.map(Left(_))
      case DictEntry("value", LinkEmpty) => readsV.read.map(Right(_))
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

  implicit def writesList[T](implicit writes: Writes[T]) = new Writes[List[T]] {
    def write(list: List[T]) = for {
      _ <- put(OpenArr(PresentationName("List")))
      _ <- list traverseU (writes.write(_))
      _ <- put(CloseArr)
    } yield ()
  }

  implicit def readsList[T](implicit reads: Reads[T]): Reads[List[T]] = new Reads[List[T]] {

    //FIXME make this tail recursive, or trampoline, or something?
    def readEntries(entries: Vector[T]): BoxReaderScript[Vector[T]] = for {
      t <- peek
      newEntries <- if (t == CloseArr) {
        pullExpected(CloseArr) flatMap ( _ => just(entries))
      } else {
        for {
          entry <- reads.read
          entries <- readEntries(entries :+ entry)
        } yield entries
      }
    } yield newEntries

    def read =  pull flatMap {
      case OpenArr(_) => readEntries(Vector.empty)
      case _ => throw new IncorrectTokenException("Expected OpenArr at start of List[_]")
    } map (_.toList)

  }
  
  implicit def replacesList[T](implicit replaces: Replaces[T]) = new Replaces[List[T]] {
    import BoxReaderDeltaF._
    //TODO why do we need this? It should be in BoxTypes
    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    def replace(list: List[T], boxId: Long) = for {
      _ <- list traverseU (replaces.replace(_, boxId))
    } yield ()
  }

  implicit def listFormat[T](implicit format: Format[T]): Format[List[T]] = new Format[List[T]] {
    override def read = readsList[T].read
    override def write(obj: List[T]) = writesList[T].write(obj)
    override def replace(obj: List[T], boxId: Long) = replacesList[T].replace(obj, boxId)
  }


  //FIXME refactor to use same code as for list, and then add any additional versions, e.g. Vector
  implicit def writesSet[T](implicit writes: Writes[T]) = new Writes[Set[T]] {
    def write(set: Set[T]) = for {
      _ <- put(OpenArr(PresentationName("Set")))
      _ <- set.toList traverseU (writes.write(_))
      _ <- put(CloseArr)
    } yield ()
  }

  implicit def readsSet[T](implicit reads: Reads[T]): Reads[Set[T]] = new Reads[Set[T]] {

    //FIXME make this tail recursive, or trampoline, or something?
    def readEntries(entries: Vector[T]): BoxReaderScript[Vector[T]] = for {
      t <- peek
      newEntries <- if (t == CloseArr) {
        pullExpected(CloseArr) flatMap ( _ => just(entries))
      } else {
        for {
          entry <- reads.read
          entries <- readEntries(entries :+ entry)
        } yield entries
      }
    } yield newEntries

    def read =  pull flatMap {
      case OpenArr(_) => readEntries(Vector.empty)
      case _ => throw new IncorrectTokenException("Expected OpenArr at start of List[_]")
    } map (_.toSet)

  }

  implicit def replacesSet[T](implicit replaces: Replaces[T]) = new Replaces[Set[T]] {
    import BoxReaderDeltaF._
    //TODO why do we need this? It should be in BoxTypes
    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    def replace(set: Set[T], boxId: Long) = for {
      _ <- set.toList traverseU (replaces.replace(_, boxId))
    } yield ()
  }

  implicit def setFormat[T](implicit format: Format[T]): Format[Set[T]] = new Format[Set[T]] {
    override def read = readsSet[T].read
    override def write(obj: Set[T]) = writesSet[T].write(obj)
    override def replace(obj: Set[T], boxId: Long) = replacesSet[T].replace(obj, boxId)
  }

  //This is the higher-priority writer for Maps that have string keys, it can use a more compact representation
  //that should also be more idiomatic in e.g. JSON and XML
  implicit def writesStringKeyedMap[V](implicit writes: Writes[V]): Writes[Map[String, V]] = new Writes[Map[String, V]] {

    def writeEntry(entry: (String, V)) = for {
      _ <- put(DictEntry(entry._1))
      _ <- writes.write(entry._2)
    } yield ()

    def write(map: Map[String, V]) = for {
      _ <- put(OpenDict(PresentationName("Map")))
      _ <- map.toList traverseU (writeEntry(_))
      _ <- put(CloseDict)
    } yield ()

  }

  implicit def readsStringKeyedMap[V](implicit reads: Reads[V]): Reads[Map[String, V]] = new Reads[Map[String, V]] {

    val readEntry: BoxReaderScript[(String, V)] = for {
      first <- pull
      value <- reads.read
    } yield (first, value) match {
        case (DictEntry(key, LinkEmpty), value) => (key, value)
        case _ => throw new IncorrectTokenException("Expected DictEntry(key, LinkEmpty) at start of Map[String, _] entry")      
    }

    //FIXME make this tail recursive, or trampoline, or something?
    def readEntries(entries: Vector[(String, V)]): BoxReaderScript[Vector[(String, V)]] = for {
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
    
  }
  
  implicit def replacesStringKeyedMap[V](implicit replacesV: Replaces[V]): Replaces[Map[String, V]] = new Replaces[Map[String, V]] {
    import BoxReaderDeltaF._
    //TODO why do we need this? It should be in BoxTypes
    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    def replaceEntry(entry: (String, V), boxId: Long): BoxReaderScript[Unit] = for {
      _ <- replacesV.replace(entry._2, boxId)
    } yield ()
  
    def replace(map: Map[String, V], boxId: Long): BoxReaderScript[Unit] = for {
      _ <- map.toList traverseU (e => replaceEntry(e, boxId))
    } yield ()
  }


  implicit def stringKeyedMapFormat[V](implicit formatV: Format[V]): Format[Map[String, V]] = new Format[Map[String, V]] {
    def write(map: Map[String, V]) = writesStringKeyedMap[V].write(map)
    def read = readsStringKeyedMap[V].read
    def replace(map: Map[String, V], boxId: Long) = replacesStringKeyedMap[V].replace(map, boxId)
  }

}
