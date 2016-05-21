package org.rebeam.boxes.persistence

trait TokenWriter {
 def write(t: Token)

 // private val c = collection.mutable.Map[Any, Long]()
 // private var nextId = 0
 // 
 // /**
 //  * Try to cache a thing. The result will tell us whether the thing
 //  * is already cached:
 //  *
 //  *   If already cached, the CacheResult is Cached(ref), where the
 //  *   supplied ref can be written out in place of the object. This
 //  *   refers back to the previous instance with the matching id.
 //  *
 //  *   If NOT already cached, the CacheResult is New(id), where the
 //  *   id should be written out with the object, so that it can be
 //  *   referenced by future refs.
 //  */
 // def cache(thing:Any): CacheResult = {
 //   c.get(thing) match {
 //     case None =>
 //       val id = nextId
 //       nextId = nextId + 1
 //       c.put(thing, id)
 //       New(id)
 // 
 //     case Some(ref) => Cached(ref)
 //   }
 // }
 // 
 // private val cachedBoxIds = scala.collection.mutable.HashSet[Long]()
 // 
 // /**
 //  * Cache a box
 //  * @param id  The id of the box to cache
 //  */
 // def cacheBox(id: Long): CacheResult = if (cachedBoxIds.contains(id)) Cached(id) else New(id)

 def close(): Unit
}