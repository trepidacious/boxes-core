package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.json.{JsonPrettyIO, JsonTokenReader}
import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import java.io.StringReader

import scala.util.Try

class JsonSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  //Like JsonPrettyIO.fromJsonString but checking that we
  //have reached the end of the tokens after reading
  def fromJsonString[T: Reads](s: String): T = {
    val sr = new StringReader(s)
    val r = new JsonTokenReader(sr)
    val t = Shelf.runReaderOrException(Reading.read[T], r)
    //Check that JsonTokenReader returns EndToken indefinitely as expected
    for (i <- 1 to 10) {
      r.peek shouldBe EndToken
      r.peek shouldBe EndToken
      r.pull shouldBe EndToken
      r.pull shouldBe EndToken
    }
    t
  }

  def duplicate[T: Format](t: T): Unit = {
    val s = JsonPrettyIO.toJsonString(t:T)
    val d = fromJsonString[T](s)
    t shouldBe d
    //And test JsonPrettyIO as well
    val d2 = JsonPrettyIO.fromJsonString[T](s)
    t shouldBe d2
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
