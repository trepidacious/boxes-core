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

  /**
   * Make a minimal Observer. This will directly run the provided effect on a Revision.
   * For each revision the effect will run in the thread used to call observe. This may be the thread
   * that committed the Revision, or another thread, depending on the Shelf implementation. This may
   * result in the effect being run concurrently on different Revisions.
   * For most applications other implementations should be used.
   */
  def apply(o: Revision => Unit): Observer = new Observer {
    def observe(r: Revision) = o(r)
  }

  /**
   * This implementation of Observer is recommended for most uses.
   * It will run a supplied BoxScript on received Revisions, to produce a value
   * that is then passed to an effect closure. This is referred to below as "processing" the revision.
   * When script is run, it is checked at run-time to only perform read operations - it is an error
   * to use any other actions than reading boxes, plus "just" and "nothing".
   * The first Revision received (i.e. the revision at which the observer is added) is always processed,
   * and the Boxes read by the script are recorded. On subsequent revisions, the revision is only processed if at least
   * one of the Boxes read by the script the last time it was run has changed in the subsequent revision. 
   * 
   * An Executor is specified which is used to run the script and then the effect, for each processed revision. This
   * can be used to avoid tying up the thread that delivers the Revision to the Observer - this may be the thread
   * that committed the Revision. By default this uses a pool of daemon threads.
   *
   * This implementation guarantees that the effect will be run non-concurrently (i.e. only one execution of the effect will
   * be running at any point in time), but possibly on different threads for each execution, according to the executor. 
   * In other words, only one revision will be processed at a time.
   * Revisions are processed by submitting tasks to the Executor that will run the script and then the effect. We consider
   * a revision to be pending from the instant it is submitted to the executor, to the time the effect has been completely run.
   * When a revision is received and there is no pending revision, the new revision is immediately submitted to the executor 
   * and becomes pending. When a revision is received and there is already a pending revision, the new revision joins a queue.
   * As each effect completes, the next queued revision is run. As such, revisions can be sent to the executor either immediately
   * when received, or when the previous pending revision completes processing.
   *
   * This means that effects do not normally need to be written to be thread-safe, but should be agnostic about what
   * thread they run on. The scripts are by their nature thread-safe.
   * 
   * You may choose the queueing behaviour used - if onlyMostRecent is true then the queue will have at most one entry, and
   * previous non-pending queued revisions will be discarded when a new revision is received. Otherwise all revisions will
   * be processed and the queue is unbounded - do not use this mode unless it is really needed and you are sure that the
   * queue will not grow too large - note that by queueing revisions we also retain all their data.
   */
  def apply[A](script: BoxScript[A], effect: A => Unit, scriptExe: Executor = Observer.defaultExecutor, onlyMostRecent: Boolean = true): Observer = 
    new ObserverDefault(script, effect, scriptExe, onlyMostRecent)

}

private class ObserverDefault[A](script: BoxScript[A], effect: A => Unit, scriptExe: Executor = Observer.defaultExecutor, onlyMostRecent: Boolean = true) extends Observer {
  private val revisionQueue = new scala.collection.mutable.Queue[Revision]()
  private val lock = Lock()
  private var state: Option[(Long, Set[Long])] = None
  private var pending = false;

  private def relevant(r: Revision): Boolean = {
    state match {
      case None => true
      case Some((index, reads)) => reads.iterator.flatMap(r.indexOfId(_)).exists(_>index)
    }
  }
  
  private def go(): Unit = {
    
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
            effect(a)
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
