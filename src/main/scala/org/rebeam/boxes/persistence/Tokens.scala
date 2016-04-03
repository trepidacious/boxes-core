package org.rebeam.boxes.persistence

import scala.annotation.implicitNotFound
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
 * Note that the link ids used are unique only for Boxes within one Shelf, since they may be
 * the Box.id() values of the original serialised Box. Note that they may conflict with link ids
 * used for other types, but are distinguished by being in BoxTokens.
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

/**
 * When a stream of tokens is exhausted, it should return EndToken whenever
 * queried.
 */
case object EndToken extends Token
