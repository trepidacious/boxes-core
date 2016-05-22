package org.rebeam.boxes.persistence

import java.util.Random

/**
 * Gives access to an association of Long ids with arbitrary things. 
 * Should be assumed to assign the same id to all equal things.
 * 
 * Often ids are those that have been added via Ids trait, but may
 * be pre-existing ids that are known even before they are assigned.
 *
 * Currently may be mutable.
 * TODO make guaranteed immutable
 */
trait Ids {
  /**
   * Find the id for a thing
   * @param thing The thing to look up
   * @return The id of the thing
   */
  def idFor(thing: Any): Long
}

trait IdsWithContents extends Ids {
  def contents:collection.immutable.Map[Any, Long]
}

private class IdsMutable(private val c: collection.mutable.Map[Any, Long], private val firstId: Long) extends IdsWithContents {

 private var nextId = firstId
 
 override def idFor(thing: Any): Long = c.get(thing) match {
  case None =>
    val id = nextId
    nextId = nextId + 1
    c.put(thing, id)
    id

  case Some(id) => id
 }

 override def contents:collection.immutable.Map[Any, Long] = c.toMap  
 
}

/**
 * Basic implementation of Ids using a mutable map
 * to store mapping from things to ids.
 */ 
object IdsDefault{
  def apply(firstId: Long = 42): IdsWithContents = new IdsMutable(collection.mutable.Map[Any, Long](), firstId)
}

/**
 * Use a WeakHashMap from things to their ids, to avoid retaining those things
 * when they could otherwise be garbage collected.
 */
 object IdsWeak{
   def apply(firstId: Long = 42): IdsWithContents = new IdsMutable(collection.mutable.WeakHashMap[Any, Long](), firstId)
 }
