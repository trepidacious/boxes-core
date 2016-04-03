package org.rebeam.boxes.persistence

/**
 * Provides reading and writing of a type T 
 */
trait Format[T] extends Reads[T] with Writes[T]

/**
 * Provides reading and writing of a type T 
 */
trait FormatAndReplaces[T] extends Format[T] with Replaces[T]
