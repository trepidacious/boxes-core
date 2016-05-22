package org.rebeam.boxes.persistence

/**
 * Gives access to an association of Long ids with arbitrary things. 
 * Should be assumed to assign the same id to all equal things.
 * 
 * Often ids are those that have been added via IdsWriter trait, but may
 * be pre-existing ids that are known even before they are assigned.
 *
 * Currently may be mutable.
 * TODO make guaranteed immutable
 */
trait Ids {
  /**
   * Find the id for a thing, if any
   * @param thing The id to look up
   * @return Some(id) if the thing has an id, None otherwise
   */
  def idFor(thing: Any): Option[Long]  
}

/**
 * Ids with support for adding new things, for use alongside a TokenWriter
 * in BoxWriterScripts.
 * TODO replace with an immutable structure
 */
trait IdsWriter extends Ids {
  /**
   * Assign an id to a thing. If there was already an id assigned via this
   * IdsWriter, the result is ExistingId(id), otherwise it is NewId(id).
   * Note that things may already have a known id before they are assigned via
   * this IdsWriter, and these things will return Some(id) from the idsFor 
   * method. This is the id that will be returned via assignId, and on the first
   * call to assignId, a New(id) will still be returned.
   * This allows an IdsWriter to provide access to and work with pre-existing
   * persistent ids, while still allowing the user of the IdsWriter to track
   * whether a particular thing has been encountered before during writing.
   */
  def assignId(thing: Any): IdResult
}

/**
 * Basic implementation of IdsWriter using a mutable map
 * to store mapping from things to ids.
 */ 
class IdsWriterDefault extends IdsWriter {

 private val c = collection.mutable.Map[Any, Long]()
 private var nextId = 42
 
 override def assignId(thing:Any): IdResult = {
   c.get(thing) match {
     case None =>
       val id = nextId
       nextId = nextId + 1
       c.put(thing, id)
       NewId(id)
 
     case Some(ref) => ExistingId(ref)
   }
 } 
 
 override def idFor(thing: Any): Option[Long] = c.get(thing)

 def contents:collection.immutable.Map[Any, Long] = c.toMap
 
}

/**
 * IdsWriter implementation that will use an underlying IdsWriter to
 * provide all actual id values. 
 * When assigning an id to a thing that has not been assigned a previous
 * id in THIS IdsWriter, NewId(id) will still be returned, but the id
 * assigned will always be from the underlying IdsWriter - it may be a
 * new id or an existing one in that underlying IdsWriter. As you would
 * expect, requesting an id for the same thing  later will still return
 * ExistingId(id) with the same id.
 *
 * This can be used to provide persistent ids that survive across
 * multiple writer scripts etc.
 */
class IdsWriterOverlay(underlying: IdsWriter) extends IdsWriter {
  
  private val c = collection.mutable.Map[Any, Long]()
  
  private def underlyingId(thing: Any) = underlying.assignId(thing).id
  
  override def assignId(thing:Any): IdResult = {
    c.get(thing) match {
      case None =>
        val id = underlyingId(thing)
        c.put(thing, id)
        NewId(id)
  
      case Some(ref) => ExistingId(ref)
    }
  } 
  
  //Note we get the id from the underlying IdsWriter, so that we will return
  //preexisting ids even before they are assigned by the overlay.
  override def idFor(thing: Any): Option[Long] = underlying.idFor(thing)

}

/**
 * Use a WeakHashMap from things to their ids, to avoid retaining those things
 * when they could otherwise be garbage collected.
 */
class IdsWriterWeak extends IdsWriter {

  private val c = collection.mutable.WeakHashMap[Any, Long]()
  private var nextId = 42

  override def assignId(thing:Any): IdResult = {
    c.get(thing) match {
      case None =>
        val id = nextId
        nextId = nextId + 1
        c.put(thing, id)
        NewId(id)

      case Some(ref) => ExistingId(ref)
    }
  } 

  override def idFor(thing: Any): Option[Long] = c.get(thing)

}
