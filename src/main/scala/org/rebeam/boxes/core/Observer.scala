package org.rebeam.boxes.core

import util._
import BoxUtils._
import BoxTypes._
import BoxScriptImports._

import java.util.concurrent.{ExecutorService, Executors, Executor}

trait Observer {
  def observe(r: Revision): Unit
}

object Observer {

  val defaultExecutorPoolSize = 8
  val defaultThreadFactory = DaemonThreadFactory()
  lazy val defaultExecutor: Executor = Executors.newFixedThreadPool(defaultExecutorPoolSize, defaultThreadFactory)

  def apply(o: Revision => Unit): Observer = new Observer {
    def observe(r: Revision) = o(r)
  }

  def apply[A](script: BoxScript[A], effect: A => Unit, scriptExe: Executor = Observer.defaultExecutor, effectExe: Option[Executor] = None, onlyMostRecent: Boolean = true): Observer = 
    new ObserverDefault(script, effect, scriptExe, effectExe, onlyMostRecent)

}

private class ObserverDefault[A](script: BoxScript[A], effect: A => Unit, scriptExe: Executor = Observer.defaultExecutor, effectExe: Option[Executor] = None, onlyMostRecent: Boolean = true) extends Observer {
  private val revisionQueue = new scala.collection.mutable.Queue[Revision]()
  private val lock = Lock()
  private var state: Option[(Long, Set[Long])] = None
  private var pending = false;

  private def relevant(r: Revision) = {
    state match {
      case None => true
      case Some((index, reads)) => reads.iterator.flatMap(r.indexOfId(_)).exists(_>index)
    }
  }
  
  private def go() {
    
    //If we have more revisions pending, try to run the next
    if (!revisionQueue.isEmpty) {
      val r = revisionQueue.dequeue
      
      //If this revision is relevant (i.e. it has changes the view will read)
      //then run the transaction on it
      if (relevant(r)) {
        pending = true
        scriptExe.execute(new Runnable() {
          def run = {
            //FIXME if this has an exception, it kills the View (i.e. it won't run on any future revisions).
            val (a, reads) = BoxScriptInterpreter.runReadOnly(script, r)

            //If we have a separate effect executor, use it, otherwise run effect immediately (here in script executor)
            effectExe match {
              case Some(e) => e.execute(new Runnable(){
                def run = effect(a)
              })
              case None => effect(a)
            }

            lock.run{
              state = Some((r.index, reads))
              go()
            }
          }
        })

      //If this revision is NOT relevant, try the next revision
      } else {
        go()
      }
      
    //If we have no more revisions, then stop for now
    } else {
      pending = false
    }
  }
  
  def observe(r: Revision): Unit = {
    lock.run {
      if (onlyMostRecent) revisionQueue.clear
      revisionQueue.enqueue(r)
      if (!pending) {
        go()
      }
    }
  }
}
