package org.rebeam.boxes.core.demo

import org.rebeam.boxes.core.{Box, BoxNow, ShelfDefault}

object NestedTxnDemo {

  def main(args: Array[String]): Unit = {
    implicit val s = ShelfDefault()

    val c = s.transact(implicit txn => {
      val a = Box("a")
      val b = Box("b")
      s.transact(implicit txn => {
        a() + b()
      })
    })

    println(c)
  }
}