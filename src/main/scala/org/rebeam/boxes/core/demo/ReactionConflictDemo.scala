package org.rebeam.boxes.core.demo
//
//import org.rebeam.boxes.core.{Box, ShelfDefault}
//
//object ReactionConflictDemo {
//  def main(args: Array[String]) {
//    implicit val shelf = ShelfDefault()
//    shelf.transact(implicit txn => {
//
//      val a = Box(0)
//      val b = Box(0)
//      val c = Box(0)
//
//      println("a is " + a)
//      println("b is " + b)
//      println("c is " + c)
//
//      println("About to add r1")
//      //These reactions are consistent only when a() == b(). This is initially true, and so
//      //reactions are accepted
//      val r1 = txn.createReaction(implicit txn => {
//        println("Reaction 1: Will set c to a (" + a() + ") + 1")
//        c() = a() + 1
//      })
//
//      println("r1 is " + r1)
//
//
//      println("About to add r2")
//
//      val r2 = txn.createReaction(implicit txn => {
//        println("Reaction 2: Will set c to b (" + b() + ") + 1")
//        c() = b() + 1
//      })
//
//      println("r2 is " + r2)
//
//      println("After setting up reactions c = " + c())
//      //This change will expose the fact that the reactions are inconsistent, by making them
//      //conflict
//        a() = 32
//        println(c())
//    })
//
//  }
//}
