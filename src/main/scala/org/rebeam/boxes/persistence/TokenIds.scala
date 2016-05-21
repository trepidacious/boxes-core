package org.rebeam.boxes.persistence

trait TokenIds {
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
  
  /**
   * See if there is an id for a thing already
   */
  def idFor(thing: Any): Option[Long]
}

/**
 * Basic implementation of TokenIds using a mutable map
 * to store mapping from things to ids.
 */ 
class TokenIdsDefault extends TokenIds{

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
 * TokenIds implementation that will use an underlying TokenIds to
 * provide all actual ids. So this TokenIds will only contain the
 * things that have been cached with this actual TokenIds, but when
 * a new thing is cached, it will also be cached in the underlying
 * TokenIds, and the id from the underlying TokenIds will be used.
 * This can be used to provide persistent ids that survive across
 * multiple writer scripts etc.
 */
class TokenIdsOverlay(t: TokenIds) extends TokenIds {
  
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
class TokenIdsWeak extends TokenIds{

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
