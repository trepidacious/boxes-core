package org.rebeam.boxes.persistence

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.json.JsonIO
import org.rebeam.boxes.persistence.buffers._
import org.rebeam.boxes.persistence.formats._
import PrimFormats._
import ProductFormats._
import CollectionFormats._
import NodeFormats._
import BasicFormats._
import TaggedUnionFormats._
import BoxFormatsIdLinks._

import org.scalatest._
import org.scalatest.prop.PropertyChecks
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

import scalaz._
import Scalaz._

import PersistenceSpecUtils._

class TaggedUnionFormatsSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  sealed trait OneOrTwo
  case class One(first: Box[String]) extends OneOrTwo
  case class Two(first: Box[String], second: Box[Int]) extends OneOrTwo
  
  object One {
    def default: BoxScript[One] = default("")
    def default(first: String): BoxScript[One] = create(first).map{One(_)}
  }

  object Two {
    def default: BoxScript[Two] = default("", 0)
    def default(first: String, second: Int): BoxScript[Two] = (create(first) |@| create(second)){Two(_, _)}
  }


  implicit val oneOrTwoFormat = {
    implicit val oneFormat = nodeFormat1(One.apply, One.default)("first", boxLinkStrategy = EmptyLinks, nodeLinkStrategy = EmptyLinks)
    implicit val twoFormat = nodeFormat2(Two.apply, Two.default)("first", "second", boxLinkStrategy = EmptyLinks, nodeLinkStrategy = EmptyLinks)

    //Note we have to specify the union type OneOrTwo
    taggedUnionFormat[OneOrTwo](
      {
        //Provide a format for each tag
        case "one" => oneFormat
        case "two" => twoFormat
      },
      {
        //Provide a tag (and implicit format) for each type
        //We must also provide the value, since taggedUnionFormat needs a
        //thing that is covariant in the ADT type, so we can use it with
        //a OneOrTwo rather than a One or a Two.
        case o: One => Tagged("one", o)
        
        //Can also provide the format explicitly
        case t: Two => Tagged("two", t)(twoFormat)
      }
    )
  }
  
  "TaggedUnionFormats" should {
    
    "encode some values" in {
      val one = atomic { One.default("one") }
      JsonIO.toJsonString(one) shouldBe """{"one":{"first":"one"}}"""

      val two = atomic { Two.default("two", 2) }
      JsonIO.toJsonString(two) shouldBe """{"two":{"first":"two","second":2}}"""
    }

    "duplicate some values" in {
      val one = atomic { One.default("one") }
      //Note this makes the One into a OneOrTwo
      val oneDup = JsonIO.fromJsonString(JsonIO.toJsonString(one))
      assert ( 
        oneDup match {
          case od: One =>
            atomic {
              (one.first() |@| od.first()) {(a, b) => a == b}
            }
          case _ => false
        }
      )

      val two = atomic { Two.default("two", 2) }
      //Note this makes the Two into a OneOrTwo
      val twoDup = JsonIO.fromJsonString(JsonIO.toJsonString(two))
      assert ( 
        twoDup match {
          case td: Two =>
            atomic { 
              (two.first() |@| td.first() |@| two.second() |@| td.second()) {(a, b, c, d) => (a == b) && (c == d)} 
            }
          case _ => false
        }
      )      
    }
    
    "encode a list of values" in {
      val one = atomic { One.default("one") }
      val two = atomic { Two.default("two", 2) }
      
      val list = List[OneOrTwo](one, two)
      JsonIO.toJsonString(list) shouldBe """[{"one":{"first":"one"}},{"two":{"first":"two","second":2}}]"""
    }

  }
}
