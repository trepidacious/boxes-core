package org.rebeam.boxes.core.monad

import OmitShelf._

object MonadProtoApp extends App {

  implicit val s = new ShelfMonad()

  val r0 = s.currentRevision
  val d0 = s.currentRevisionDelta

  val createBob = for {
    bob <- create("bob")
  } yield bob

  val (d1, bob) = createBob.run(d0)

  s.commit(d1)

  val readBob = for {
    bobName <- get(bob)
  } yield bobName

  val (d2, bobName) = readBob.run(d1)

  println(bobName)

  val readBobTwice = for {
    b1 <- readBob
    b2 <- readBob
  } yield (b1, b2)

  val (d3, bobNameTwice) = readBobTwice.run(d2)

  println(bobNameTwice)

  //This would not work - wrong shelf is used to make the Box for alice - note only the last line fails, where we run
//  val s2 = new ShelfMonad()
//  val wrongShelf = for {
//    alice <- s.create("alice")
//  } yield alice
//  wrongShelf.run(s2.currentRevision)

  //  val d1 = d0.copy(creates=d0.creates + b0).copy(writes=d0.writes + (b0->"bob"))

//  val (d1, b0) = d0.create("bob")
//
//  s.commit(d1)
//
//  val r1 = s.currentRevision
//  println(r0.valueOf(b0))
//  println(r1.valueOf(b0))

}
