package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import org.scalatest.Matchers
import BoxScriptImports._
import org.rebeam.boxes.persistence.buffers._

import scalaz._
import Scalaz._

object PersistenceSpecUtils extends Matchers {
  def duplicate[T](t: T, format: Format[T]): Unit = {
    implicit val f = format
    val writtenTokens = BufferIO.toTokens(t)
    val read = BufferIO.fromTokens[T](writtenTokens)
    read shouldBe t
  }
}

case class Person(name: Box[String], age: Box[Int]) {
  def asString: BoxScript[String] = (name() |@| age()){"Person(" + _ + ", " + _ + ")"}
}

object Person {
  def default: BoxScript[Person] = default("", 0)
  def default(name: String, age: Int): BoxScript[Person] = (create(name) |@| create(age)){Person(_, _)}
}

case class CaseClass(s: String, i: Int)
