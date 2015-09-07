package org.rebeam.boxes.persistence

sealed trait TokenName

/**
 * There is no name for this token
 */
case object NoName extends TokenName

/**
 * There is a name for this token, it is not needed for disambiguation (e.g. to choose
 * an implementation of a trait or class), but can be used for presentation in a format that
 * prefers this (e.g. as a tag name for the parent tag in XML)
 * @param name  The name of the dictionary
 */
case class PresentationName(name: String) extends TokenName

/**
 * There is a name for this token, and it is needed for disambiguation (e.g. to choose
 * an implementation of a trait or class). It must be included in all formats, even those that
 * don't normally name the structure associated with the token. For example in JSON this might result in
 * a "type tag" field being set in an object, alongside the fields for dictionary entries.
 * @param name  The name of the dictionary
 */
case class SignificantName(name: String) extends TokenName
