package org.rebeam.boxes.core

import BoxUtils._
import BoxTypes._

import java.util.concurrent.{ExecutorService, Executors}

trait Observer {
  def observe(r: Revision): Unit
}

object Observer {
  def apply(o: Revision => Unit): Observer = new Observer {
    def observe(r: Revision) = o(r)
  }
}

// object ScriptObserver {
//   val defaultPoolSize = 4
//   val defaultExecutor = Executors.newFixedThreadPool(defaultPoolSize)
// }

// class ScriptObserver[A](script: BoxObserverScript[A], effect: A => Unit, scriptExecutor: ExecutorService = ScriptObserver.defaultExecutor, effectExecutor: ExecutorService = ScriptObserver.defaultExecutor) extends Observer {
//   def observe(r: Revision): Unit = {
//     scriptExecutor.execute(new Runnable() {
//       def run(): Unit = BoxObserverScript.run(script, r, Set.empty)
//     })
//   }  
// }