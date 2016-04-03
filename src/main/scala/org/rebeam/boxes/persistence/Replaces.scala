package org.rebeam.boxes.persistence

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._


/**
* Provides replacing of type T
*/
@implicitNotFound(msg = "Cannot find Replaces type class for ${T}")
trait Replaces[T] {
 /**
  * Inspect an object of type T, and if it "contains" the Box with specified
  * boxId, return a script that will attempt to replace the contents of that
  * Box with the content read from available tokens, unless the next available
  * token is EndToken, in which case the script may immediately return Unit. 
  * This handling of EndToken allows us to provide the script
  * with tokens representing exactly one instance of T, so that the contents of
  * the Box will be replaced exactly once, using up the tokens and resulting
  * in no additional attempts to replace the Box contents if the same Box is
  * found elsewhere in the object graph, and resulting in early return from the
  * script if possible.
  *
  * If object does NOT contain the Box with specified boxId, recursively 
  * call approprite Replace typeclass on all objects accessible from the object,
  * until it is found.
  *
  * This is analogous to writing an object, in that we traverse the object graph,
  * but we don't produce any output, then when we find a specific box we become
  * a reader script that just replaces the box contents in-place, and returns
  * Unit.
  *
  * @param t      The object in which to replace
  * @param boxId  The id of the Box to be replaced
  * @return       A script to replace box contents from read tokens
  */
 def replace(t: T, boxId: Long): BoxReaderScript[Unit]
}

object BoxReplaces {
  import BoxReaderDeltaF._

  implicit def replacesBox[T](implicit replaces: Replaces[T], reads: Reads[T]): Replaces[Box[T]] = new Replaces[Box[T]] {
    def replace(box: Box[T], boxId: Long) = {
      //If this is our box, read a new value for it from tokens, set that new 
      //value and we are done
      if (box.id == boxId) {
        for {
          t <- peek
          //If we have some data to read, read it and use values
          _ <- if (t != EndToken) {
            for {
              newT <- reads.read
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
          _ <- replaces.replace(t, boxId)
        } yield ()
      }
    }
  }
}

object BasicReplaces {
  import BoxReaderDeltaF._    
  implicit def replacesOption[T](implicit replaces: Replaces[T]): Replaces[Option[T]] = new Replaces[Option[T]] {
    def replace(option: Option[T], boxId: Long) = option match {
      case Some(v) => replaces.replace(v, boxId)
      case None => nothing
    }
  }
  
  /**
   * Lazy replace for nested/recursive case classes/nodes
   * To use, wrap the normal Replaces for the recursive data, add a Replaces[T] annotation, and assign to a lazy val or def, e.g.
   *
   * case class Nested(i: Int, n: Option[Nested])
   * implicit def nestedReplaces: Replaces[Nested] = lazyReplaces(productReplaces2(Nested.apply)("i", "n"))
   *
   * This breaks the cycle that would otherwise result in a compile time error or runtime stack overflow.
   *
   * @param replaces A non-lazy Replaces to which to lazily delegate
   */
  def lazyReplaces[T](replaces: => Replaces[T]) = new Replaces[T] {
    lazy val delegate = replaces
    def replace(t: T, boxId: Long) = delegate.replace(t, boxId)
  }  
}

object CollectionReplaces {
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
  
  implicit def replacesList[T](implicit replaces: Replaces[T]) = new Replaces[List[T]] {
    import BoxReaderDeltaF._
    //TODO why do we need this? It should be in BoxTypes
    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    def replace(list: List[T], boxId: Long) = for {
      _ <- list traverseU (replaces.replace(_, boxId))
    } yield ()
  }

  implicit def replacesSet[T](implicit replaces: Replaces[T]) = new Replaces[Set[T]] {
    import BoxReaderDeltaF._
    //TODO why do we need this? It should be in BoxTypes
    implicit val f = BoxReaderDeltaF.boxReaderDeltaFunctor
    def replace(set: Set[T], boxId: Long) = for {
      _ <- set.toList traverseU (replaces.replace(_, boxId))
    } yield ()
  }

}

object PrimReplaces {
  import BoxReaderDeltaF._
  implicit val booleanReplaces = new Replaces[Boolean] {
    def replace(s: Boolean, boxId: Long) = nothing
  }
  implicit val intReplaces = new Replaces[Int] {
    def replace(s: Int, boxId: Long) = nothing
  }
  implicit val longReplaces = new Replaces[Long] {
    def replace(s: Long, boxId: Long) = nothing
  }
  implicit val floatReplaces = new Replaces[Float] {
    def replace(s: Float, boxId: Long) = nothing
  }
  implicit val doubleReplaces = new Replaces[Double] {
    def replace(s: Double, boxId: Long) = nothing
  }
  implicit val bigIntReplaces = new Replaces[BigInt] {
    def replace(s: BigInt, boxId: Long) = nothing
  }
  implicit val bigDecimalReplaces = new Replaces[BigDecimal] {
    def replace(s: BigDecimal, boxId: Long) = nothing
  }
  implicit val stringReplaces = new Replaces[String] {
    def replace(s: String, boxId: Long) = nothing
  }
}

object NodeReplaces {
  import BoxReaderDeltaF._
  
  private def replaceField[T](n: Product, index: Int, boxId: Long)(implicit f: FormatAndReplaces[T]) = {
    val box = n.productElement(index).asInstanceOf[Box[T]]
  
    //If this is our box, read a new value for it from tokens, set that new 
    //value and we are done
    if (box.id == boxId) {
      for {
        t <- peek
        //If we have some data to read, read it and use values
        _ <- if (t != EndToken) {
          for {
            newT <- f.read
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
        _ <- f.replace(t, boxId)
      } yield ()
    }
  }
  
  def nodeReplaces2[P1: FormatAndReplaces, P2: FormatAndReplaces, N <: Product](construct: (Box[P1], Box[P2]) => N, default: BoxScript[N])
      (name1: String, name2: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Replaces[N] = new Replaces[N] {

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
    } yield ()

  }

}

