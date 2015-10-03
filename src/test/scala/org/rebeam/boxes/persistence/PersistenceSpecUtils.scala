package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import org.scalatest.Matchers
import BoxScript._

object PersistenceSpecUtils extends Matchers {
  def duplicate[T](t: T, format: Format[T]): Unit = {
    implicit val f = format
    val writtenTokens = BufferIO.toTokens(t)
    val read = BufferIO.fromTokens[T](writtenTokens)
    read shouldBe t
  }
}

case class Person(name: Box[String], age: Box[Int]) {
  def asString: BoxScript[String] = for {
    n <- name()
    a <- age()
    } yield "Person(" + n + ", " + a + ")"
}

object Person {
  def default: BoxScript[Person] = default("", 0)

  def default(name: String, age: Int): BoxScript[Person] = for {
    n <- create(name)
    a <- create(age)
  } yield Person(n, a)
}

case class CaseClass(s: String, i: Int)
