package org.rebeam.boxes.core.monad

import ShelfActions._

object MonadProtoApp extends App {

  def createName(s: String) = for {
    name <- create(s)
  } yield name

  val bob = atomic{createName("bob")}

  println(atomic{
    for {
      bobName <- get(bob)
    } yield bobName
  })

}
