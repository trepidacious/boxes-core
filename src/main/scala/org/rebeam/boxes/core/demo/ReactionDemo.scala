package org.rebeam.boxes.core.demo

import org.rebeam.boxes.core.ShelfDefault

object ReactionDemo {

  def main(args: Array[String]): Unit = {
    val s = ShelfDefault()

    println(">Adding a")
    val a = s.create("a")
    println(">Adding b")
    val b = s.create("b")
    println(">Adding c")
    val c = s.create("")
    
    println(">Adding reaction")
    val r = s.react{ 
      implicit txn => {
        print("Changes to '")
        txn.changedSources.foreach(box => print(box() + " "))
        println("'.")
        c() = a() + ", " + b()
      }
    }

    println(">Printing c")
    s.transact{
      implicit txn => {
        println("c = '" + c() + "'")
      }
    }
    
    println(">Setting a = 'a2' and printing c")
    s.transact{
      implicit txn => {
        a() = "a2"
        println("c = '" + c() + "'")
      }
    }

    println(">Printing c")
    s.transact{
      implicit txn => {
        println("c = '" + c() + "'")
      }
    }

  }
}