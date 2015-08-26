package org.rebeam.boxes.core.free.reaction

import org.rebeam.boxes.core.free.{Reaction, Box}
import org.rebeam.boxes.core.free.BoxTypes.BoxScript

import scalaz._
import Scalaz._

import org.rebeam.boxes.core.free.BoxDeltaF._

object RadioReaction {
//  for {
//    a <- x
//    b <- y
//    c <- z
//  } yield (Vector() + a + b + c)
//
//  x.flatmap(a => y.flatmap(b => z.map (c => List() + a + b + c)))

//  val l: Option[List[Int]] = List(1.some, 2.some).sequence
//
//  def test(options: List[Box[Boolean]]): BoxScript[List[Boolean]] = options.traverseU(o=>o()) //Traverse[List].traverse(options)(o => o()) //options.sequence

  def apply(options: List[Box[Boolean]]): BoxScript[Reaction] = for {
    r <- createReaction{
      for {
        cs <- changedSources()
        oVals <- options traverseU { o => o() }
        _ <- desiredStates(options, oVals, cs) traverseU { case (b, v) => b() = v }
      } yield ()
    }
    _ <- options traverseU {o => o.attachReaction(r)}
  } yield r

  def desiredStates(b: List[Box[Boolean]], v: List[Boolean], cs: Set[Box[_]]): List[(Box[Boolean], Boolean)] = {
    val boxesAndValues = b.zip(v)
    val activeOptions = boxesAndValues.filter{case (b, v) => v}
    val changedSelectedOptions = activeOptions.filter { case (b, v) => cs.contains(b) }
    val selected = changedSelectedOptions.headOption.map(_._1).orElse(activeOptions.headOption.map(_._1))
    selected match {
      case Some(selectedBox) => b.map(b => (b, b eq selectedBox))
      case None => b.map(b => (b, false))
    }
  }

  //    val r = txn.createReaction(implicit rt => {
  //      val activeOptions = options.filter(o => o())
  //
  //      //If more than one option is selected, find the best option to leave selected
  //      if (activeOptions.size > 1) {
  //        //Use the first selected option that has changed, otherwise just the first selected option
  //        val changedSelectedOptions = activeOptions.toSet.intersect(rt.changedSources)
  //        val selected = changedSelectedOptions.headOption match {
  //          case Some(o) => o
  //          case _ => activeOptions.head
  //        }
  //        activeOptions.foreach{ao => if (ao ne selected) ao() = false}
  //      }
  //    })
  //    options.foreach(o => o.retainReaction(r))
  //    r

}
