package org.rebeam.boxes.core

import annotation.implicitNotFound
import scala.collection._
import scala.language.implicitConversions

sealed trait Token

/**
 * Open a dictionary, which is a map from Strings to arbitrary values
 * Must be followed by none or more pairs of (DictEntry, encoded value as tokens), then a CloseDict, unless
 * link is LinkRef(id), in which case no further tokens are necessary, and the Dict is taken to be the
 * same one original defined with LinkId(id).
 * @param name  The name of the dictionary - defaults to NoName
 * @param link  The link for this dict, defaults to LinkEmpty for a standalone dict with no id or ref.
 */
case class OpenDict(name: TokenName = NoName, link: Link = LinkEmpty) extends Token

/**
 * Start an entry in a dictionary. Must be between OpenDict and CloseDict tokens, and
 * must be followed by tokens representing exactly one value, unless link is LinkRef in which
 * case it stands alone as an entry containing the referenced item (normally a Box)
 * @param key  The string key of the dict entry
 * @param link For plain dictionaries, this is always LinkEmpty. For dictionaries using links,
 *             may be a LinkId or LinkRef, normally the id used is the id of an associated Box.
 */
case class DictEntry(key: String, link: Link = LinkEmpty) extends Token

/**
 * Close a dictionary
 */
case object CloseDict extends Token

/**
 * Start a Box. If link is LinkEmpty or LinkId then this token must be followed
 * by an encoded value. If link is LinkRef then this box is the same Box as the referenced
 * box.
 * Note that the link ids used are unique only for Boxes within one Shelf, since they are
 * the Box.id() values of the original serialised Box. Note that they may conflict with link ids
 * used for other types.
 * @param link  The link for this box, defaults to LinkEmpty.
 */
case class BoxToken(link: Link = LinkEmpty) extends Token

sealed trait Prim[P] extends Token { def p: P }
case class BooleanToken(p: Boolean) extends Prim[Boolean]
case class IntToken(p: Int) extends Prim[Int]
case class LongToken(p: Long) extends Prim[Long]
case class FloatToken(p: Float) extends Prim[Float]
case class DoubleToken(p: Double) extends Prim[Double]
case class StringToken(p: String) extends Prim[String]
case class BigIntToken(p: BigInt) extends Prim[BigInt]
case class BigDecimalToken(p: BigDecimal) extends Prim[BigDecimal]

/**
 * Represents an Option value of None. Some is represented as the value itself.
 */
case object NoneToken extends Token

/**
 * Open an array, which is an ordered list of none or more arbitrary values
 * Must be followed by none or more arbitrary values encoded as tokens, then a CloseArr.
 * @param name  The name of the array - defaults to NoName
 */
case class OpenArr(name: TokenName) extends Token
case object CloseArr extends Token

//trait TokenWriter {
//  def write(t: Token)
//
//  private val c = collection.mutable.Map[Any, Int]()
//  private var nextId = 0
//
//  /**
//   * Try to cache a thing. The result will tell us whether the thing
//   * is already cached:
//   *
//   *   If already cached, the CacheResult is Cached(ref), where the
//   *   supplied ref can be written out in place of the object. This
//   *   refers back to the previous instance with the matching id.
//   *
//   *   If NOT already cached, the CacheResult is New(id), where the
//   *   id should be written out with the object, so that it can be
//   *   referenced by future refs.
//   */
//  def cache(thing:Any):CacheResult = {
//    c.get(thing) match {
//      case None =>
//        val id = nextId
//        nextId = nextId + 1
//        c.put(thing, id)
//        New(id)
//
//      case Some(ref) => Cached(ref)
//    }
//  }
//
//  private val cachedBoxIds = mutable.HashSet[Long]()
//
//  /**
//   * Cache a box
//   * @param id  The id of the box to cache
//   */
//  def cacheBox(id: Long) {
//    if (cachedBoxIds.contains(id)) throw new BoxCacheException("Box id " + id + " is already cached - don't cache it again!")
//    cachedBoxIds.add(id)
//  }
//
//  /**
//   * Check whether a box is already cached
//   * @param id  The id of the box to check
//   * @return    True if cacheBox has already been called on this id, indicating that the full contents have been
//   *            written already, and a ref can be used
//   */
//  def isBoxCached(id: Long) = cachedBoxIds.contains(id)
//
//  def close(): Unit
//}

