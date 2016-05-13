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
import ActionFormats._
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
    def act(b: Box[NameAndList]) = for {
      nal <- b
      l <- nal.list()
      _ <- nal.list() = l ++ addToList
    } yield ()
  }

  def modify[M, T, A <: Action[T]](model: M, action: A, box: Box[T])(implicit formatA: Format[A], formatM: Format[M]): Unit = {
    val readerOfAction = BufferIO.toReader(action)
    val modifyScript = formatM.modify(model, box.id)
    Shelf.runReader(modifyScript, readerOfAction)
  }

  "Modifies" should {
    
    "modify a NameAndList directly in a Box" in {
      
      //Make a format for our action
      implicit val nameAndListActionFormat = productFormat2(NameAndListAction.apply)("name", "addToList")

      //Now a format for name and list, adding the action we use to modify it
      implicit val nameAndListFormat = nodeFormat2(NameAndList.apply, NameAndList.default)("name", "list")
                                        .withAction(nameAndListActionFormat)
      
      //Make a box of a name and list
      val box = atomic { NameAndList.default("name", List("i")) flatMap create }
      
      //Make a specific action to apply - will add a, b to list
      val action = NameAndListAction(None, List("a", "b"))

      //Modify and check results
      modify(box, action, box)
      atomic { box().flatMap(_.list()) } shouldBe List("i", "a", "b")

      //And again
      modify(box, action, box)
      atomic { box().flatMap(_.list()) } shouldBe List("i", "a", "b", "a", "b")
      
    }
    
  }
  
}
