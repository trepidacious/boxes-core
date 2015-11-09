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

class ListIndexSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  //FIXME we should try to test effect of GC - make sure that reactions
  //are not GCed as long as they have a source

  // case class Person(name: Box[String], age: Box[Int], friend: Box[Option[Person]], spouse: Box[Option[Person]], numbers: Box[List[Int]], accounts: Box[Map[String, Int]]) {
  //   def asString(implicit txn: TxnR) = "Person(" + name() + ", " + age() + ", " + friend() + ", " + spouse() + ", " + numbers() + ", " + accounts + ")"
  // }

  // object Person {
  //   def default(implicit txn: Txn): Person = {
  //     Person(Box(""), Box(0), Box(None), Box(None), Box(Nil), Box(Map.empty))
  //   }
  //   def now(implicit shelf: Shelf) = shelf.transact(implicit txn => default)
  // }

  def assertBox[A](a: BoxScript[A], expected: A): BoxScript[Unit] = a.map(x => assert(x == expected))

  "ListIndexing" should {
    "keep selection set within list using setIsInList" in {
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

    "keep selection option within list using optionIsInList" in {
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
          _ <- assertBox(s, Set("c", "d"))
          _ <- assertBox(i(), Set(1, 2))

        } yield ()
      }      
    }

  }


  "ListIndices" should {

    "work without selecting all by default" in {
      atomic {

        for {

          l <- create(List("a", "b", "c", "d", "e", "f", "g", "h"))
          i <- ListIndices(l, false)

          _ <- assertBox(i.indices(), Set[Int]())
          _ <- assertBox(i.selected(), Set[String]())

          //We can make a selection
          _ <- i.indices() = Set(4)
          _ <- assertBox(i.indices(), Set(4))
          _ <- assertBox(i.selected(), Set("e"))

          //Can't select past end of list - just selects default (nothing)
          _ <- i.indices() = Set(10)
          _ <- assertBox(i.indices(), Set[Int]())
          _ <- assertBox(i.selected(), Set[String]())

          //Messing with the list shouldn't change selection
          _ <- i.indices() = Set(4)
          _ <- modifyBox(l, (lv: List[String]) => "A" :: lv.tail)
          _ <- assertBox(i.indices(), Set(4))
          _ <- assertBox(i.selected(), Set("e"))

          //Removing elements should preserve the selection
          _ <- modifyBox(l, (lv: List[String]) => lv.tail.tail)
          _ <- assertBox(i.indices(), Set(2))
          _ <- assertBox(i.selected(), Set("e"))

          //Adding elements should preserve the selection
          _ <- modifyBox(l, (lv: List[String]) => "X" :: "Y" :: "Z" :: lv)
          _ <- assertBox(i.indices(), Set(5))
          _ <- assertBox(i.selected(), Set("e"))

          //Removing the selected element should move selection to element at same index in new list
          _ <- l() = List("X", "Y", "Z", "c", "d", "f", "g", "h")
          _ <- assertBox(i.indices(), Set(5))
          _ <- assertBox(i.selected(), Set("f"))

          //Shortening list so that index is not in it should move selection to end of list instead
          _ <- l() = List("X", "Y", "Z")
          _ <- assertBox(i.indices(), Set(2))
          _ <- assertBox(i.selected(), Set("Z"))

          //Using indices inside and outside the list will retain only those inside it
          _ <- i.indices() = Set(1, 2, 3, 4, 5)
          _ <- assertBox(i.indices(), Set(1, 2))
          _ <- assertBox(i.selected(), Set("Y", "Z"))

        } yield ()
      }
    }
  }
  

  "track correctly with multiple selections" in {

    atomic {

      for {

        l <- create(List(0, 1, 2, 3, 4, 5, 6, 7))
        i <- ListIndices(l)

        _ <- i.indices() = Set(0, 1, 5, 6)
        _ <- assertBox(i.indices(), Set(0, 1, 5, 6))

        //If we change the first element, it is no longer selected
        _ <- modifyBox(l, (lv: List[Int]) => 42 :: lv.tail)
        _ <- assertBox(i.indices(), Set(1, 5, 6))

        //If we remove some elements from the list, the selected indices will adapt
        // _ <- l() = List(42, 1, 4, 5, 6, 7)
        // _ <- assertBox(i.indices(), Set(1, 3, 4))
        // _ <- assertBox(i.selected(), Set(1, 5, 6))

        // l.insert(2, 2, 3)
        // assert(i() === Set(0, 1, 5, 6))

        // //Completely replace the List with a new one, should reset selection
        // l() = List(0, 1, 2, 3)
        // assert(i() === Set(0))

      } yield ()  
    }
  }


}


