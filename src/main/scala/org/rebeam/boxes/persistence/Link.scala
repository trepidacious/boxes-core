package org.rebeam.boxes.persistence

/**
 * A link, either empty, or indicating that an object can be linked to (referenced), or indicating
 * that it links to another (references it).
 */
sealed trait Link

/**
 * Link referencing another object (of same type)
 * @param id  The id of the referenced object
 */
case class LinkRef(id: Long) extends Link

/**
 * Link indicating that this object can be referenced using
 * the specified id from another object (of the same type)
 * @param id  The id of this object
 */
case class LinkId(id: Long) extends Link

/**
 * No link for this object - it doesn't reference any other object, and
 * cannot be referenced
 */
case object LinkEmpty extends Link

/**
 * Gives the strategy to be used when reading and writing data elements that support Links
 */
sealed trait LinkStrategy

/**
 * A subset of LinkStrategies that are suitable for use with strict formats like NodeFormat
 */
sealed trait NoDuplicatesLinkStrategy extends LinkStrategy

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
case object EmptyLinks extends LinkStrategy with NoDuplicatesLinkStrategy

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
case object IdLinks extends LinkStrategy with NoDuplicatesLinkStrategy

/**
 * Data elements are serialised and deserialised using LinkId the first time an element is encountered, and LinkRef
 * each subsequent time that an equal element is encountered. LinkEmpty is never used. This means that where a
 * single data elementis present at more than one position in the graph, it will be deserialised as a single data
 * element in more than one position.
 * This is most suited for data being stored for later reading and use in this library, for example saving data
 * to a file. This is the only strategy that permits duplicates.
 */
case object AllLinks extends LinkStrategy
