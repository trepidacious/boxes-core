package org.rebeam.boxes.core.demo
//
//import org.rebeam.boxes.core.{ShelfDefault, BoxNow, Shelf, Box}
//
//object GCDemo {
//
//  case class Loopy(a: Box[String], l: Box[Option[Loopy]]) {
//    toString
//  }
//
//  def makeLoopy(implicit shelf: Shelf): Loopy = Loopy(BoxNow("a"), BoxNow(None))
//
//  def makeLoop(implicit shelf: Shelf): (Loopy, Loopy) = {
//    val a = Loopy(BoxNow("a"), BoxNow(None))
//    val b = Loopy(BoxNow("b"), BoxNow(Some(a)))
//    a.l.now() = Some(b)
//    (a, b)
//  }
//
//  def main(args: Array[String]) {
//    implicit val shelf = new ShelfDefault
//
//    Range(0, 10000).foreach{_ => makeLoop}
//
//    Range(0, 100).foreach{_ => System.gc()}
//  }
//
//}