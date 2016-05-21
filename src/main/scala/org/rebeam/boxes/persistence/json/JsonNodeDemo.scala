package org.rebeam.boxes.persistence.json

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.formats._

import BoxTypes._
import BoxUtils._
import BoxScriptImports._

import scalaz._
import Scalaz._

object JsonNodeDemo extends App {

  import PrimFormats._
  import ProductFormats._
  import NodeFormats._
  import BasicFormats._
  import CollectionFormats._

  case class Person(val name: Box[String], val friend: Box[Option[Person]]) {
    def asString: BoxScript[String] = for {
      n <- name()
      f <- friend()
      fs <- f traverseU (_.asString)
    } yield s"Person($n, $fs)" 
  }

  object Person {
    def default: BoxScript[Person] = default("", None)
    def default(name: String, friend: Option[Person]): BoxScript[Person] = (create(name) |@| create(friend)){Person(_, _)}
  }

  implicit lazy val PersonFormat: Format[Person] = lazyFormat(nodeFormat2(Person.apply, Person.default)("name", "friend"))

  val alicia = atomic(Person.default("Alicia", None))
  val bob = atomic(Person.default("Bob", Some(alicia)))

  val bobString = JsonPrettyIO.toJsonString(bob)
  println(bobString)
  
  val bobCopy = JsonPrettyIO.fromJsonString[Person](bobString)
  println(atomic(bobCopy.asString))

}
