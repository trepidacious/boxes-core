package org.rebeam.boxes.core.free

import scalaz.{\/-, -\/}

object RevisionApp extends App {

  import BoxDeltaF._

  def makeAValue[T](): T = null.asInstanceOf[T]

  def printScript[T](script: BoxScript[T], output: String) = printScriptLoop[T](script, output)

  def printScriptLoop[T](script: BoxScript[_], output: String): (String, T) = script.resume match {

    case -\/(CreateBoxDeltaF(t, toNext)) => printScriptLoop(toNext(null), output + " " + "CreateBox(" + t + ")")
    case -\/(ReadBoxDeltaF(b, toNext)) => printScriptLoop(toNext(null), output + " " + "ReadBox(" + b + ")")

    case -\/(WriteBoxDeltaF(b, t, next)) => printScriptLoop(next, output + " " + "WriteBox(" + b + ", " + t + ")")
    case -\/(ObserveDeltaF(o, next)) => printScriptLoop(next, output + " " + "Observe(" + o + ")")
    case -\/(UnobserveDeltaF(o, next)) => printScriptLoop(next, output + " " + "Unobserve(" + o + ")")

    case \/-(x) => (output + " Return(" + x + ")", x.asInstanceOf[T])

    case _ => throw new RuntimeException("Invalid!")
  }

//  case class CreateBoxDeltaF[Next, T](t: T, toNext: Box[T] => Next) extends BoxDeltaF[Next]
//  case class ReadBoxDeltaF[Next, T](b: Box[T], toNext: T => Next) extends BoxDeltaF[Next]
//
//  case class WriteBoxDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next]
//  //case class CreateReactionDeltaF[Next, T](b: Box[T], t: T, next: Next) extends BoxDeltaF[Next]
//  case class ObserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]
//  case class UnobserveDeltaF[Next, T](observer: Observer, next: Next) extends BoxDeltaF[Next]


  val script: BoxScript[Box[String]] = //Free[BoxDeltaF, Box[String]] =
    for {
      b <- create("bob")
      c <- create("cate")
      x <- get(b)
      _ <- set(c, x)
    } yield b

  println(printScript(script, ""))
//  println(script)

}