//class IncorrectTokenException(m: String) extends RuntimeException(m)
//class NoTokenException extends RuntimeException
//class BoxCacheException(m: String) extends RuntimeException(m)
//class NodeCacheException(m: String) extends RuntimeException(m)
//class CacheException(m: String) extends RuntimeException(m)
//
//trait TokenReader {
//
//  @throws[NoTokenException]
//  def peek: Token
//
//  @throws[NoTokenException]
//  def pull(): Token
//
//  private val boxCache = new mutable.HashMap[Long, Box[_]]()
//
//  private val cache = new mutable.HashMap[Long, Any]()
//
//  def putCache(id: Long, thing: Any) = cache.put(id, thing) match {
//    case Some(existingThing) => throw new CacheException("Already have a thing " + existingThing + " for id " + id)
//    case _ =>
//  }
//
//  def getCacheOption(id: Long) = cache.get(id)
//  def getCache(id: Long) = getCacheOption(id).getOrElse(throw new CacheException("No cached thing for id " + id))
//
//  def putBox(id: Long, box: Box[_]) {
//    if (boxCache.get(id).isDefined) throw new BoxCacheException("Already have a box for id " + id)
//    boxCache.put(id, box)
//  }
//
//  def getBox(id: Long): Box[_] = {
//    boxCache.getOrElse(id, throw new BoxCacheException("No cached box for id " + id))
//  }
//
//  @throws [IncorrectTokenException]
//  def pullAndAssertEquals(t:Token) {
//    val p = pull()
//    if (p != t) throw new IncorrectTokenException("Expected " + t + ", got " + p)
//  }
//
//  @throws [IncorrectTokenException]
//  def pullAndAssert(filter: Token => Boolean) {
//    val p = pull()
//    if (!filter(p)) throw new IncorrectTokenException("Assertion failed on " + p)
//  }
//
//  @throws [IncorrectTokenException]
//  def pullBoolean(): Boolean = {
//    val t = pull()
//    t match {
//      case BooleanToken(s) => s
//      case _ => throw new IncorrectTokenException("Expected a BooleanToken, got " + t)
//    }
//  }
//  @throws [IncorrectTokenException]
//  def pullInt(): Int = {
//    val t = pull()
//    t match {
//      case IntToken(s) => s
//      case _ => throw new IncorrectTokenException("Expected an IntToken, got " + t)
//    }
//  }
//  @throws [IncorrectTokenException]
//  def pullLong(): Long = {
//    val t = pull()
//    t match {
//      case LongToken(s) => s
//      case _ => throw new IncorrectTokenException("Expected a LongToken, got " + t)
//    }
//  }
//  @throws [IncorrectTokenException]
//  def pullFloat(): Float = {
//    val t = pull()
//    t match {
//      case FloatToken(s) => s
//      case _ => throw new IncorrectTokenException("Expected a FloatToken, got " + t)
//    }
//  }
//  @throws [IncorrectTokenException]
//  def pullDouble(): Double = {
//    val t = pull()
//    t match {
//      case DoubleToken(s) => s
//      case _ => throw new IncorrectTokenException("Expected a DoubleToken, got " + t)
//    }
//  }
//  @throws [IncorrectTokenException]
//  def pullBigInt(): BigInt = {
//    val t = pull()
//    t match {
//      case BigIntToken(i) => i
//      case _ => throw new IncorrectTokenException("Expected a BigIntToken, got " + t)
//    }
//  }
//
//  @throws [IncorrectTokenException]
//  def pullBigDecimal(): BigDecimal = {
//    val t = pull()
//    t match {
//      case BigDecimalToken(n) => n
//      case _ => throw new IncorrectTokenException("Expected a BigDecimalToken, got " + t)
//    }
//  }
//  @throws [IncorrectTokenException]
//  def pullString(): String = {
//    val t = pull()
//    t match {
//      case StringToken(s) => s
//      case _ => throw new IncorrectTokenException("Expected a StringToken, got " + t)
//    }
//  }
//
//  def close(): Unit
//}
//
//case class WriteContext(writer: TokenWriter, revision: Revision)
//
//case class ReadContext(reader: TokenReader, txn: Txn)
//
//
///**
// * Provides reading of type T
// */
//@implicitNotFound(msg = "Cannot find Reads or Format type class for ${T}")
//trait Reads[T] {
//  /**
//   * Read an object from the context, returning the object read
//   * @param context The context from which to read
//   * @return        The object we've read
//   */
//  def read(context: ReadContext): T
//}
//
//object Reads {
//  implicit def func2Reads[T](f: ReadContext => T): Reads[T] = new Reads[T] {
//    def read(context: ReadContext) = f(context)
//  }
//}
//
///**
// * Provides writing of type T
// */
//@implicitNotFound(msg = "Cannot find Writes or Format type class for ${T}")
//trait Writes[T] {
//  /**
//   * Write an object to the context
//   * @param obj     The object to write
//   * @param context The context to which to append
//   */
//  def write(obj: T, context: WriteContext)
//}
//
//object Writes {
//  implicit def func2Writes[T](f: (T, WriteContext) => Unit): Writes[T] = new Writes[T] {
//    def write(obj: T, context: WriteContext) = f(obj, context)
//  }
//}
//
///**
// * Provides reading for type T from a context of type R, and writing to a context of type W.
// */
//trait Format[T] extends Reads[T] with Writes[T]
//
//object Writing {
//  def write[T](obj: T, context: WriteContext)(implicit writes: Writes[T]) = writes.write(obj, context)
//}
//object Reading {
//  def read[T](context: ReadContext)(implicit reads: Reads[T]): T = reads.read(context)
//}
//
//trait ReaderWriterFactory {
//  def reader(input:InputStream): TokenReader
//  def writer(output:OutputStream): TokenWriter
//}
//
//class IO(val factory: ReaderWriterFactory) {
//
//  def write[T: Writes](t: T, output: OutputStream)(implicit txn: TxnR) = {
//    val target = factory.writer(output)
//    val context = WriteContext(target, txn)
//    implicitly[Writes[T]].write(t, context)
//    target.close()
//  }
//
//  def read[T: Reads](input:InputStream)(implicit txn: Txn) = {
//    val source = factory.reader(input)
//    val context = ReadContext(source, txn)
//    val t = implicitly[Reads[T]].read(context)
//    source.close()
//    t
//  }
//
//  def writeNow[T: Writes](t: T, output: OutputStream)(implicit shelf: Shelf) = shelf.read(implicit txn => write(t, output))
//
//  def readNow[T: Reads](input:InputStream)(implicit shelf: Shelf) = shelf.transact(implicit txn => read(input), ReactionBeforeCommit)
//
//}