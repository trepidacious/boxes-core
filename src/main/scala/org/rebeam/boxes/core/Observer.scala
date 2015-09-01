package org.rebeam.boxes.core

trait Observer {
  def observe(r: Revision): Unit
}
