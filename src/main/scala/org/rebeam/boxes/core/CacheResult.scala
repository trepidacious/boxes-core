package org.rebeam.boxes.core

/**
 * Result of checking for an object in the cache
 */
sealed trait CacheResult

/**
 * Object is already cached, can refer to it using the provided id
 * @param id The id under which the object was already cached
 */
case class Cached(id: Long) extends CacheResult

/**
 * Object was not already cached, it is now cached using the provided id
 * @param id The id under which the object has now been cached
 */
case class New(id:Int) extends CacheResult