//
//  "ListIndex" should {
//    "track correctly" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7)
//      val i = ListIndex(l)
//
//      assert(i() === Some(0))
//
//      //Can't select past end of list - just selects last index
//      i() = Some(10)
//      assert(i() === Some(7))
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      l(0) = 42
//      assert(i() === Some(4))
//
//      l(0) = 0
//      assert(i() === Some(4))
//
//      l.remove(0, 2)
//      assert(i() === Some(2))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      l.insert(0, 0, 1)
//      assert(i() === Some(4))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      //Completely replace the List with a new one, should reset selection
//      l() = List(0, 1, 2, 3)
//      assert(i() === Some(0))
//    }
//
//    "work with ListSelection" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7)
//      val i = ListIndex(l)
//      val s = ListSelection(l, i)
//
//      assert(i() === Some(0))
//      assert(s() === Some(0))
//
//      i() = Some(10)
//      assert(i() === Some(7))
//      assert(s() === Some(7))
//
//      i() = Some(4)
//      assert(i() === Some(4))
//      assert(s() === Some(4))
//
//      l(4) = 42
//      assert(i() === Some(4))
//      assert(s() === Some(42))
//
//
//      l(4) = 4
//      assert(i() === Some(4))
//      assert(s() === Some(4))
//
//      l.remove(0, 2)
//      assert(i() === Some(2))
//      assert(l(i().getOrElse(-1)) === 4)
//      assert(s() === Some(4))
//
//      l.insert(0, 0, 1)
//      assert(i() === Some(4))
//      assert(l(i().getOrElse(-1)) === 4)
//      assert(s() === Some(4))
//
//      //Completely replace the List with a new one, should reset selection
//      l() = List(0, 1, 2, 3)
//      assert(i() === Some(0))
//      assert(s() === Some(0))
//
//      l() = List()
//      assert(s() === None)
//
//    }
//
//    "work with ListPath when modifying ListPath" in {
//
//      class ListNode {
//        val list = ListVar(0, 1, 2, 3, 4, 5, 6, 7)
//      }
//      val ln = new ListNode()
//
//      val l = ListPath(ln.list)
//      val i = ListIndex(l)
//
//      assert(i() === Some(0))
//
//      //Can't select past end of list - just selects last index
//      i() = Some(10)
//      assert(i() === Some(7))
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      l(0) = 42
//      assert(i() === Some(4))
//
//      l(0) = 0
//      assert(i() === Some(4))
//
//      l.remove(0, 2)
//      assert(i() === Some(2))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      l.insert(0, 0, 1)
//      assert(i() === Some(4))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      //Completely replace the List with a new one, should reset selection
//      l() = List(0, 1, 2, 3)
//      assert(i() === Some(0))
//    }
//
//    "work with ListPath when modifying ListPath endpoint" in {
//
//      class ListNode {
//        val list = ListVar(0, 1, 2, 3, 4, 5, 6, 7)
//      }
//      val ln = new ListNode()
//
//      val l = ListPath(ln.list)
//      val i = ListIndex(l)
//
//      assert(i() === Some(0))
//
//      //Can't select past end of list - just selects last index
//      i() = Some(10)
//      assert(i() === Some(7))
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      ln.list(0) = 42
//      assert(i() === Some(4))
//
//      ln.list(0) = 0
//      assert(i() === Some(4))
//
//      ln.list.remove(0, 2)
//      assert(i() === Some(2))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      ln.list.insert(0, 0, 1)
//      assert(i() === Some(4))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      //Completely replace the List with a new one, should reset selection
//      ln.list() = List(0, 1, 2, 3)
//      assert(i() === Some(0))
//    }
//
//    "work with ListPath when modifying path" in {
//      class ListNode(l:Int*) {
//        val list = ListVar(l:_*)
//      }
//
//      val ln = new ListNode(0, 1, 2, 3, 4, 5, 6, 7)
//      val ln2 = new ListNode(0, 1, 2, 3, 4, 5)
//      val s = Var(true)
//
//
//      val l = ListPath(if (s()) ln.list else ln2.list)
//      val i = ListIndex(l)
//
//      assert(i() === Some(0))
//      assert(l().sameElements(List(0, 1, 2, 3, 4, 5, 6, 7)))
//
//
//      //Can't select past end of list - just selects last index
//      i() = Some(10)
//      assert(i() === Some(7))
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      ln.list(0) = 42
//      assert(i() === Some(4))
//
//      ln.list(0) = 0
//      assert(i() === Some(4))
//
//      ln.list.remove(0, 2)
//      assert(i() === Some(2))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      ln.list.insert(0, 0, 1)
//      assert(i() === Some(4))
//      assert(l(i().getOrElse(-1)) === 4)
//
//      //Completely replace the List with a new one, should reset selection
//      ln.list() = List(0, 1, 2, 3)
//      assert(i() === Some(0))
//
//      i() = Some(2)
//      assert(i() === Some(2))
//
//      //Change the path, should reset selection
//      s() = false
//      assert(i() === Some(0))
//      assert(l().sameElements(List(0, 1, 2, 3, 4, 5)))
//
//      //Check selection still works on new path
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      ln2.list.remove(0, 2)
//      assert(i() === Some(2))
//      assert(l(i().getOrElse(-1)) === 4)
//
//
//    }
//  }
//
//
//
//  "ListIndex with loseIndexOnDeletion false" should {
//    "leave selection alone on deletion after selection" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=false)
//
//      i() = Some(4)
//      l.remove(5, 4)
//      assert(i() === Some(4))
//    }
//
//    "move selection on deletion before selection" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=false)
//
//      i() = Some(4)
//      l.remove(0, 3)
//      assert(i() === Some(1))
//    }
//
//    "select last index after deletion from selection to end" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=false)
//
//      i() = Some(4)
//      l.remove(4, 5)
//      assert(i() === Some(3))
//    }
//
//    "select next undeleted index after deletion from selection to before end" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=false)
//
//      i() = Some(4)
//      l.remove(4, 2)
//      assert(i() === Some(4))
//    }
//
//    "change invalid initial selection to valid one" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, Some(100), loseIndexOnDeletion=false)
//
//      assert(i() === Some(8))
//    }
//
//    "clear selection on list change, if selectFirstRatherThanNone is false" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=false, selectFirstRatherThanNone = false)
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      l() = List(42)
//      assert(i() === None)
//    }
//
//    "clear selection then prefer 0 on list change for non-empty list, if selectFirstRatherThanNone is true" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=false, selectFirstRatherThanNone = true)
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      l() = List(42)
//      assert(i() === Some(0))
//
//      l() = List()
//      assert(i() === None)
//    }
//  }
//
//  "ListIndex with loseIndexOnDeletion true" should {
//    "leave selection alone on deletion after selection" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=true)
//
//      i() = Some(4)
//      l.remove(5, 4)
//      assert(i() === Some(4))
//    }
//
//    "move selection on deletion before selection" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=true)
//
//      i() = Some(4)
//      l.remove(0, 3)
//      assert(i() === Some(1))
//    }
//
//    "clear selection on delete, if selectFirstRatherThanNone is false" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=true, selectFirstRatherThanNone=false)
//
//      i() = Some(4)
//      l.remove(4, 2)
//      assert(i() === None)
//    }
//
//    "clear selection then prefer 0, if selectFirstRatherThanNone is true" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=true, selectFirstRatherThanNone=true)
//
//      i() = Some(4)
//      l.remove(4, 2)
//      assert(i() === Some(0))
//    }
//
//    "change invalid initial selection to valid one" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, Some(100), loseIndexOnDeletion=true)
//
//      assert(i() === Some(8))
//    }
//
//    "clear selection on list change, if selectFirstRatherThanNone is false" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=true, selectFirstRatherThanNone = false)
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      l() = List(42)
//      assert(i() === None)
//    }
//
//    "clear selection then prefer 0 on list change for non-empty list, still None for empty list, if selectFirstRatherThanNone is true" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7, 8)
//      val i = ListIndex(l, loseIndexOnDeletion=true, selectFirstRatherThanNone = true)
//
//      i() = Some(4)
//      assert(i() === Some(4))
//
//      l() = List(42)
//      assert(i() === Some(0))
//
//      l() = List()
//      assert(i() === None)
//    }
//  }

// }