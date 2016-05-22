package org.rebeam.boxes.persistence

/**
 * A link, either empty, or giving an identifier for an object
 */
sealed trait Link

/**
 * Link associating an identifier with this object
 * @param id  The id of this object
 */
case class LinkId(id: Long) extends Link

/**
 * No link for this object - it isn't identified
 */
case object LinkEmpty extends Link

/**
 * Gives the strategy to be used when reading and writing data elements that support Links
 */
sealed trait LinkStrategy {
  def link(id: Long): Link
}

/**
 * When writing tokens, only LinkEmpty links are used, and as a result "mutable" data elements must not be duplicated -
 * there must be no two elements in different positions in the serialised graph that are mutable and equal to
 * each other ("mutable" elements are Boxes and anything that is serialised with Boxes, e.g. using NodeFormats, and such
 * elements implement equality by identicality. Note that these elements are not actually mutable, but resolve to
 * different values in different Revisions. In addition, Nodes may register Reactions when created, and so we do
 * not wish to have duplicates.).
 *
 * Immutable data may be duplicated (equal elements in different positions in the graph), and will be written to the
 * token stream in full each time encountered.
 *
 * This may be useful when reading or writing data
 * from or to systems that do not support references, for example when producing "standard" JSON data.
 * Data with no references or duplicates may also be more predictable and easier to understand.
 * When reading token data, only LinkEmpty links must be present.
 */
case object EmptyLinks extends LinkStrategy {
  def link(id: Long) = LinkEmpty
}

/**
 * When writing tokens, only LinkId links are used, and as a result "mutable" data elements must not be duplicated -
 * there must be no two elements in different positions in the serialised graph that are mutable and equal to
 * each other ("mutable" elements are Boxes and anything that is serialised with Boxes, e.g. using NodeFormats, and such
 * elements implement equality by identicality. Note that these elements are not actually mutable, but resolve to
 * different values in different Revisions. In addition, Nodes may register Reactions when created, and so we do
 * not wish to have duplicates.).
 *
 * Immutable data may be duplicated (equal elements in different positions in the graph), and will be written to the
 * token stream in full each time encountered.
 *
 * This is useful when identifiers are associated with data, mostly when using
 * boxes. For example we might wish to send a json representation of the data including an id for each box, so that a
 * client could then receive updates tagged with an id when those boxes change on the server, and/or request that the
 * server makes changes to those boxes.
 * When reading token data, only LinkEmpty links must be present.
 */
case object IdLinks extends LinkStrategy {
  def link(id: Long) = LinkId(id)
}
