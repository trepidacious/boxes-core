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

  "ListIndices" should {

    "work without selecting all by default" in {
      atomic {

        for {

          l <- create(List("a", "b", "c", "d", "e", "f", "g", "h"))
          i <- ListIndices(l, false)

          // assert(i.indices() === Set())
          // assert(i.selected() === Set())

          // //We can make a selection
          // i.indices() = Set(4)
          // assert(i.indices() === Set(4))
          // assert(i.selected() === Set("e"))

          // //Can't select past end of list - just selects default (nothing)
          // i.indices() = Set(10)
          // assert(i.indices() === Set())
          // assert(i.selected() === Set())

          // //Messing with the list shouldn't change selection
          // i.indices() = Set(4)
          // l() = "A" :: l().tail
          // assert(i.indices() === Set(4))
          // assert(i.selected() === Set("e"))

          // //Removing elements should preserve the selection
          // l() = l().tail.tail
          // assert(i.indices() === Set(2))
          // assert(i.selected() === Set("e"))

          // //Adding elements should preserve the selection
          // l() = "X" :: "Y" :: "Z" :: l()
          // assert(i.indices() === Set(5))
          // assert(i.selected() === Set("e"))

          // //Removing the selected element should move selection to element at same index in new list
          // l() = List("X", "Y", "Z", "c", "d", "f", "g", "h")
          // assert(i.indices() === Set(5))
          // assert(i.selected() === Set("f"))

          // //Shortening list so that index is not in it should move selection to end of list instead
          // l() = List("X", "Y", "Z")
          // assert(i.indices() === Set(2))
          // assert(i.selected() === Set("Z"))

          // //Using indices inside and outside the list will retain only those inside it
          // i.indices() = Set(1, 2, 3, 4, 5)
          // assert(i.indices() === Set(1, 2))
          // assert(i.selected() === Set("Y", "Z"))

        } yield ()
      }
    }
  }
  
//
//    "track correctly with multiple selections" in {
//      val l = ListVar(0, 1, 2, 3, 4, 5, 6, 7)
//      val i = ListIndices(l)
//
//      i() = Set(0, 1, 5, 6)
//      assert(i() === Set(0, 1, 5, 6))
//
//      l(0) = 42
//      assert(i() === Set(0, 1, 5, 6))
//
//      l(0) = 0
//      assert(i() === Set(0, 1, 5, 6))
//
//      l.remove(2, 2)
//      assert(i() === Set(0, 1, 3, 4))
//
//      l.insert(2, 2, 3)
//      assert(i() === Set(0, 1, 5, 6))
//
//      //Completely replace the List with a new one, should reset selection
//      l() = List(0, 1, 2, 3)
//      assert(i() === Set(0))
//    }
//
//  }
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

}