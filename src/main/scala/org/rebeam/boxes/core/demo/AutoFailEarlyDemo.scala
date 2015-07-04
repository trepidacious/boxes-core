package org.rebeam.boxes.core.demo

import org.rebeam.boxes.core.{TxnR, Txn, ShelfDefault}

object AutoFailEarlyDemo {

  def thread(f: => Unit) = new Thread(new Runnable{
    def run() = f
  }).start()

  def run(failEarly: Boolean) {
    val s = ShelfDefault()
    val counter = s.now.create(0)
    val slowResult = s.now.create(0)
    
    val auto = s.now.auto{
      implicit t: Txn => {
        val c = counter()
        Range(0, 6).foreach(_ => {
          Thread.sleep(1)
          if (failEarly) t.failEarly
        })
        slowResult() = c * 2
      }
    }
    
    val view = s.now.view{
      implicit t: TxnR => {
        println("View, counter = " + counter() + ", slowResult = " + slowResult())
      }
    }
    
    thread{
      Range(0, 10).foreach{_ => {
        Thread.sleep(2)
        s.transact{ implicit t: Txn => counter() = counter() + 1 }
        Thread.sleep(8)
        s.transact{ implicit t: Txn => counter() = counter() + 1 }
      }}
    }
    
    Thread.sleep(2000)
  }
  
  def main(args: Array[String]): Unit = {
    println("Without early fail:")
    run(false)

    println("With early fail:")
    run(true)
  }
}