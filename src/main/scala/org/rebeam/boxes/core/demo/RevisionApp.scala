package org.rebeam.boxes.core.demo

import org.rebeam.boxes.core._
import BoxUtils._

object RevisionApp extends App {

  import BoxDeltaF._

  val name: Box[String] = atomic{create("bob")}

  println(atomic{name()})

  val o = new Observer {
    //We have a revision, so we can just use this to retrieve data via box.apply(revision)
    override def observe(r: Revision): Unit = println("Observed name as " + name(r) + " in " + r)

    //Observer could also execute a new atomic - be careful to avoid loops, atomics must eventually stop
    //being called in response to changes. Ideally an atomic like this should only make changes once, and then
    //when run again should detect that boxes are already in desired state. Using an atomic that only reads state
    //is always acceptable.
    //Note that in this case we are running the atomic on the CURRENT revision at the time, not necessarily
    //the same revision observe is called with
//    override def observe(r: Revision): Unit = println("Observed name as " + atomic{name()})
  }

  //Register the observer
  atomic{observe(o)}

  //Make a change so we can observe it
  atomic{name() = "bobobobob"}

  println("Modified from " + atomic(modify(name, (s: String) => s + "-mod")))  

  println("Changed sources in normal transaction: " + atomic{changedSources()})

  val upperCase = atomic{cal{name().map(_.toUpperCase)}}

  println(atomic(upperCase()))
  atomic(name() = "alice")
  println(atomic(upperCase()))

  val upperCaseReportSources = atomic{create("ucrs")}
  val ucrsReaction = atomic{createReaction{
    for {
      n <- name()
      _ <- upperCaseReportSources() = n.toUpperCase
      cs <- changedSources()
      _ <- {println("Reaction changed sources: " + cs); name()}
    } yield ()
  }}

  atomic(name() = "cate")

}
