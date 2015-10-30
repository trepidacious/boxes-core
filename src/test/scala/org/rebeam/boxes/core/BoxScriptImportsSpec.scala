package org.rebeam.boxes.core

import BoxTypes._
import BoxUtils._
import BoxScriptImports._

import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import org.scalatest.WordSpec
import org.scalatest.Matchers._

import scala.util.Try

class BoxScriptImportsSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  sealed trait Polymorphic
  case class I(i: Int) extends Polymorphic
  case class S(s: String) extends Polymorphic
  case object O extends Polymorphic

  def s2i(s: String) = Try(s.toInt).toOption

  "partial" should {
    "work with patten matching" in {
      val p: Box[Polymorphic] = atomic { create(I(0): Polymorphic) }

      val oI = p.partial{ case I(i) => I(i) }
      atomic { oI } shouldBe Some(I(0))

      val oS = p.partial{ case S(s) => S(s) }
      atomic { oS } shouldBe None

      val oO = p.partial{ case O => O }
      atomic { oO } shouldBe None

      atomic { p() = I(1) }
      atomic { oI } shouldBe Some(I(1))
      atomic { oS } shouldBe None
      atomic { oO } shouldBe None

      atomic { p() = S("s") }
      atomic { oI } shouldBe None
      atomic { oS } shouldBe Some(S("s"))
      atomic { oO } shouldBe None

      atomic { p() = S("t") }
      atomic { oI } shouldBe None
      atomic { oS } shouldBe Some(S("t"))
      atomic { oO } shouldBe None

      atomic { p() = O }
      atomic { oI } shouldBe None
      atomic { oS } shouldBe None
      atomic { oO } shouldBe Some(O)
    }
  }

  "default" should {
    "provide default value instead of None" in {
      val oS: Box[Option[String]] = atomic { create(Some("s"): Option[String]) }

      val s: BoxR[String] = oS.default("default")

      atomic { s } shouldBe "s"

      atomic { oS() = None }
      atomic { s } shouldBe "default"      

      atomic { oS() = Some("t") }
      atomic { s } shouldBe "t"      
    }
  }

  "optional" should {
    "convert string to int where possible" in {
      val s = atomic{ create("blah") }
      val i = s.optional(s2i)

      atomic { i } shouldBe None

      atomic { s() = "1" }
      atomic { i } shouldBe Some(1)

      atomic { s() = "not a number" }
      atomic { i } shouldBe None

      atomic { s() = "12345" }
      atomic { i } shouldBe Some(12345)
    }
  }

}