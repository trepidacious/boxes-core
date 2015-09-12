package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.json.JsonPrettyIO
import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import scala.util.Try

class JsonSpec extends WordSpec with PropertyChecks with ShouldMatchers {


  def duplicate[T: Format](t: T): Unit = {
    val s = JsonPrettyIO.toJsonString(t:T)
    val d = JsonPrettyIO.fromJsonString[T](s)
    t shouldBe d
  }

  def duplicateBigDecimal(v: BigDecimal): Unit = {
    import PrimFormats._
    if (isReasonableBigDecimal(v)) {
      duplicate(v)
    } else {
      intercept[IncorrectTokenException] {
        JsonPrettyIO.toJsonString(v)
      }
    }
  }

  def duplicateArbitrary[T: Format: Arbitrary](): Unit = forAll{ (v: T) => duplicate(v) }

  //Don't try to process big decimals that can't actually be parsed from their toString representation - this seems
  //to be a Java bug, but the values that fail are pretty unreasonable
  def isReasonableBigDecimal(d: BigDecimal) = Try{BigDecimal(d.toString)}.isSuccess

  "Json Tokens" should {
    "duplicate arbitrary primitives" in {
      import PrimFormats._
      duplicateArbitrary[Int]()
      duplicateArbitrary[Double]()
      duplicateArbitrary[Long]()
      duplicateArbitrary[Float]()
      duplicateArbitrary[Boolean]()
      duplicateArbitrary[String]()
      duplicateArbitrary[BigInt]()
      forAll{ (v: BigDecimal) => duplicateBigDecimal(v)}
    }

    "duplicate arbitrary lists of primitives" in {
      import PrimFormats._
      import CollectionFormats._
      duplicateArbitrary[List[Double]]()
      duplicateArbitrary[List[Long]]()
      duplicateArbitrary[List[Float]]()
      duplicateArbitrary[List[Boolean]]()
      duplicateArbitrary[List[String]]()
      duplicateArbitrary[List[BigInt]]()
    }

    "duplicate CaseClass" in  {
      forAll{(s: String, i: Int) => {
        import PrimFormats._
        import ProductFormats._
        implicit val caseClassFormat = productFormat2(CaseClass.apply)("s", "i")

        duplicate(CaseClass(s, i))
      }}
    }

  }
}
