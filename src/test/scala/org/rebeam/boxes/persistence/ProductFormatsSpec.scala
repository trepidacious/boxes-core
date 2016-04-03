package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.buffers._
import PrimFormats._
import ProductFormats._
import BasicFormats._
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import PersistenceSpecUtils._

case class CaseClass6(b: Boolean, i: Int, l: Long, f: Float, d: Double, s: String)

case class Nested(i: Int, n: Option[Nested])

class ProductFormatsSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  def duplicateCaseClass6(b: Boolean, i: Int, l: Long, f: Float, d: Double, s: String): Unit = duplicate(
    CaseClass6(b, i, l, f, d, s),
    productFormat6(CaseClass6.apply)("b", "i", "l", "f", "d", "s")
  )

  def duplicateCaseClass(s: String, i: Int): Unit = duplicate(
    CaseClass(s, i),
    productFormat2(CaseClass.apply)("s", "i")
  )

  "ProductFormats" should {

    "duplicate arbitrary case class of arity 6" in forAll{ (b: Boolean, i: Int, l: Long, f: Float, d: Double, s: String) => duplicateCaseClass6(b, i, l, f, d, s)}

    "duplicate arbitrary case class" in forAll { (s: String, i: Int) => duplicateCaseClass(s, i) }

    "duplicate nested case class" in {
      //Note the requirements for using a recursive case class:
      // * lazy val (could also use def)
      // * lazyFormat
      // * Format[Nested] type annotation
      implicit lazy val nestedFormat: Format[Nested] = lazyFormat(productFormat2(Nested.apply)("i", "n"))

      duplicate(
        Nested(0, Some(Nested(1, Some(Nested(2, None))))),
        nestedFormat
      )
    }

    "read out of order fields" in {
      implicit val caseClassFormat = productFormat2(CaseClass.apply)("s", "i")

      val c = CaseClass("p", 42)

      val writtenTokens = BufferIO.toTokens(c)

      val canonicalOrderTokens = List(
        OpenDict(NoName,LinkEmpty),
          DictEntry("s", LinkEmpty), StringToken("p"),
          DictEntry("i", LinkEmpty), IntToken(42),
        CloseDict
      )

      //Check that we write as expected - fields are in order used by CaseClass.apply and returned by c.productElement
      writtenTokens shouldBe canonicalOrderTokens

      //Check we can read the canonical order
      val readOrdered = BufferIO.fromTokens[CaseClass](canonicalOrderTokens)
      readOrdered shouldBe c

      //Now reorder the two fields to test reading out of order (this can be caused for example by a round trip
      //through json tools that don't respect order)
      val outOfOrderTokens = List(
        OpenDict(NoName,LinkEmpty),
        DictEntry("i", LinkEmpty), IntToken(42),
        DictEntry("s", LinkEmpty), StringToken("p"),
        CloseDict
      )

      //Check that we can read out of order
      val readOutOfOrder = BufferIO.fromTokens[CaseClass](outOfOrderTokens)
      readOutOfOrder shouldBe c
    }

    "fail with additional fields" in {
      implicit val caseClassFormat = productFormat2(CaseClass.apply)("s", "i")

      //This contains the correct fields, and also an extra one
      val additionalTokens = List(
        OpenDict(NoName,LinkEmpty),
        DictEntry("i", LinkEmpty), IntToken(42),
        DictEntry("j", LinkEmpty), IntToken(42),
        DictEntry("s", LinkEmpty), StringToken("p"),
        CloseDict
      )

      intercept[IncorrectTokenException] {
        BufferIO.fromTokens[CaseClass](additionalTokens)
      }
    }

    "fail with missing fields" in {
      implicit val caseClassFormat = productFormat2(CaseClass.apply)("s", "i")

      //Here we are missing field i
      val missingFieldI = List(
        OpenDict(NoName,LinkEmpty),
        DictEntry("s", LinkEmpty), StringToken("p"),
        CloseDict
      )
      intercept[IncorrectTokenException] {
        BufferIO.fromTokens[CaseClass](missingFieldI)
      }

      //Here we are missing field s
      val missingFieldS = List(
        OpenDict(NoName,LinkEmpty),
        DictEntry("i", LinkEmpty), IntToken(42),
        CloseDict
      )
      intercept[IncorrectTokenException] {
        BufferIO.fromTokens[CaseClass](missingFieldS)
      }

    }

  }
}
