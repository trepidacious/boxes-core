package org.rebeam.boxes.core.util

import org.rebeam.boxes.core.Identifiable

import scala.collection._
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.Reference

class GCWatcher {
  private val refQueue = new ReferenceQueue[Identifiable]()
  private val refToId = new mutable.HashMap[Reference[_ <: Identifiable], Long]()
     
  def watch(boxes: Set[_ <: Identifiable]) {
    //Any new boxes need to be tracked for GC - make a weak reference to the box, and use that to map to the id of the box
    boxes.foreach{b => {
        val r = new WeakReference(b, refQueue)
        refToId.put(r, b.id)
      }
    }
  }
  
  def deletes() = {
    val gcedIds = new mutable.ListBuffer[Long]()
    var gced = refQueue.poll()
    while (gced != null) {
      val id = refToId.remove(gced)
      id.foreach(gcedIds += _)
      gced = refQueue.poll()
    }
    gcedIds.toList
  }

}