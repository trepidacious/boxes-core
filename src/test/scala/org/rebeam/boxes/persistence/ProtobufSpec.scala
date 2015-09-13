package org.rebeam.boxes.persistence

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.protobuf._
import org.rebeam.boxes.persistence.formats._
import PrimFormats._
import ProductFormats._
import CollectionFormats._
import NodeFormats._
import BasicFormats._
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import BoxTypes._
import BoxUtils._

import scalaz._
import Scalaz._

import PersistenceSpecUtils._

class ProtobufSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  def makePerson(name: String, age: Int) = Person.default(name, age)

  def duplicateList[T](list: List[T])(implicit format: Format[List[T]]) = {
    val os = new ByteArrayOutputStream()
    ProtobufIO.write(list, os)
    val list2 = ProtobufIO.read[List[T]](new ByteArrayInputStream(os.toByteArray))
    list shouldBe list2
  }

  def duplicateCaseClass(c: CaseClass) = {
    implicit val caseClassFormat = productFormat2(CaseClass.apply)("s", "i")

    val os = new ByteArrayOutputStream()
    ProtobufIO.write(c, os)
    val c2 = ProtobufIO.read[CaseClass](new ByteArrayInputStream(os.toByteArray))
    c shouldBe c2
  }

  def duplicatePerson(name: String, age: Int) = {
    implicit val personFormat = nodeFormat2(Person.apply, Person.default)("name", "age", nodeName = PresentationName("Person"), boxLinkStrategy = EmptyLinks, nodeLinkStrategy = AllLinks)

    val bob = atomic{makePerson(name, age)}

    val os = new ByteArrayOutputStream()
    ProtobufIO.write(bob, os)

    val bob2 = ProtobufIO.read[Person](new ByteArrayInputStream(os.toByteArray))

    val os2 = new ByteArrayOutputStream()
    ProtobufIO.write(bob2, os2)

    os.toByteArray shouldBe os2.toByteArray

    atomic {for {
        n1 <- bob.name()
        n2 <- bob2.name()
        a1 <- bob.age()
        a2 <- bob2.age()
      } yield {
        n1 shouldBe n2
        a1 shouldBe a2
      }
    }
  }

  def duplicateIdenticalPersonList(boxLinkStrategy: NoDuplicatesLinkStrategy, nodeLinkStrategy: LinkStrategy) = {
    implicit val personFormat = nodeFormat2(Person.apply, Person.default)("name", "age", nodeName = PresentationName("Person"), boxLinkStrategy, nodeLinkStrategy)

    //Create two persons with equal contents, but not the same person (not identical),
    //and a list that contains each one twice
    val list = atomic { 
      for {
        a <- makePerson("a", 1)
        b <- makePerson("a", 1)
      } yield List(a, a, b, b) 
    }

    //Check that each a is equal to the other a, and that a is not equal to b
    list.head should be (list(1))
    list(2) should be (list(3))
    list.head should not be list(2)
    list(1) should not be list(3)

    val os = new ByteArrayOutputStream()
    ProtobufIO.write(list, os)

    //Check that after writing and reading, we again have identical (equal) Persons in the first two
    //positions and last two positions, but they are not equal to each other, or the corresponding
    //Persons in the original array
    val list2 = ProtobufIO.read[List[Person]](new ByteArrayInputStream(os.toByteArray))
    list2.head should be (list2(1))
    list2(2) should be (list2(3))
    list2.head should not be list2(2)
    list2(1) should not be list2(3)
    list.head should not be list2.head
    list(1) should not be list2(1)
    list(2) should not be list2(2)
    list(3) should not be list2(3)
  }

  "ProtobufIO" should {

    "duplicate Person" in duplicatePerson("bob", 34)

    "duplicate List[Person]" in {
      implicit val personFormat = nodeFormat2(Person.apply, Person.default)("name", "age", nodeName = PresentationName("Person"), boxLinkStrategy = EmptyLinks, nodeLinkStrategy = AllLinks)

      val list = atomic {
        for {
          a <- makePerson("a", 1)
          b <- makePerson("b", 2)
          c <- makePerson("c", 3)
          d <- makePerson("d", 4)
          e <- makePerson("e", 5)
        } yield List(a, b, c, d, e)
      }

      val os = new ByteArrayOutputStream()
      ProtobufIO.write(list, os)
      val list2 = ProtobufIO.read[List[Person]](new ByteArrayInputStream(os.toByteArray))

      list.size shouldBe list2.size
      val lists = list.zip(list2)

      val values = atomic {
        lists traverseU {pair => 
          for {
            n1 <- pair._1.name()
            n2 <- pair._2.name()
            a1 <- pair._1.age()
            a2 <- pair._2.age()
          } yield {
            n1 shouldBe n2
            a1 shouldBe a2
          }
        }
      }

    }

    "duplicate arbitrary lists " in {
      forAll{ (list: List[Int]) => duplicateList(list)};          info("of Int")
      forAll{ (list: List[Double]) => duplicateList(list)};       info("of Double")
      forAll{ (list: List[Long]) => duplicateList(list)};         info("of Long")
      forAll{ (list: List[Float]) => duplicateList(list)};        info("of Float")
      forAll{ (list: List[Boolean]) => duplicateList(list)};      info("of Boolean")
      forAll{ (list: List[String]) => duplicateList(list)};       info("of String")
      forAll{ (list: List[BigInt]) => duplicateList(list)};       info("of BigInt")
      forAll{ (list: List[BigDecimal]) => duplicateList(list)};   info("of BigDecimal")
    }

    "duplicate arbitrary Person" in forAll{ (name: String, age: Int) => duplicatePerson(name, age)}

    "preserve identicality of Persons with AllLinks for nodes" in {
      duplicateIdenticalPersonList(EmptyLinks, AllLinks)
      info ("Boxes with empty links")

      duplicateIdenticalPersonList(IdLinks, AllLinks)
      info ("Boxes with id links")
    }

    "fail with NodeCacheException when writing duplicate persons without all links" in {

      intercept[NodeCacheException] {
        duplicateIdenticalPersonList(boxLinkStrategy = EmptyLinks, nodeLinkStrategy = EmptyLinks)
      }
      info ("Boxes and nodes with empty links")

      intercept[NodeCacheException] {
        duplicateIdenticalPersonList(boxLinkStrategy = IdLinks, nodeLinkStrategy = EmptyLinks)
      }
      info ("Boxes with id links, nodes with empty links")

      intercept[NodeCacheException] {
        duplicateIdenticalPersonList(boxLinkStrategy = EmptyLinks, nodeLinkStrategy = IdLinks)
      }
      info ("Boxes with empty links, nodes with id links")

      intercept[NodeCacheException] {
        duplicateIdenticalPersonList(boxLinkStrategy = IdLinks, nodeLinkStrategy = IdLinks)
      }
      info ("Boxes and nodes with id links")
    }

    "duplicate arbitrary CaseClass" in  {
      forAll{(s: String, i: Int) => duplicateCaseClass(CaseClass(s, i))}
    }

  }
}
