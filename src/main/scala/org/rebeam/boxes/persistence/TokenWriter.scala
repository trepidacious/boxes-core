package org.rebeam.boxes.persistence

/**
 * Writes tokens using side effects.
 * TODO replace with something like a Process from fs2?
 */
trait TokenWriter {
 def write(t: Token): Unit
 def close(): Unit
}