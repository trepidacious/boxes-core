package org.rebeam.boxes.persistence

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.buffers._
import org.rebeam.boxes.persistence.formats._
import PrimFormats._
import ProductFormats._
import CollectionFormats._
import NodeFormats._
import BasicFormats._
import BoxFormatsIdLinks._

import org.scalatest._
import org.scalatest.prop.PropertyChecks
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

import scalaz._
import Scalaz._

import PersistenceSpecUtils._

class ReplacesSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  def replaceAndTest[T, M](model: M, newValue: T, box: Box[T])(implicit formatT: Format[T], formatM: Format[M]): Unit = {
    //Check that box does not already have new value
    atomic { box() } should not be newValue
    val readerOfNewValue = BufferIO.toReader(newValue)
    val replaceScript = formatM.replace(model, box.id)
    Shelf.runReader(replaceScript, readerOfNewValue)
    atomic { box() } shouldBe newValue
  }

  "Replaces" should {
    
    "set plain string box contents" in {
      val boxA = atomic { create("a") }
      replaceAndTest(boxA, "b", boxA)
    }

    "set nested string box contents" in {
      val (boxA, boxOfBoxA) = atomic {
        for {
          boxA <- create("a")
          boxOfBoxA <- create(boxA)
        } yield (boxA, boxOfBoxA)
      }
      replaceAndTest(boxOfBoxA, "b", boxA)
    }
    
    "set string box in a list" in {
      val boxes = atomic { List("1", "2", "3", "4") traverseU (create(_)) }
      replaceAndTest(boxes, "b", boxes(0))
      replaceAndTest(boxes, "b", boxes(1))
    }

    "set string box in a list with duplicate boxes" in {
      val boxA = atomic { create("a") }
      val model: List[Box[String]] = List(boxA, boxA)
      replaceAndTest(model, "b", boxA)
    }

    "set string box in a set" in {
      val boxes = atomic { List("1", "2", "3", "4") traverseU (create(_)) }.toSet
      replaceAndTest(boxes, "b", boxes.head)
    }

    "set string box in a map" in {
      val boxes = atomic { List("1", "2", "3", "4") traverseU (create(_)) }
      val map = Map(0 -> boxes(0), 1 -> boxes(1), 2 -> boxes(2), 3 -> boxes(3))
      replaceAndTest(boxes, "b", boxes(0))
    }

    "set string box in a map with duplicate entries" in {
      val boxes = atomic { List("1", "2", "3", "4") traverseU (create(_)) }
      val map = Map(0 -> boxes(0), 1 -> boxes(0), 2 -> boxes(0), 3 -> boxes(0))
      replaceAndTest(map, "b", boxes(0))
      atomic { boxes(1) } shouldBe "2"
      atomic { boxes(2) } shouldBe "3"
      atomic { boxes(3) } shouldBe "4"
    }

    "set string box in an option" in {
      val boxA = atomic { create("a") }
      val model: List[Option[Box[String]]] = List(None, Some(boxA), Some(boxA))
      replaceAndTest(model, "b", boxA)
    }

    "set string box in a Person (Node2)" in {
      implicit val personFormat = nodeFormat2(Person.apply, Person.default)("name", "age")      
      val p = atomic { Person.default("a", 40) }
      replaceAndTest(p, "b", p.name)
    }

    "set string box in a case class containing Person" in {
      implicit val personFormat = nodeFormat2(Person.apply, Person.default)("name", "age")      
      
      case class PersonAndString(p: Person, s: String)
      
      implicit val personAndStringFormat = productFormat2(PersonAndString.apply)("p", "s")
      
      val p = atomic { Person.default("a", 40) }
      
      val pas = PersonAndString(p, "s")
      
      replaceAndTest(pas, "b", p.name)
    }
    
  }
  
}
