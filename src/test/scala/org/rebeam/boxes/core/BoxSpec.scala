package org.rebeam.boxes.core

import org.rebeam.boxes.core._
import org.rebeam.boxes.persistence.formats._
import org.rebeam.boxes.persistence.json.JsonPrettyIO
import org.scalacheck.Arbitrary
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import scalaz._
import Scalaz._

class BoxSpec extends WordSpec with PropertyChecks with ShouldMatchers {

  "Revision" should {
    "skip writes with the same value, but not those with different values" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") }

      val r2 = atomicToRevision { b() = "b" }

      assert(r1.indexOf(b).isDefined)
      r1.indexOf(b) shouldBe r2.indexOf(b)

      val r3 = atomicToRevision { b() = "c" }

      val a: Option[Boolean] = for {
        r3b <- r3.indexOf(b)
        r2b <- r2.indexOf(b)
      } yield (r3b > r2b)

      a shouldBe Some(true)
    }
  }

  "Shelf" should {
    "keep the same revision for read-only atomics" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") }
      val (r2, b2) = atomicToRevisionAndResult { b() }

      b2 shouldBe "b"
      r1 shouldBe r2
      r1.index shouldBe r2.index
    }

    "keep the same revision for atomics that only write the same existing values" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") }

      val r2 = atomicToRevision { b()  = "b"}

      r1 shouldBe r2
      r1.index shouldBe r2.index
    }

    "increment the revision for atomics that write new values" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") }

      val r2 = atomicToRevision { b()  = "c"}

      assert(r2 != r1)
      assert(r2.index > r1.index)
    }

    "notify observers of the revision where they are added, even when that revision is read-only" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") } 

      var revisionSeen: Option[Revision] = None
      val observer = Observer((r: Revision) => revisionSeen = Some(r))

      //Just adding an observer is a read-only atomic
      val revisionWhereAdded = atomicToRevision { observe(observer) }
      revisionSeen shouldBe Some(revisionWhereAdded)

      //Just for luck - the revision where we added should be the same as the revision where we created Box b, 
      //since we didn't create a new revision by adding an observer
      revisionWhereAdded shouldBe r1
    }

    "notify observers of only new revisions, with no notification for read-only atomics" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") } 

      var revisionSeen: Option[Revision] = None
      val observer = Observer((r: Revision) => revisionSeen = Some(r))

      //Just adding an observer is a read-only atomic - we still get a notification
      val revisionWhereAdded = atomicToRevision { observe(observer) }
      revisionSeen shouldBe Some(revisionWhereAdded)

      //Just for luck - the revision where we added should be the same as the revision where we created Box b, 
      //since we didn't create a new revision by adding an observer
      revisionWhereAdded shouldBe r1

      //Now reset the revisionSeen and make some read-only atomics - observer should not be called
      revisionSeen = None

      val (r2, b2) = atomicToRevisionAndResult { b() }
      b2 shouldBe "b"
      revisionSeen shouldBe None
      r2 shouldBe r1

      //Writing same value is a read-only atomic
      val r3 = atomicToRevision { b() = "b"}
      revisionSeen shouldBe None
      r3 shouldBe r1

      //Finally make an actual write, and expect to see the new revision
      val r4 = atomicToRevision { b() = "c"}
      revisionSeen shouldBe Some(r4)
      assert (r4 != r1)
    }

    "notify observers of new revision when they are registered in a revision that makes changes" in {
      val (r1, b) = atomicToRevisionAndResult { create("b") } 

      var revisionSeen: Option[Revision] = None
      var bSeen: Option[String] = None

      val observer = Observer((r: Revision) => {
        revisionSeen = Some(r)
        bSeen = Some(b(r))
      })

      val revisionWhereAdded = atomicToRevision { observe(observer) andThen (b() = "c") }

      revisionSeen shouldBe Some(revisionWhereAdded)
      bSeen shouldBe Some("c")

    }

  }

  "Reaction" should {

    "cache simple scripts (note that if result does not need to be cached, a plain BoxR is better)" in {
      atomic {
        for {
          a <- create(2)
          b <- cache(a().map(_ + 3))

          test1 <- b().map(_ == 5)

          c <- cache((a() |@| b()){_ + _})

          test2 <- c().map(_ == 7)

          _ <- a() = 4

          test3 <- b().map(_ == 7)
          test4 <- c().map(_ == 11)
          } yield assert(test1 && test2 && test3 && test4)
      }

    }

    "only run when sources have actually changed" in {
      val b = atomic { create("b") }
      val reactionExecuted = atomic { create(false) }

      val reaction = atomic {
        createReaction(
          for {
            b <- b()
            _ <- reactionExecuted() = true
          } yield ()
        )
      }

      //Reaction runs when created, so it has executed
      atomic { reactionExecuted() } shouldBe true

      //Clear execution and check this works
      atomic { (reactionExecuted() = false) andThen reactionExecuted() } shouldBe false

      //Now write same value to Box b - since the value has not changed, the reaction is not run
      atomic { b() = "b" }
      atomic { reactionExecuted() } shouldBe false

      //If we change the value in b, the reaction is run
      atomic { b() = "c" }
      atomic { reactionExecuted() } shouldBe true
    }

    "not run when another reaction makes a pointless write to a source Box (y = x^2 as an example)" in {
      val x = atomic { create(1) }
      val y = atomic { create(0) }
      val squared = atomic {
        createReaction(
          for {
            x <- x()
            _ <- y() = x * x
          } yield ()
        )
      }

      atomic { y() } shouldBe 1

      //Make a reaction that reads y, and a boolean box we can use to see if it is executed
      val reactionExecuted = atomic { create(false) }
      val reaction = atomic {
        createReaction(
          for {
            y <- y()
            _ <- reactionExecuted() = true
          } yield ()
        )
      }

      //Creating the reaction will run it
      atomic { reactionExecuted() } shouldBe true

      //Now clear the flag so we can see if reaction is executed again
      atomic { reactionExecuted() = false}
      atomic { reactionExecuted() } shouldBe false

      //Setting x to -1 will lead squared reaction to write to y, but since -1 * -1 is still 1, this should not trigger our other reaction
      atomic { x() = -1 }
      atomic { y() } shouldBe 1
      atomic { reactionExecuted() } shouldBe false

      //Setting x to 2 will lead squared reaction to write to y and actually change it, triggering our other reaction
      atomic { x() = 2 }
      atomic { y() } shouldBe 4
      atomic { reactionExecuted() } shouldBe true
    }

    "not change a target Box when it would write the same value to it (y = x^2 as an example)" in {
      val x = atomic { create(1) }
      val y = atomic { create(0) }
      val squared = atomic {
        createReaction(
          for {
            x <- x()
            _ <- y() = x * x
          } yield ()
        )
      }

      atomic { y() } shouldBe 1
      val yInitialRevision = Shelf.currentRevision
      val yInitialIndex = yInitialRevision.indexOf(y)

      //Setting x to -1 will lead squared reaction to write to y, but since -1 * -1 is still 1, this should not change y's index
      atomic { x() = -1 }
      atomic { y() } shouldBe 1

      val yFinalRevision = Shelf.currentRevision
      val yFinalIndex = yFinalRevision.indexOf(y)
      assert (yFinalIndex.isDefined)
      yFinalIndex shouldBe yInitialIndex

      //Setting x to 2 will lead squared reaction to write to y and actually change it
      atomic { x() = 2 }
      atomic { y() } shouldBe 4

      //y should have actually changed now
      val yAlteredRevision = Shelf.currentRevision
      val yAlteredIndex = yAlteredRevision.indexOf(y)
      assert (yAlteredIndex.isDefined)
      assert (yAlteredIndex != yInitialIndex)

    }

    "support bidirectional reactions (although BoxM is a better approach) (y = x + 1 as an example)" in {
      val x = atomic { create(1) }
      val y = atomic { create(1) }

      //Create a pair of reactions, one for each direction
      val (xToY, yToX) = atomic {
        for {
          xToY <- createReaction(
            for {
              x <- x()
              _ <- y() = x + 1
            } yield ()
          )
          yToX <- createReaction(
            for {
              y <- y()
              _ <- x() = y - 1
            } yield ()
          )
        } yield (xToY, yToX)
      }

      //xToY was registered first, so will have "won" the race to correct the initial inconsistent state
      atomic { x() } shouldBe 1
      atomic { y() } shouldBe 2

      //Now try setting and checking values
      atomic { x() = 2 }
      atomic { x() } shouldBe 2
      atomic { y() } shouldBe 3

      atomic { y() = 10 }
      atomic { x() } shouldBe 9
      atomic { y() } shouldBe 10
    }

    "reject non-converging cyclic reactions (y = x + 1, x = y + 1 as an example)" in {
      val x = atomic { create(1) }
      val y = atomic { create(1) }

      //Create a pair of reactions, one for each direction, which aren't compatible - they
      //just keep incrementing x and y.
      intercept[FailedReactionsException] {
        atomic {
          for {
            xToY <- createReaction(
              for {
                x <- x()
                _ <- y() = x + 1
              } yield ()
            )
            yToX <- createReaction(
              for {
                y <- y()
                _ <- x() = y + 1
              } yield ()
            )
          } yield ()
        }
      }
    }

    def eventualConverging(initialValues: Int) = {
      val x = atomic { create(initialValues) }
      val y = atomic { create(initialValues) }

      //Create a pair of reactions, one for each direction, which aren't compatible - they
      //just keep incrementing x and y.
      atomic {
        for {
          xToY <- createReaction(
            for {
              x <- x()
              _ <- y() = Math.min(x + 1, Reactor.maxCycle * 2)
            } yield ()
          )
          yToX <- createReaction(
            for {
              y <- y()
              _ <- x() = Math.min(y + 1, Reactor.maxCycle * 2)
            } yield ()
          )
        } yield ()
      }

      atomic { x() } shouldBe Reactor.maxCycle * 2
      atomic { y() } shouldBe Reactor.maxCycle * 2

    }

    "accept eventually-converging cyclic reactions that use less than Reactor.maxCycle executions per reaction" in {
      eventualConverging(1)
    }

    "reject eventually-converging cyclic reactions that exceed Reactor.maxCycle executions per reaction" in {
      intercept[FailedReactionsException] {
        eventualConverging(0)
      }
    }

    "support multiple reactions targetting the same Box, where they do not conflict" in {
      atomic {
        for {
          x <- create(2d)
          y <- create(0d)

          r1 <- createReaction {
            for {
              x <- x()
              _ <- y() = x * 2
            } yield ()
          }

          x1 <- x()
          y1 <- y()

          r2 <- createReaction {
            for {
              x <- x()
              _ <- y() = x * 2
            } yield ()
          }

          x2 <- x()
          y2 <- y()
        } yield {
          assert(x1 == 2)
          assert(x2 == 2)
          assert(y1 == 4)
          assert(y2 == 4)
        }
      }
    }

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

  }

  "BoxM" should {
    "support writable views of transformed data (y = x + 1 as an example)" in {
      val x = atomic { create(1) }

      //Note that if this transformation is not consistent then no failure occurs,
      //but value of x (and so y) will depend on which one was set more recently
      val y = BoxM(
        read = x().map(_ + 1),
        write = (a:Int) => x() = (a - 1)
      )

      atomic { y() } shouldBe 2
      atomic { (x() = 2) andThen y() } shouldBe 3
      atomic { (y() = 10) andThen x() } shouldBe 9
    }
  }

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