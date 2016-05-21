package org.rebeam.boxes.persistence

/**
 * Associates Long ids with arbitrary things. Should be assumed to assign
 * the same id to all equal things.
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
   * Try to cache a thing. The result will tell us whether the thing
   * is already cached:
   *
   *   If already cached, the CacheResult is Cached(ref), where the
   *   supplied ref can be written out in place of the object. This
   *   refers back to the previous instance with the matching id.
   *
   *   If NOT already cached, the CacheResult is New(id), where the
   *   id should be written out with the object, so that it can be
   *   referenced by future refs.
   */
  def cache(thing: Any): CacheResult
}

/**
 * Basic implementation of IdsWriter using a mutable map
 * to store mapping from things to ids.
 */ 
class IdsWriterDefault extends IdsWriter {

 private val c = collection.mutable.Map[Any, Long]()
 private var nextId = 42
 
 override def cache(thing:Any): CacheResult = {
   c.get(thing) match {
     case None =>
       val id = nextId
       nextId = nextId + 1
       c.put(thing, id)
       New(id)
 
     case Some(ref) => Cached(ref)
   }
 } 
 
 override def idFor(thing: Any): Option[Long] = c.get(thing)

 def contents:collection.immutable.Map[Any, Long] = c.toMap
 
}

/**
 * IdsWriter implementation that will use an underlying IdsWriter to
 * provide all actual ids. So this IdsWriter will only contain the
 * things that have been cached with this actual IdsWriter, but when
 * a new thing is cached, it will also be cached in the underlying
 * IdsWriter, and the id from the underlying IdsWriter will be used.
 * This can be used to provide persistent ids that survive across
 * multiple writer scripts etc.
 */
class IdsWriterOverlay(t: IdsWriter) extends IdsWriter {
  
  private val c = collection.mutable.Map[Any, Long]()
  
  private def underlyingId(thing: Any) = t.cache(thing).id
  
  override def cache(thing:Any): CacheResult = {
    c.get(thing) match {
      case None =>
        val id = underlyingId(thing)
        c.put(thing, id)
        New(id)
  
      case Some(ref) => Cached(ref)
    }
  } 
  
  override def idFor(thing: Any): Option[Long] = c.get(thing)

}

/**
 * Use a WeakHashMap to remember ids
 */
class IdsWriterWeak extends IdsWriter {

  private val c = collection.mutable.WeakHashMap[Any, Long]()
  private var nextId = 42

  override def cache(thing:Any): CacheResult = {
    c.get(thing) match {
      case None =>
        val id = nextId
        nextId = nextId + 1
        c.put(thing, id)
        New(id)

      case Some(ref) => Cached(ref)
    }
  } 

  override def idFor(thing: Any): Option[Long] = c.get(thing)

}
