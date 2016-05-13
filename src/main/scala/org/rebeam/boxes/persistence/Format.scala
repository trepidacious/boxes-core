package org.rebeam.boxes.persistence

/**
 * Provides reading, writing, replacing and modifying of a type T
 */
trait Format[T] extends Reads[T] with Writes[T] with Replaces[T] with Modifies[T]
