package org.rebeam.boxes.core.monad

import ShelfActions._

object MonadProtoApp extends App {

  def createName(s: String) = for {
    name <- create(s)
  } yield name

  val name: Box[String] = atomic{createName("bob")}

  val getName = for {
    bobName <- name()
  } yield bobName

  println(atomic{getName})

  val o = new Observer {
    //We have a revision, so we can just use this to retrieve data via box.apply(revision)
//    override def observe(r: Revision): Unit = println("Observed name as " + name(r) + " in " + r)

    //Observer could also execute a new atomic - be careful to avoid loops, atomics must eventually stop
    //being called in response to changes. Ideally an atomic like this should only make changes once, and then
    //when run again should detect that boxes are already in desired state. Using an atomic that only reads state
    //is always acceptable.
    //Note that in this case we are running the atomic on the CURRENT revision at the time, not necessarily
    //the same revision observe is called with
    override def observe(r: Revision): Unit = println("Observed name as " + atomic{getName})
  }

  //Register the observer
  atomic{
    for {
      _ <- observe(o)
    } yield ()
  }

  //Make a change so we can observe it
  atomic{
    for {
      _ <- name() = "bobobobob"
    } yield ()
  }

}
