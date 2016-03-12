package org.rebeam.boxes.core.util

import java.util.concurrent.{TimeUnit, ThreadFactory, Executors}

trait CoalescingMultiResponse {
  def response(): Unit
}

class CoalescingMultiResponder(fusionInterval: Long = 5, tickInterval: Long = 20) {

  private class Details(var lastRequestTime: Long = 0L, var requestPending: Boolean = false)

  private val map = scala.collection.mutable.WeakHashMap.empty[CoalescingMultiResponse, Details]

  private val executor = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory())
  private val lock = new Object()

  {
    executor.scheduleAtFixedRate(
      new Runnable(){
        override def run = respond
      },
      tickInterval, tickInterval,
      TimeUnit.MILLISECONDS
    )
  }

  private def respond() = {
    lock.synchronized {
      map.foreach{case(r, d) => {
        if (d.requestPending) {
          r.response
          d.requestPending = false
        }        
      }}
    }
  }

  def request(response: CoalescingMultiResponse) = {
    lock.synchronized {      
      val d = map.getOrElseUpdate(response, new Details())
      
      //Work out timing
      val time = System.currentTimeMillis();
      val interval = time - d.lastRequestTime;
      d.lastRequestTime = time;

      //Now have a pending request
      d.requestPending = true;

      //If we can't coalesce with the last request, then respond immediately
      //Otherwise, the regular responder will catch it and respond sometime soon
      if (interval > fusionInterval) {
        respond
      }
    }
  }
}
