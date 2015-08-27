package org.rebeam.boxes.core.free.reaction

import org.rebeam.boxes.core.free._
import BoxTypes._
import BoxUtils._

import scalaz._
import Scalaz._

object RadioReaction {

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

}
