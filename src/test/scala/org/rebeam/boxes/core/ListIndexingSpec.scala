package org.rebeam.boxes.core

import org.rebeam.boxes.core._
import org.rebeam.boxes.core.data._
import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import scalaz._
import Scalaz._

class ListIndexingSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  def assertBox[A](a: BoxScript[A], expected: A): BoxScript[Unit] = a.map(x => assert(x == expected))

  "ListIndexing" should {
    "keep selection set within list using setIsInList and no default selection" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create(Set("a", "b", "c", "f"))

          r <- ListIndexing.setIsInList(l, s)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("b", "c", "f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("c", "f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("f"))

          _ <- l() = List("f", "f", "f")
          _ <- assertBox(s, Set("f"))

          _ <- l() = List()
          _ <- assertBox(s, Set[String]())

          _ <- l() = List("f", "f", "f")
          _ <- assertBox(s, Set[String]())

          _ <- s() = Set("f")
          _ <- assertBox(s, Set("f"))

          _ <- l() = List("A", "B", "C")
          _ <- assertBox(s, Set[String]())

          _ <- s() = Set("A", "C", "D")
          _ <- assertBox(s, Set("A", "C"))
        } yield ()
      }
    }

    "keep selection set within list using setIsInList and selecting first by default" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create(Set("a", "b", "c", "f"))

          r <- ListIndexing.setIsInList(l, s, ListIndexing.selectFirstAsSet[String])

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("b", "c", "f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("c", "f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("f"))

          _ <- l() = List("f", "f", "f")
          _ <- assertBox(s, Set("f"))

          _ <- l() = List()
          _ <- assertBox(s, Set[String]())

          _ <- l() = List("f", "f", "f")
          _ <- assertBox(s, Set[String]("f"))

          _ <- s() = Set("f")
          _ <- assertBox(s, Set("f"))

          //Different behaviour here when selecting first by default
          _ <- l() = List("A", "B", "C")
          _ <- assertBox(s, Set("A"))

          _ <- s() = Set("A", "C", "D")
          _ <- assertBox(s, Set("A", "C"))

          //Selecting outside the list should select first
          _ <- s() = Set("X")
          _ <- assertBox(s, Set("A"))

          //Select something valid should work, then selecting nothing should select first instead
          _ <- s() = Set("B")
          _ <- assertBox(s, Set("B"))
          _ <- s() = Set.empty[String]
          _ <- assertBox(s, Set("A"))

          //Selecting something or nothing in an empty list should select nothing
          _ <- l() = List()
          _ <- assertBox(s, Set[String]())
          _ <- s() = Set("B")
          _ <- assertBox(s, Set[String]())
          _ <- s() = Set.empty[String]
          _ <- assertBox(s, Set[String]())          

        } yield ()
      }
    }

    "keep selection set within list using setIsInList and selecting all by default" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create(Set("a", "b", "c", "f"))

          r <- ListIndexing.setIsInList(l, s, ListIndexing.selectAllAsSet[String])

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("b", "c", "f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("c", "f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("f"))

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, Set("f"))

          _ <- l() = List("f", "f", "f")
          _ <- assertBox(s, Set("f"))

          _ <- l() = List()
          _ <- assertBox(s, Set[String]())

          _ <- l() = List("f", "f", "f")
          _ <- assertBox(s, Set[String]("f"))

          _ <- s() = Set("f")
          _ <- assertBox(s, Set("f"))

          //Different behaviour here when selecting all by default
          _ <- l() = List("A", "B", "C")
          _ <- assertBox(s, Set("A", "B", "C"))

          _ <- s() = Set("A", "C", "D")
          _ <- assertBox(s, Set("A", "C"))

          //Selecting outside the list should select all
          _ <- s() = Set("X")
          _ <- assertBox(s, Set("A", "B", "C"))

          //Select something valid should work, then selecting nothing should select all instead
          _ <- s() = Set("B")
          _ <- assertBox(s, Set("B"))
          _ <- s() = Set.empty[String]
          _ <- assertBox(s, Set("A", "B", "C"))

          //Selecting something or nothing in an empty list should select nothing
          _ <- l() = List()
          _ <- assertBox(s, Set[String]())
          _ <- s() = Set("B")
          _ <- assertBox(s, Set[String]())
          _ <- s() = Set.empty[String]
          _ <- assertBox(s, Set[String]())          

        } yield ()
      }
    }

    "keep selection option within list using optionIsInList and no default selection" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create("a".some)

          r <- ListIndexing.optionIsInList(l, s)

          _ <- assertBox(s, "a".some)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, None)

          _ <- s() = "c".some
          _ <- assertBox(s, "c".some)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, "c".some)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, None)

          _ <- l() = List("c", "c", "c")
          _ <- assertBox(s, None)

          _ <- s() = "c".some
          _ <- assertBox(s, "c".some)

          _ <- s() = "X".some
          _ <- assertBox(s, None)

        } yield ()
      }
    }

    "keep selection option within list using optionIsInList and selecting first by default" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create("a".some)

          r <- ListIndexing.optionIsInList(l, s, ListIndexing.selectFirstAsOption[String])

          _ <- assertBox(s, "a".some)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, "b".some)

          _ <- s() = "c".some
          _ <- assertBox(s, "c".some)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, "c".some)

          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(s, "d".some)

          _ <- l() = List("c", "c", "c")
          _ <- assertBox(s, "c".some)

          _ <- l() = List()
          _ <- assertBox(s, None)

          _ <- l() = List("A", "B", "C")
          _ <- assertBox(s, "A".some)

          _ <- s() = "B".some
          _ <- assertBox(s, "B".some)

          _ <- s() = "X".some
          _ <- assertBox(s, "A".some)

          _ <- l() = List()
          _ <- assertBox(s, None)
          _ <- s() = "X".some
          _ <- assertBox(s, None)
          _ <- s() = None
          _ <- assertBox(s, None)

        } yield ()
      }
    }


    "read/write selection as index: Option[Int] using indexFromListAndOption" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create("a".some)

          i = ListIndexing.indexFromListAndOption(l, s)

          _ <- assertBox(i(), 0.some)

          //Remove "a" from list, so selection is no longer in it
          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(i(), None)

          //Select "c" and check index
          _ <- s() = "c".some
          _ <- assertBox(i(), 1.some)

          //Try to select some indices outside list, and fail, clearing selection
          _ <- i() = -1.some
          _ <- assertBox(s, None)
          _ <- assertBox(i(), None)

          _ <- i() = 10.some
          _ <- assertBox(s, None)
          _ <- assertBox(i(), None)

          //Reinstate selection
          _ <- s() = "d".some
          _ <- assertBox(i(), 2.some)

          //Another invalid selection
          _ <- s() = "X".some
          _ <- assertBox(i(), None)

          //Now change selection using valid indices
          _ <- i() = 3.some
          _ <- assertBox(s, "e".some)
          _ <- assertBox(i(), 3.some)

          _ <- i() = 4.some
          _ <- assertBox(s, "f".some)
          _ <- assertBox(i(), 4.some)

          //Clear selection directly and check index
          _ <- s() = None
          _ <- assertBox(s, None)
          _ <- assertBox(i(), None)

        } yield ()
      }      
    }

    "read/write selection as indices: Set[Int] using indexFromListAndSet" in {
      atomic {
        for {
          l <- create(List("a", "b", "c", "d", "e", "f"))
          s <- create(Set("a"))

          i = ListIndexing.indexFromListAndSet(l, s)

          _ <- assertBox(i(), Set(0))

          //Remove "a" from list, so selection is no longer in it
          _ <- modifyBox(l, (l: List[String]) => l.tail)
          _ <- assertBox(i(), Set.empty[Int])

          //Select "c" and check indices
          _ <- s() = Set("c")
          _ <- assertBox(i(), Set(1))

          //Try to select some indices outside list, and fail, clearing selection
          _ <- i() = Set(-1)
          _ <- assertBox(s, Set.empty[String])
          _ <- assertBox(i(), Set.empty[Int])

          _ <- i() = Set(10, 100)
          _ <- assertBox(s, Set.empty[String])
          _ <- assertBox(i(), Set.empty[Int])

          //Reinstate selection
          _ <- s() = Set("d")
          _ <- assertBox(i(), Set(2))

          //Another invalid selection
          _ <- s() = Set("X")
          _ <- assertBox(i(), Set.empty[Int])

          //Now change selection using valid indices
          _ <- i() = Set(3)
          _ <- assertBox(s, Set("e"))
          _ <- assertBox(i(), Set(3))

          _ <- i() = Set(4)
          _ <- assertBox(s, Set("f"))
          _ <- assertBox(i(), Set(4))

          //Clear selection directly and check indices
          _ <- s() = Set.empty[String]
          _ <- assertBox(s, Set.empty[String])
          _ <- assertBox(i(), Set.empty[Int])

          //Select multiple valid indices and check selected elements
          _ <- i() = Set(3, 4)
          _ <- assertBox(s, Set("e", "f"))
          _ <- assertBox(i(), Set(3, 4))

          //Select multiple valid elements and check selected indices
          _ <- s() = Set("c", "d")
          _ <- assertBox(s, Set("c", "d"))
          _ <- assertBox(i(), Set(1, 2))

          //Select multiple valid indices and some invalid indices and check selected elements
          _ <- i() = Set(-1, 3, 4, 10, 100)
          _ <- assertBox(s, Set("e", "f"))
          _ <- assertBox(i(), Set(3, 4))

          //Select multiple valid elements and some invalid elements and check selected indices
          _ <- s() = Set("A", "B", "c", "d", "X", "Y")
          _ <- assertBox(s, Set("A", "B", "c", "d", "X", "Y"))  //Note we are not constraining selection to be in list, so it keeps the invalid selections
          _ <- assertBox(i(), Set(1, 2))                        //But the indices only reflect the valid ones

        } yield ()
      }      
    }
  }
}
