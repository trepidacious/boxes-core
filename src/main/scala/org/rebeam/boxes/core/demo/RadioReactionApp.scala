package org.rebeam.boxes.core.demo

import org.rebeam.boxes.core._
import BoxUtils._
import BoxTypes._
import BoxScript._

import org.rebeam.boxes.core.reaction.RadioReaction

object RadioReactionApp extends App {

  val options = atomic{
    for {
      a <- create(false)
      b <- create(false)
      c <- create(false)
      d <- create(false)
      _ <- RadioReaction(List(a, b, c, d))
    } yield List(a, b, c, d)
  }

//  atomic{RadioReaction(options)}

  val o = new Observer {
    override def observe(r: Revision): Unit = println("Observed options as " + options(0)(r) + ", " + options(1)(r) + ", " + options(2)(r) + ", " + options(3)(r) + " in " + r)
  }
  //Register the observer
  atomic{observe(o)}

  println("Setting 0 to true")
  atomic{options(0)() = true}

  println("Setting 1 to true")
  atomic{options(1)() = true}

  println("Setting 2 to true")
  atomic{options(2)() = true}

  println("Setting 3 to true")
  atomic{options(3)() = true}

  println("Setting 1 and 2 to true")
  atomic{for {
    _ <- options(1)() = true
    _ <- options(2)() = true
  } yield ()}

}
