package org.rebeam.boxes.core

import org.rebeam.boxes.core.data.ListIndices
import org.rebeam.boxes.core.reaction.Path
import org.rebeam.boxes.core.util.ImmediateExecutor
import org.scalatest.WordSpec

class BoxSpec extends WordSpec {

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

  // "Cal" should {

  //   "work in simple case" in {
  //     implicit val shelf = ShelfDefault()

  //     shelf.transact(implicit txn => {
  //       val a = Box(2)
  //       val b = Box.calc(implicit txn => a() + 3)

  //       assert(b() === 5)

  //       val c = Box.calc(implicit txn => a() + b())

  //       assert(c() === 7)

  //       a() = 4

  //       assert(b() === 7)
  //       assert(c() === 11)
  //     })

  //   }

  //   "permit simple unidirectional paths" in {
  //     implicit val shelf = ShelfDefault()

  //     val cate = Person.now
  //     cate.name.now() = "Cate"

  //     val alice = Person.now
  //     alice.name.now() = "Alice"

  //     val bob = Person.now
  //     bob.name.now() = "Bob"
  //     bob.friend.now() = Some(cate)

  //     val bobsFriendsName = BoxNow.calc(implicit txn => bob.friend().map(_.name()))

  //     //Notes:
  //     // You REALLY shouldn't use a var like "alterations"; if you want a similar effect in non-test code you should use an
  //     // auto that will update a Box instead - this will be transactional and can be viewed etc. However for this test
  //     // we want to use a view.
  //     // Also in this case we really just want to see how many times the view is called, and so we use ImmediateExecutor to
  //     // keep the view execution in the same thread used to run the transaction - everything is single threaded.
  //     // Note we also set onlyMostRecent to false so we see all changes, since we want to check the
  //     // view is executed at all appropriate points.
  //     var alterations = 0
  //     val aView = shelf.now.view(implicit txn => {
  //       //Read this so that we are called when it changes
  //       bobsFriendsName()
  //       alterations += 1
  //     }, ImmediateExecutor, false)

  //     //Starts from 1, because view is called once when registered
  //     assert(alterations === 1)

  //     assert(bobsFriendsName.now() === Some("Cate"))

  //     bob.friend.now() = Some(alice)

  //     assert(alterations === 2)

  //     assert(bobsFriendsName.now() === Some("Alice"))

  //     //Should see no changes to bobsFriendsName when something not
  //     //in the path changes, even if it is deeply referenced, or used to be part of path, etc.
  //     cate.name.now() = "Katey"

  //     assert(alterations === 2)

  //     assert(bobsFriendsName.now() === Some("Alice"))

  //     alice.name.now() = "Alicia"
  //     assert(bobsFriendsName.now() === Some("Alicia"))

  //     assert(alterations === 3)
  //   }

  // }

  // "Reaction" should {
  //   "support a cycle of two reactions, enforcing a relationship between two Vars" in {

  //     implicit val shelf = ShelfDefault()

  //     shelf.transact(implicit txn => {
  //       val x = Box(2d)
  //       val doubleX = Box(0d)

  //       txn.createReaction(implicit txn => doubleX() = x() * 2d)
  //       assert(x() === 2d)
  //       assert(doubleX() === 4d)

  //       txn.createReaction(implicit txn => x() = doubleX()/2d)
  //       assert(x() === 2d)
  //       assert(doubleX() === 4d)

  //       x() = 4
  //       assert(x() === 4d)
  //       assert(doubleX() === 8d)

  //       doubleX() = 10
  //       assert(x() === 5d)
  //       assert(doubleX() === 10d)

  //     })
  //   }

  // }

  // "Box" should {

