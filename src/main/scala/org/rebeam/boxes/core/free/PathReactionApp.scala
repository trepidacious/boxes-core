package org.rebeam.boxes.core.free

import org.rebeam.boxes.core.free.BoxUtils._
import org.rebeam.boxes.core.free.BoxTypes._
import org.rebeam.boxes.core.free.reaction.{PathToOption, PathViaOption, Path}

import scalaz._
import Scalaz._

object PathReactionApp extends App {

  class Person(val name: Box[String], val friend: Box[Option[Person]])
  object Person {
    def apply() = for {
      name <- create("Unnamed")
      friend <- create[Option[Person]](None)
    } yield new Person(name, friend)
  }

  val a = atomic(Person())
  val b = atomic(Person())
  val c = atomic(Person())

  atomic(a.friend() = Some(b))

  val aFriend = atomic(PathToOption.apply[Person](just(Some(a.friend))))

  println(atomic( for {
    f <- aFriend()
    name <- f traverseU (_.name())   //Use traverse to get from Option[Person] and Person => BoxScript[String] to BoxScript[Option[String]]
  } yield name
  ))
//
//  //    val aFriendAndReaction = Path.boxAndReaction(implicit txn => a.friend)
//  //    val aFriend = aFriendAndReaction._1
//  //    val reaction = aFriendAndReaction._2
//
//  println(">Viewing a's friend's name")
//  val v1 = s.now.view(implicit txn => {
//    println(a.name() + "'s friend is " + a.friend().map(_.name()))
//  })
//
//  println(">Viewing aFriend's name")
//  val v2 = s.now.view(implicit txn => {
//    println("aFriend's name is " + aFriend().map(_.name()))
//  })
//
//  println("Performing GC")
//  1 to 10 foreach {
//    _ => System.gc()
//  }
//
//  //Views are dispatched on another thread, and so we need to give them a while to run
//  //in order to see an update for each transaction, otherwise some may be skipped.
//  Thread.sleep(100)
//
//  println(">Naming a, b and c")
//  s.transact(implicit txn => {
//    a.name() = "Alice"
//    b.name() = "Bob"
//    c.name() = "Charlie"
//  })
//
//  Thread.sleep(100)
//
//  println(">a.friend() = Some(b)")
//  s.transact(implicit txn => {
//    a.friend() = Some(b)
//  })
//
//  Thread.sleep(100)
//
//  println(">aFriend() = Some(c)")
//  s.transact(implicit txn => {
//    aFriend() = Some(c)
//  })
//
//  Thread.sleep(100)
//
//  println(">aFriend() = Some(a)")
//  s.transact(implicit txn => {
//    aFriend() = Some(a)
//  })

}
