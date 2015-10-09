package org.rebeam.boxes.core

import BoxTypes._
import BoxUtils._
import BoxScriptImports._

import org.scalatest.WordSpec
import org.scalatest.Matchers._

class PathSpec extends WordSpec {

  case class End(name: Box[String]) {
    def asString: BoxScript[String] = name().map("End(" + _ + ")")
  }

  object End {
    def default(name: String): BoxScript[End] = for {
      n <- create(name)
    } yield End(n)
  }

  case class Link(name: Box[String], end: Box[End]) {
    def asString: BoxScript[String] = for {
      n <- name()
      e <- end()
    } yield "Link(" + n + ", " + e + ")"
  }

  object Link {
    def default(name: String, end: End): BoxScript[Link] = for {
      n <- create(name)      
      e <- create(end)
    } yield Link(n, e)
  }

  "path" should {
    "read and write non-optional values via non-optional path" in {
      val end = atomic{End.default("end")}
      val link = atomic{Link.default("link", end)}

      val endNameViaLink = pathB(link.end().map(_.name))
      
      //Check initial state of path
      atomic{endNameViaLink()} shouldBe "end"

      //Use path to modify the value, and check we see this both in the path, and in the end
      atomic(endNameViaLink() = "modifiedViaPath")
      atomic{endNameViaLink()} shouldBe "modifiedViaPath"
      atomic{end.name()} shouldBe "modifiedViaPath"

      //Now try modifying the end, and check the path updates
      atomic{end.name() = "modifiedDirectly"}
      atomic{endNameViaLink()} shouldBe "modifiedDirectly"

      //Make a new end to access using the path
      val end2 = atomic{End.default("end2")}

      //Since end2 is not pointed to by link, changes to it do not matter
      atomic{end2.name() = "immaterial"}
      atomic{endNameViaLink()} shouldBe "modifiedDirectly"

      //Now make the link point to end2, and the path will change value
      atomic{link.end() = end2}
      atomic{endNameViaLink()} shouldBe "immaterial"

      //We can now change the original end without changing the path
      atomic{end.name() = "modifiedWithNoResultingPathChange"}
      atomic{endNameViaLink()} shouldBe "immaterial"

      //If we change end2 directly now, we will see it in the path
      atomic{end2.name() = "material"}
      atomic{endNameViaLink()} shouldBe "material"      

      //And if we change the path, we will see this in end2, but NOT in end
      atomic(endNameViaLink() = "modifiedViaPath2")
      atomic{endNameViaLink()} shouldBe "modifiedViaPath2"
      atomic{end.name()} shouldBe "modifiedWithNoResultingPathChange"
      atomic{end2.name()} shouldBe "modifiedViaPath2"

    }
  }


}