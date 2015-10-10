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

  case class LinkO(name: Box[String], end: Box[Option[End]]) {
    def asString: BoxScript[String] = for {
      n <- name()
      e <- end()
    } yield "Link(" + n + ", " + e + ")"
  }

  object LinkO {
    def default(name: String, end: Option[End]): BoxScript[LinkO] = for {
      n <- create(name)      
      e <- create(end)
    } yield LinkO(n, e)
  }

  case class EndO(name: Box[Option[String]]) {
    def asString: BoxScript[String] = name().map("End(" + _ + ")")
  }

  object EndO {
    def default(name: Option[String]): BoxScript[EndO] = for {
      n <- create(name)
    } yield EndO(n)
  }

  case class LinkOO(name: Box[String], end: Box[Option[EndO]]) {
    def asString: BoxScript[String] = for {
      n <- name()
      e <- end()
    } yield "Link(" + n + ", " + e + ")"
  }

  object LinkOO {
    def default(name: String, end: Option[EndO]): BoxScript[LinkOO] = for {
      n <- create(name)      
      e <- create(end)
    } yield LinkOO(n, e)
  }



  "pathB" should {
    "read and write non-optional values via non-optional path" in {
      val end = atomic{End.default("end")}
      val link = atomic{Link.default("link", end)}

      val endNameViaLink = pathB(link.end().map(_.name))
      
      //Check initial state of path
      atomic { endNameViaLink() } shouldBe "end"

      //Use path to modify the value, and check we see this both in the path, and in the end
      atomic { endNameViaLink() = "modifiedViaPath" }
      atomic { endNameViaLink() } shouldBe "modifiedViaPath"
      atomic { end.name() } shouldBe "modifiedViaPath"

      //Now try modifying the end, and check the path updates
      atomic { end.name() = "modifiedDirectly" }
      atomic { endNameViaLink() } shouldBe "modifiedDirectly"

      //Make a new end to access using the path
      val end2 = atomic { End.default("end2") }

      //Since end2 is not pointed to by link, changes to it do not matter
      atomic { end2.name() = "immaterial" }
      atomic { endNameViaLink() } shouldBe "modifiedDirectly"

      //Now make the link point to end2, and the path will change value
      atomic { link.end() = end2 }
      atomic { endNameViaLink() } shouldBe "immaterial"

      //We can now change the original end without changing the path
      atomic { end.name() = "modifiedWithNoResultingPathChange" }
      atomic { endNameViaLink() } shouldBe "immaterial"

      //If we change end2 directly now, we will see it in the path
      atomic { end2.name() = "material" }
      atomic { endNameViaLink() } shouldBe "material"      

      //And if we change the path, we will see this in end2, but NOT in end
      atomic { endNameViaLink() = "modifiedViaPath2" }
      atomic { endNameViaLink() } shouldBe "modifiedViaPath2"
      atomic { end.name() } shouldBe "modifiedWithNoResultingPathChange"
      atomic { end2.name() } shouldBe "modifiedViaPath2"
    }
  }

  "pathViaOptionB" should {
    "read and write values via optional path" in {
      val end = atomic{End.default("end")}
      val link = atomic{LinkO.default("link", None)}

      val script = link.end().map(_.map(_.name))

      val endNameViaLink = pathViaOptionB(script)
      
      //Check we start with None
      atomic { endNameViaLink() } shouldBe None

      //Check that writing has no effect
      atomic { endNameViaLink() = Some("ignored") }
      atomic { endNameViaLink() } shouldBe None
      atomic { end.name() } shouldBe "end"

      //Assign end and check initial state of path
      atomic { link.end() = Some(end) }
      atomic { endNameViaLink() } shouldBe Some("end")

      //Use path to modify the value, and check we see this both in the path, and in the end
      atomic { endNameViaLink() = Some("modifiedViaPath") }
      atomic { endNameViaLink() } shouldBe Some("modifiedViaPath")
      atomic { end.name() } shouldBe "modifiedViaPath"

      //Trying to set the path to None should do nothing
      atomic { endNameViaLink() = None }
      atomic { endNameViaLink() } shouldBe Some("modifiedViaPath")
      atomic { end.name() } shouldBe "modifiedViaPath"

      //Now try modifying the end, and check the path updates
      atomic { end.name() = "modifiedDirectly"}
      atomic { endNameViaLink()} shouldBe Some("modifiedDirectly")

      //Make a new end to access using the path
      val end2 = atomic { End.default("end2") }

      //Since end2 is not pointed to by link, changes to it do not matter
      atomic { end2.name() = "immaterial" }
      atomic { endNameViaLink() } shouldBe Some("modifiedDirectly")

      //Now make the link point to end2, and the path will change value
      atomic { link.end() = Some(end2) }
      atomic { endNameViaLink() } shouldBe Some("immaterial")

      //We can now change the original end without changing the path
      atomic { end.name() = "modifiedWithNoResultingPathChange" }
      atomic { endNameViaLink() } shouldBe Some("immaterial")

      //If we change end2 directly now, we will see it in the path
      atomic { end2.name() = "material" }
      atomic { endNameViaLink()} shouldBe Some("material")

      //And if we change the path, we will see this in end2, but NOT in end
      atomic { endNameViaLink() = Some("modifiedViaPath2") }
      atomic { endNameViaLink() } shouldBe Some("modifiedViaPath2")
      atomic { end.name() } shouldBe "modifiedWithNoResultingPathChange"
      atomic { end2.name() } shouldBe "modifiedViaPath2"

    }
  }

  "pathToOptionB" should {
    "read and write values via optional path to an optional value" in {
      val end = atomic{EndO.default(None)}
      val link = atomic{LinkOO.default("link", None)}

      val script = link.end().map(_.map(_.name))

      val endNameViaLink = pathToOptionB(script)
      
      //Check we start with None
      atomic { endNameViaLink() } shouldBe None

      //Check that writing has no effect
      atomic { endNameViaLink() = Some("ignored") }
      atomic { endNameViaLink() } shouldBe None
      atomic { end.name() } shouldBe None

      //Assign end and check initial state of path
      atomic { link.end() = Some(end) }
      atomic { endNameViaLink() } shouldBe None

      //Now give end a value and check path
      atomic { end.name() = Some("end") }
      atomic { endNameViaLink() } shouldBe Some("end")

      //Use path to modify the value, and check we see this both in the path, and in the end
      atomic { endNameViaLink() = Some("modifiedViaPath") }
      atomic { endNameViaLink() } shouldBe Some("modifiedViaPath")
      atomic { end.name() } shouldBe Some("modifiedViaPath")

      //Trying to set the path to None should set end to None
      atomic { endNameViaLink() = None }
      atomic { endNameViaLink() } shouldBe None
      atomic { end.name() } shouldBe None

      //Now try modifying the end to Some value, and check the path updates
      atomic { end.name() = Some("modifiedDirectly")}
      atomic { endNameViaLink()} shouldBe Some("modifiedDirectly")

      //Set end back to None, and check the path updates
      atomic { end.name() = None}
      atomic { endNameViaLink()} shouldBe None

      //Make a new end to access using the path
      val end2 = atomic { EndO.default(Some("end2")) }

      //Since end2 is not pointed to by link, changes to it do not matter
      atomic { end2.name() = Some("immaterial") }
      atomic { endNameViaLink() } shouldBe None

      //Now make the link point to end2, and the path will change value
      atomic { link.end() = Some(end2) }
      atomic { endNameViaLink() } shouldBe Some("immaterial")

      //We can now change the original end without changing the path
      atomic { end.name() = Some("modifiedWithNoResultingPathChange") }
      atomic { endNameViaLink() } shouldBe Some("immaterial")

      //If we change end2 directly now, we will see it in the path
      atomic { end2.name() = Some("material") }
      atomic { endNameViaLink()} shouldBe Some("material")

      //And if we change the path, we will see this in end2, but NOT in end
      atomic { endNameViaLink() = Some("modifiedViaPath2") }
      atomic { endNameViaLink() } shouldBe Some("modifiedViaPath2")
      atomic { end.name() } shouldBe Some("modifiedWithNoResultingPathChange")
      atomic { end2.name() } shouldBe Some("modifiedViaPath2")

    }
  }


}