package org.rebeam.boxes.core.free

import scalaz.{\/-, -\/}

object RevisionApp extends App {

  import BoxDeltaF._

  def makeAValue[T](): T = null.asInstanceOf[T]

  def printScript(script: BoxScript[_], output: String): (String, Any) = script.resume match {
    case -\/(CreateBoxDeltaF(t, toNext)) => printScript(toNext(null).asInstanceOf[BoxScript[Unit]], output + " " + "CreateBox(" + t + ")")
    case \/-(x) => (output + " Return(" + x + ")", x)
    case _ => throw new RuntimeException("Invalid!")
  }

//  case class CreateBoxDeltaF[Next, T](t: T, toNext: Box[T] => Next) extends BoxDeltaF[Next]
//  case class ReadBoxDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxDeltaF[Next]
//
//  case class WriteBoxDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next]
//  //case class CreateReactionDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next]
//  case class ObserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]
//  case class UnobserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]

  val createBob = for {
    b <- create("bob")
  } yield b

  println(printScript(createBob, ""))

}
