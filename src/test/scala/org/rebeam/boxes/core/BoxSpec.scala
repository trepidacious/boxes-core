package org.rebeam.boxes.core

import org.rebeam.boxes.core._
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

    "throw FailedReactionsException for non-converging cyclic reactions (y = x + 1, x = y + 1 as an example)" in {
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

    "support eventually-converging cyclic reactions that use less than Reactor.maxCycle executions per reaction" in {
      eventualConverging(1)
    }

    "throw FailedReactionsException for eventually-converging cyclic reactions that exceed Reactor.maxCycle executions per reaction" in {
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

    "throw FailedReactionsException if reactions conflict within a cycle" in {
      intercept[FailedReactionsException] {
        atomic {
          for {
            x <- create(2d)
            y <- create(0d)

            r1 <- createReaction{
              for {
                x <- x()
                _ <- y() = x * 2
              } yield ()
            }

            r2 <- createReaction{
              for {
                x <- x()
                _ <- y() = x * 4
              } yield ()
            }
          } yield ()
        }
      }
    }

    "throw FailedReactionsException if reactions conflict between cycles" in {
      //Based on suggestion by MisterD
      intercept[FailedReactionsException] {
        atomic {
          for {
            a <- create(0)
            b <- create(0)
            c <- create(0)

            //These reactions are consistent only when a() == b(). This is initially true, and so
            //reactions are accepted
            r1 <- createReaction{
              for {
                a <- a()
                _ <- c() = a + 1
              } yield ()
            }

            r2 <- createReaction{
              for {
                b <- b()
                _ <- c() = b + 1
              } yield ()
            }

            //This change will expose the fact that the reactions are inconsistent, by making them
            //conflict
            _ <- a() = 32
          } yield ()
        }
      }
    }

    "throw FailedReactionsException if reactions conflict between transactions" in {

      //Based on suggestion by MisterD
      val (a, b, c, r1, r2) = atomic {
        for {
          a <- create(0)
          b <- create(0)
          c <- create(0)

          //These reactions are consistent only when a() == b(). This is initially true, and so
          //reactions are accepted
          r1 <- createReaction{
            for {
              a <- a()
              _ <- c() = a + 1
            } yield ()
          }

          r2 <- createReaction{
            for {
              b <- b()
              _ <- c() = b + 1
            } yield ()
          }
        } yield (a, b, c, r1, r2)
      }

      intercept[FailedReactionsException] {
        atomic {
          for {
            //This change will expose the fact that the reactions are inconsistent, by making them
            //conflict
            _ <- a() = 32
          } yield ()
        }
      }
    }

    "throw FailedReactionsException for infinite incrementing reaction" in {
      //Based on suggestion by MisterD
      intercept[FailedReactionsException] {
        atomic {
          for {
            c <- create(0)

            //This reaction would cycle endlessly, but should throw exception after Reactor.maxCycle applications of the same reaction in one cycle
            r <- createReaction {
              for {
                cv <- c()
                _ <- c() = cv + 1
              } yield ()
            }
          } yield ()
        }
      }
    }
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

}