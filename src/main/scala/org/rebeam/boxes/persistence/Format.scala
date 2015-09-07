package org.rebeam.boxes.persistence

/**
 * Provides reading and writing of a type T 
 */
trait Format[T] extends Reads[T] with Writes[T]
