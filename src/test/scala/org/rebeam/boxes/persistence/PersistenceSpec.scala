package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.json.JsonPrettyIO
import org.rebeam.boxes.persistence.buffers._
import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import scala.util.Try

class PersistenceSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  def duplicate[T: Format](t: T): Unit = {
    val s = BufferIO.toTokens(t:T)
    val d = BufferIO.fromTokens[T](s)
    t shouldBe d
  }

  "Persistence" should {
    "duplicate list of 100000 ints without stack overflow" in {
      import PrimFormats._
      import CollectionFormats._
      val l = Range(1, 100000).toList
      duplicate(l)
    }
  }
}
