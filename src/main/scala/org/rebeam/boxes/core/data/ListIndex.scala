package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

case class ListIndex[T](selected: Box[Option[T]], index: Box[Option[Int]])

object ListIndex {
  def apply[T](list: BoxR[_ <: Seq[T]], selectFirstByDefault: Boolean = true): BoxScript[ListIndex[T]] = for {
    selected <- create(None: Option[T])
    index <- create(None: Option[Int])

    r <- createReaction(for {
      l <- list
      s <- selected()
      i <- index()

      cs <- changedSources

      consistent = (i, s, l) match {
        //Must select None in empty list
        case (None, None, Nil)                              => true
        //In non-empty list, selecting None is ok iff we are not selecting first by default
        case (None, None, _)                                => !selectFirstByDefault
        //If index is valid, then consistent if selected object is at index in list
        case (Some(i), Some(s), _) if i >= 0 && i < l.size  => s == l(i)
        //No consistent case applies
        case _ => false
      }

      default = if (!selectFirstByDefault || l.isEmpty) {
        (index() = None) andThen (selected() = None)
      } else {
        (index() = Some(0)) andThen (selected() = Some(l(0)))
      }

      useIndex = i match {
        case None => default
        case Some(i) if i < 0       => (index() = Some(0)) andThen (selected() = Some(l(0)))
        case Some(i) if i >= l.size => (index() = Some(l.size - 1)) andThen (selected() = Some(l(l.size - 1)))
        case Some(i)                => (index() = Some(i)) andThen (selected() = Some(l(i)))
      }

      //If already consistent, nothing to do
      _ <- if (consistent) {
        nothing

      //If list is empty, no selection
      } else if (l.isEmpty) {
        (index() = None) andThen (selected() = None)

      //If just the index has changed, it is authoritative
      } else if (cs == Set(index)) {
        useIndex

      //Otherwise try to use the selection
      } else {
        val newIndex = s.map(s => l.indexOf(s)).getOrElse(-1)

        //Selection is still in list, update index
        if (newIndex > -1) {
          index() = Some(newIndex)

        //TODO - this requires a Box rather than a BoxR for list - can we replicate this effect somehow?
        //Selection is not in list, if just list has changed, use
        //index to look up new selection
        // } else if (cs == Set(list)) {
        //   useIndex

        //Otherwise just use default
        } else {
          default
        }
      }

    } yield ())

    _ <- selected.attachReaction(r)
    _ <- index.attachReaction(r)

  } yield ListIndex(selected, index)
}