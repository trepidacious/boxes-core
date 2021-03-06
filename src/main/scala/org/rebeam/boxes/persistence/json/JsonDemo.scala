package org.rebeam.boxes.persistence.json

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence._
import org.rebeam.boxes.persistence.formats._

object JsonDemo extends App {

  import PrimFormats._
  import ProductFormats._
  import BasicFormats._
  import CollectionFormats._

  case class Person(firstName: String, lastName: String, age: Int, friend: Option[Person], favouriteColors: List[String])
  implicit lazy val PersonFormat: Format[Person] = lazyFormat(productFormat5(Person)("firstName", "lastName", "age", "friend", "favouriteColors"))

  val alicia = Person("Alicia", "A", 42, None, List("red", "blue"))
  val bob = Person("Bob", "B", 43, Some(alicia), List("grey"))

  val bobString = JsonPrettyIO.toJsonString(bob)
  println(bobString)

  val listString = JsonPrettyIO.toJsonString(List(1, 2, 3, 4))
  println(listString)

  case object Thing

  implicit lazy val ThingFormat = productFormat0(Thing)()

  println(JsonPrettyIO.toJsonString(Thing))

  val bob2 = JsonPrettyIO.fromJsonString[Person](bobString)

  println(bob2)

  val list2 = JsonPrettyIO.fromJsonString[List[Int]](listString)

  println(list2)

}