  //   "support multiple reactions targetting the same Box, where they do not conflict" in {
  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {
  //       val x = Box(2d)
  //       val y = Box(0d)

  //       txn.createReaction(implicit txn => y() = x() * 2)
  //       assert(x() === 2d)
  //       assert(y() === 4d)

  //       txn.createReaction(implicit txn => y() = x() * 2)
  //       assert(x() === 2d)
  //       assert(y() === 4d)
  //     })
  //   }

  //   "throw FailedReactionsException if reactions conflict within a cycle" in {
  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {
  //       val x = Box(2d)
  //       val y = Box(0d)

  //       txn.createReaction(implicit txn => y() = x() * 2)

  //       intercept[FailedReactionsException] {
  //         txn.createReaction(implicit txn => y() = x() * 4)
  //       }
  //     })
  //   }

  //   "throw FailedReactionsException if reactions conflict between cycles" in {
  //     //Based on suggestion by MisterD
  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {

  //       val a = Box(0)
  //       val b = Box(0)
  //       val c = Box(0)

  //       //These reactions are consistent only when a() == b(). This is initially true, and so
  //       //reactions are accepted
  //       val r1 = txn.createReaction(implicit txn => {
  //         c() = a() + 1
  //       })

  //       val r2 = txn.createReaction(implicit txn => {
  //         c() = b() + 1
  //       })

  //       //This change will expose the fact that the reactions are inconsistent, by making them
  //       //conflict
  //       intercept[FailedReactionsException] {
  //         a() = 32
  //       }
  //     })
  //   }

  //   "throw FailedReactionsException if reactions conflict between transactions" in {
  //     //Based on suggestion by MisterD
  //     implicit val shelf = ShelfDefault()
  //     val a = shelf.transact(implicit txn => {

  //       val a = Box(0)
  //       val b = Box(0)
  //       val c = Box(0)

  //       //These reactions are consistent only when a() == b(). This is initially true, and so
  //       //reactions are accepted
  //       val r1 = txn.createReaction(implicit txn => {
  //         c() = a() + 1
  //       })

  //       val r2 = txn.createReaction(implicit txn => {
  //         c() = b() + 1
  //       })

  //       a
  //     })

  //     shelf.transact(implicit txn => {
  //       //This change will expose the fact that the reactions are inconsistent, by making them
  //       //conflict
  //       intercept[FailedReactionsException] {
  //         a() = 32
  //       }
  //     })

  //   }

  //   "throw FailedReactionsException for infinite incrementing reaction" in {
  //     //Based on suggestion by MisterD
  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {
  //       val c = Box(0)

  //       //This reaction would cycle endlessly, but should throw exception after 10000 applications of the same reaction in one cycle
  //       intercept[FailedReactionsException] {
  //         txn.createReaction(implicit txn => c() = c() + 1)
  //       }
  //     })
  //   }
  // }

  // "Path" should {
  //   "work for Person" in {
  //     implicit val shelf = ShelfDefault()
  //     val (alice, bob, cate, bobsFriendsName) = shelf.transact(implicit txn => {

  //       val cate = Person.default
  //       cate.name() = "Cate"
  //       val alice = Person.default
  //       alice.name() = "Alice"
  //       val bob = Person.default
  //       bob.name() = "Bob"
  //       bob.friend() = Some(cate)

  //       val bobsFriendsName = Path { implicit txn: Txn => bob.friend().map(_.name) }

  //       assert(bobsFriendsName() === Some("Cate"))


  //       bob.friend() = Some(alice)

  //       assert(bobsFriendsName() === Some("Alice"))

  //       (alice, bob, cate, bobsFriendsName)
  //     })

  //     //Should see no changes to bobsFriendsName when something not
  //     //in the path changes, even if it is deeply referenced, or used to be part of path, etc.
  //     //Note we execute view immediately in current thread to allow for test
  //     var changed = false
  //     shelf.now.view(implicit txn => {
  //       bobsFriendsName()
  //       changed = true
  //     }, ImmediateExecutor, false)

  //     //Setting up the view leads to it being called
  //     assert(changed === true)

  //     //Now we reset, so we can see if we get a new change
  //     changed = false

  //     cate.name.now() = "Katey"

  //     //We shouldn't have a change to bobsFriendsName, from changing cate's name
  //     assert(changed === false)

  //     alice.name.now() = "Alicia"

  //     //NOW we should have a change
  //     assert(changed === true)

  //     assert(bobsFriendsName.now() === Some("Alicia"))

  //     //Now we reset, so we can see if we get a new change
  //     changed = false

  //     //Make a change back through to Alice's name by setting bobsFriendsName
  //     bobsFriendsName.now() = Some("Alucard")

  //     //We should have a change
  //     assert(changed === true)

  //     //And the name should have changed both via the path and for Alice herself
  //     assert(bobsFriendsName.now() === Some("Alucard"))
  //     assert(alice.name.now() === "Alucard")

  //     //Setting bob's friend's name to None is meaningless, since
  //     //this is only the case when the path is broken (i.e. the path function returns None, which it does
  //     //not) or alice's name is None (which it cannot be). Therefore this is ignored.
  //     bobsFriendsName.now() = None
  //     assert(bobsFriendsName.now() === Some("Alucard"))
  //     assert(alice.name.now() === "Alucard")

  //   }

  //   "support paths via Options" in {

  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {

  //       val cate = Person.default
  //       cate.name() = "Cate"
  //       val alice = Person.default
  //       alice.name() = "Alice"
  //       val bob = Person.default
  //       bob.name() = "Bob"

  //       //Bob may not have a friend - but we can create a path
  //       //through this to make a Var[Option[String]] that will
  //       //contain bob's friend's name when bob has a friend,
  //       //or None otherwise.
  //       val bobsFriendsName = Path { implicit txn: Txn => bob.friend().map(_.name) }

  //       //No friend yet
  //       assert(bobsFriendsName() === None)

  //       //Now we have a friend, and so a name
  //       bob.friend() = Some(alice)
  //       assert(bobsFriendsName() === Some("Alice"))

  //       //We can edit the name of Alice using either her own
  //       //name Var, or the path Var
  //       alice.name() = "Alicia"
  //       assert(alice.name() == "Alicia")
  //       assert(bobsFriendsName() === Some("Alicia"))

  //       bobsFriendsName() = Some("Aliss")
  //       assert(alice.name() == "Aliss")
  //       assert(bobsFriendsName() === Some("Aliss"))

  //       //We can reassign bob's friends, and do it all again
  //       bob.friend() = Some(cate)
  //       assert(bobsFriendsName() === Some("Cate"))

  //       cate.name() = "Kate"
  //       assert(cate.name() == "Kate")
  //       assert(bobsFriendsName() === Some("Kate"))
  //       assert(alice.name() == "Aliss")

  //       bobsFriendsName() = Some("Katherine")
  //       assert(cate.name() == "Katherine")
  //       assert(bobsFriendsName() === Some("Katherine"))
  //       assert(alice.name() == "Aliss")

  //       //Setting bobs friends name to None is meaningless, since
  //       //this is only the case when the path is broken (which it is
  //       //not) or cate's name is None (which it cannot be). Therefore
  //       //this is ignored.
  //       bobsFriendsName() = None
  //       assert(cate.name() == "Katherine")
  //       assert(bobsFriendsName() === Some("Katherine"))
  //     })
  //   }

  //   "support paths to (and via) Options" in {
  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {
  //       val cate = Person.default
  //       cate.name() = "Cate"
  //       val alice = Person.default
  //       alice.name() = "Alice"
  //       val bob = Person.default
  //       bob.name() = "Bob"

  //       bob.friend() = Some(alice)

  //       //We can also have a path that goes TO
  //       //a Ref[Option[Something]]. In this case
  //       //it makes no difference whether or not the
  //       //path also goes via an optional element -
  //       //the resulting Path Var will contain None
  //       //whenever the Path fails OR the path succeeds
  //       //but leads to an end Ref that contains None.
  //       val bobsFriendsFriend = Path { implicit txn: Txn => bob.friend().map(_.friend) }

  //       //Bob has a friend, alice, but she has no friend, so
  //       //there is no "Bob's friend's friend"
  //       assert(bobsFriendsFriend() === None)

  //       //Now we give alice a friend, and complete the path
  //       alice.friend() = Some(cate)
  //       assert(bobsFriendsFriend() === Some(cate))

  //       //But if we break the path early, then we go back to None
  //       bob.friend() = None
  //       assert(bobsFriendsFriend() === None)
  //     })
  //   }
  // }

  // "View" should {

  // }


  // "ListIndices" should {

  //   "work without selecting all by default" in {
  //     implicit val shelf = ShelfDefault()
  //     shelf.transact(implicit txn => {
  //       val l = Box(List("a", "b", "c", "d", "e", "f", "g", "h"))
  //       val i = ListIndices(l, false)

  //       assert(i.indices() === Set())
  //       assert(i.selected() === Set())

  //       //We can make a selection
  //       i.indices() = Set(4)
  //       assert(i.indices() === Set(4))
  //       assert(i.selected() === Set("e"))

  //       //Can't select past end of list - just selects default (nothing)
  //       i.indices() = Set(10)
  //       assert(i.indices() === Set())
  //       assert(i.selected() === Set())

  //       //Messing with the list shouldn't change selection
  //       i.indices() = Set(4)
  //       l() = "A" :: l().tail
  //       assert(i.indices() === Set(4))
  //       assert(i.selected() === Set("e"))

  //       //Removing elements should preserve the selection
  //       l() = l().tail.tail
  //       assert(i.indices() === Set(2))
  //       assert(i.selected() === Set("e"))

  //       //Adding elements should preserve the selection
  //       l() = "X" :: "Y" :: "Z" :: l()
  //       assert(i.indices() === Set(5))
  //       assert(i.selected() === Set("e"))

  //       //Removing the selected element should move selection to element at same index in new list
  //       l() = List("X", "Y", "Z", "c", "d", "f", "g", "h")
  //       assert(i.indices() === Set(5))
  //       assert(i.selected() === Set("f"))

  //       //Shortening list so that index is not in it should move selection to end of list instead
  //       l() = List("X", "Y", "Z")
  //       assert(i.indices() === Set(2))
  //       assert(i.selected() === Set("Z"))

  //       //Using indices inside and outside the list will retain only those inside it
  //       i.indices() = Set(1, 2, 3, 4, 5)
  //       assert(i.indices() === Set(1, 2))
  //       assert(i.selected() === Set("Y", "Z"))

  //     })
  //   }
  // }
  
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