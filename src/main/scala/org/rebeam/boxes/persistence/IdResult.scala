package org.rebeam.boxes.persistence

/**
 * Result of assigning an id to a thing
 */
sealed trait IdResult {
  def existing: Boolean
  def id: Long
}

/**
 * Object already had an id
 * @param id The pre-existing id for the object
 */
case class ExistingId(id: Long) extends IdResult {
  def existing = true
}

/**
 * Object had no pre-existing id, it is now assigned the included id
 * @param id The id newly assigned to the object
 */
case class NewId(id:Long) extends IdResult {
    def existing = false
}
