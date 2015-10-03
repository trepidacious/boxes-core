package org.rebeam.boxes.core

trait Observer {
  def observe(r: Revision): Unit
}

object Observer {
  def apply(o: Revision => Unit): Observer = new Observer {
    def observe(r: Revision) = o(r)
  }
}