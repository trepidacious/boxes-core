package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._

case class ListIndices[T](selected: Box[Set[T]], indices: Box[Set[Int]])

object ListIndices {
  
  def apply[T](list: Box[_ <: Seq[T]], selectAllByDefault: Boolean = true): BoxScript[ListIndices[T]] = for {
    selected <- create(Set.empty[T])
    indices <- create(Set.empty[Int])

    r <- createReaction(for {
      lis <- list()
      sel <- selected()
      ind <- indices()

      cs <- changedSources()

      consistent = (ind, sel, lis) match {
        case (i, s, Nil) if i.isEmpty && s.isEmpty => true                  //Must select nothing in empty list
        case (i, s, _) if i.isEmpty && s.isEmpty => !selectAllByDefault     //In non-empty list, selecting nothing is ok iff we are not selecting all by default
        case (i, s, l) =>                                                  //If we have a selection and a list, then check all indices are in list, and selection matches indices
        val iInList = i.filter(i => i >= 0 && i < l.size)
        iInList.size == i.size && iInList.map(i => l(i)) == s
      }

      clear = (indices() = Set.empty) andThen (selected() = Set.empty)
      selectAll = (indices() = Range(0, lis.size).toSet) andThen (selected() = lis.toSet)
      default = if (!selectAllByDefault || lis.isEmpty) clear else selectAll

      useIndices = {
        //Filter indices to be in list
        val newI = ind.filter(i => i >= 0 && i < lis.size)
        //If empty, use default
        if (newI.isEmpty) {
          default
        //Otherwise use indices to update selection
        } else {
          selected() = newI.map(i => lis(i))
        }
      }

      //Use first selected index, and try to preserve it
      useFirstIndex = ind.reduceLeftOption{(a, b) => Math.min(a, b)} match {
        case None =>
          default
        case Some(i) if i < 0 =>
          (indices() = Set(0)) andThen (selected() = Set(lis.head))
        case Some(i) if i >= lis.size =>
          (indices() = Set(lis.size-1)) andThen (selected() = Set(lis(lis.size-1)))
        case Some(i) =>
          (indices() = Set(i)) andThen (selected() = Set(lis(i)))
      }

      //If we are already consistent, nothing to do
      _ <- if (consistent) {
        list()  //TODO this is just used as a noop, how should we do this?

      //If list is empty, no selection
      } else if (lis.isEmpty) {
        clear

      //If just the index has changed, it is authoritative
      } else if (cs == Set(indices)) {
        useIndices

      //Otherwise try to use the selection
      } else {
        //Find indices of selection in list
        val newIndices = sel.map(lis.indexOf(_)).filter(_ > -1)

        //Some of selection is still in list, update index
        if (!newIndices.isEmpty) {
          indices() = newIndices

        //Selection is completely missing from list, if just list has changed, use
        //first index to look up new selection
        } else if (cs == Set(list)) {
          useFirstIndex

        //Otherwise just use default
        } else {
          default
        }
      }

    } yield ())

    _ <- selected.attachReaction(r)
    _ <- indices.attachReaction(r)

  } yield ListIndices(selected, indices)
}