package org.rebeam.boxes.core.free

trait Observer {
  def observe(r: Revision): Unit
}
