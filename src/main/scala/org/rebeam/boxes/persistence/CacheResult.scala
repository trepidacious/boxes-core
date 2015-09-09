package org.rebeam.boxes.persistence

/**
 * Result of checking for an object in the cache
 */
sealed trait CacheResult {
  def isCached: Boolean
}

/**
 * Object is already cached, can refer to it using the provided id
 * @param id The id under which the object was already cached
 */
case class Cached(id: Long) extends CacheResult {
  def isCached = true
}

/**
 * Object was not already cached, it is now cached using the provided id
 * @param id The id under which the object has now been cached
 */
case class New(id:Long) extends CacheResult {
    def isCached = false
}