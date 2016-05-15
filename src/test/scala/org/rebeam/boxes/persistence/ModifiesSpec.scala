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

class ModifiesSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  case class NameAndList(name: Box[String], list: Box[List[String]]) {
    def asString: BoxScript[String] = (name() |@| list()){"NameAndList(" + _ + ", " + _ + ")"}
  }
  
  object NameAndList {
    def default: BoxScript[NameAndList] = default("", Nil)
    def default(name: String, list: List[String]): BoxScript[NameAndList] = (create(name) |@| create(list)){NameAndList(_, _)}
  }
  
  case class NameAndListAction(name: Option[String], addToList: List[String]) extends Action[NameAndList] {
    def act(nal: NameAndList) = for {
      l <- nal.list()
      _ <- nal.list() = l ++ addToList
    } yield ()
  }

  def modify[M, A](model: M, action: A, boxId: Long)(implicit formatA: Format[A], formatM: Format[M]): Unit = {
    val readerOfAction = BufferIO.toReader(action)
    val modifyScript = formatM.modify(model, boxId)
    Shelf.runReader(modifyScript, readerOfAction)
  }

  //Make a format for our action
  implicit val nameAndListActionFormat = productFormat2(NameAndListAction.apply)("name", "addToList")

  //Now a format for name and list, with a format to read the action we will apply
  implicit val nameAndListFormat = nodeFormat2(NameAndList.apply, NameAndList.default)("name", "list", Some(nameAndListActionFormat))

  "Modifies" should {
    
    "modify a NameAndList" in {
      
      //Make a name and list
      val nal = atomic { NameAndList.default("name", List("i")) }
      
      //Make a specific action to apply - will add a, b to list
      val action = NameAndListAction(None, List("a", "b"))

      //Modify and check results.
      //Note we only need to pass the id of some box in nal to modify nal.
      modify(nal, action, nal.list.id)
      atomic { nal.list() } shouldBe List("i", "a", "b")

      //And again
      modify(nal, action, nal.list.id)
      atomic { nal.list() } shouldBe List("i", "a", "b", "a", "b")      
    }

    "modify a NameAndList in a List" in {
      
      //Make a name and list
      val nal = atomic { NameAndList.default("name", List("i")) }
      
      //Make a specific action to apply - will add a, b to list
      val action = NameAndListAction(None, List("a", "b"))

      //Make some more nals and a list of them
      val nal2 = atomic { NameAndList.default("name2", List("j")) }
      val nal3 = atomic { NameAndList.default("name3", List("k")) }
      val nal4 = atomic { NameAndList.default("name4", List("l")) }

      val listOfNal = List(nal, nal2, nal3, nal4)

      //Modify and check results.
      //Note we only need to pass the id of some box in nal to modify nal.
      modify(listOfNal, action, nal.list.id)
      atomic { nal.list() } shouldBe List("i", "a", "b")
      atomic { nal2.list() } shouldBe List("j")
      atomic { nal3.list() } shouldBe List("k")
      atomic { nal4.list() } shouldBe List("l")

      //And again
      modify(listOfNal, action, nal.list.id)
      atomic { nal.list() } shouldBe List("i", "a", "b", "a", "b")      
      atomic { nal2.list() } shouldBe List("j")
      atomic { nal3.list() } shouldBe List("k")
      atomic { nal4.list() } shouldBe List("l")
    }

    "modify a NameAndList in a List with duplicates" in {
      
      //Make a name and list
      val nal = atomic { NameAndList.default("name", List("i")) }
      
      //Make a specific action to apply - will add a, b to list
      val action = NameAndListAction(None, List("a", "b"))

      //Make some more nals and a list of them
      val nal2 = atomic { NameAndList.default("name2", List("j")) }
      val nal3 = atomic { NameAndList.default("name3", List("k")) }
      val nal4 = atomic { NameAndList.default("name4", List("l")) }

      //This time we have nal multiple times - the action should only
      //apply once
      val listOfNal = List(nal, nal, nal, nal2, nal3, nal4)

      //Modify and check results.
      //Note we only need to pass the id of some box in nal to modify nal.
      modify(listOfNal, action, nal.list.id)
      atomic { nal.list() } shouldBe List("i", "a", "b")
      atomic { nal2.list() } shouldBe List("j")
      atomic { nal3.list() } shouldBe List("k")
      atomic { nal4.list() } shouldBe List("l")

      //And again
      modify(listOfNal, action, nal.list.id)
      atomic { nal.list() } shouldBe List("i", "a", "b", "a", "b")      
      atomic { nal2.list() } shouldBe List("j")
      atomic { nal3.list() } shouldBe List("k")
      atomic { nal4.list() } shouldBe List("l")
    }
    
  }
  
}
