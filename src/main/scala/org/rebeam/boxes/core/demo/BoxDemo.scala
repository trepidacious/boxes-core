package org.rebeam.boxes.core.demo

import org.rebeam.boxes.core.{BoxNow, ShelfDefault}

object BoxDemo {

  def main(args: Array[String]): Unit = {
    implicit val s = ShelfDefault()

    val a = BoxNow(1)
    val b = BoxNow(0)
    b.now << {implicit txn => a() * 2}

    println(b.now())
    a.now() = 2
    println(b.now())
    
  }
}