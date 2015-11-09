package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

case class ListIndex[T](selected: Box[Option[T]], index: Box[Option[Int]])

object ListIndexing {

  def listSetIntersection[T](l: List[T], s: Set[T]): Set[T] = s.intersect(l.toSet)

  def setIsInList[T](l: BoxR[List[T]], s: BoxM[Set[T]]): BoxScript[Reaction] = for {
    r <- createReaction {
      for {
        lv <- l
        sv <- s()
        _ <- s() = listSetIntersection(lv, sv)
      } yield ()
    }
  } yield r

  def optionIsInList[T](l: BoxR[List[T]], o: BoxM[Option[T]]): BoxScript[Reaction] = for {
    r <- createReaction {
      for {
        lv <- l
        ov <- o()
        _ <- o() = ov.filter(t => lv.contains(t))
      } yield ()
    }
  } yield r

  def indexInList[T](l: List[T], o: Option[T]): Option[Int] = o.flatMap(t => {
    val i = l.indexOf(t)
    if (i < 0) None else Some(i)
  })

  def indexFromListAndOption[T](l: BoxR[List[T]], o: BoxM[Option[T]]): BoxM[Option[Int]] = BoxM(
    read = for {
      lv <- l
      ov <- o() 
    } yield indexInList(lv, ov),

    write = (i: Option[Int]) => for {
      lv <- l
      _ <- o() = i.filter(iv => iv >= 0 && iv < lv.size).map(lv(_))
    } yield ()
  )

  def indicesInList[T](l: List[T], s: Set[T]): Set[Int] = s.flatMap(t => {
    val i = l.indexOf(t)
    if (i < 0) Set.empty[Int] else Set(i)
  })

  def indexFromListAndSet[T](l: BoxR[List[T]], s: BoxM[Set[T]]): BoxM[Set[Int]] = BoxM(
    read = for {
      lv <- l
      sv <- s() 
    } yield indicesInList(lv, sv),

    write = (i: Set[Int]) => for {
      lv <- l
      _ <- s() = i.filter(iv => iv >= 0 && iv < lv.size).map(lv(_))
    } yield ()
  )

}

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

        //Selection is not in list, and neither selected nor indices has
        //changed, so we assume just list has changed, and use first index to look up new selection
        } else if (!cs.contains(selected) && !cs.contains(index)) {
          useIndex

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