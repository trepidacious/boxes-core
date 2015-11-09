package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

object ListIndexing {

  def listSetIntersection[T](l: List[T], s: Set[T]): Set[T] = s.intersect(l.toSet)

  def selectFirstAsSet[T] = (l: List[T]) => l.headOption.toSet
  def selectAllAsSet[T] = (l: List[T]) => l.toSet
  def selectNoneAsSet[T] = (l: List[T]) => Set.empty[T]

  def setIsInList[T](l: BoxR[List[T]], s: BoxM[Set[T]], selectIfEmpty: (List[T]) => Set[T] = selectNoneAsSet[T]): BoxScript[Reaction] = for {
    r <- createReaction {
      for {
        lv <- l
        sv <- s()
        intersection = listSetIntersection(lv, sv)
        withDefault = if (intersection.isEmpty) selectIfEmpty(lv) else intersection        
        _ <- s() = withDefault
      } yield ()
    }
  } yield r

  def selectFirstAsOption[T] = (l: List[T]) => l.headOption
  def selectNoneAsOption[T] = (l: List[T]) => None: Option[T]

  def optionIsInList[T](l: BoxR[List[T]], o: BoxM[Option[T]], selectIfNone: (List[T]) => Option[T] = selectNoneAsOption[T]): BoxScript[Reaction] = for {
    r <- createReaction {
      for {
        lv <- l
        ov <- o()
        _ <- o() = ov.filter(t => lv.contains(t)).orElse(selectIfNone(lv))
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
