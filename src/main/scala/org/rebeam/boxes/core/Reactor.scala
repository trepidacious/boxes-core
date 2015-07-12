package org.rebeam.boxes.core

/**
 * The interface available to Reactions as they run, which provides both the
 * transaction the Reaction is running in, and additional data that is only
 * useful to Reactions, and only exists while a Reactor is processing reactions
 * in one "cycle".
 */
trait ReactorTxn extends Txn {
  
  /**
   * Get the set of Boxes that are sources of the Reaction, and have changed
   * during this Reactor's execution
   */
  def changedSources: Set[BoxR[_]]
}

/**
 * ReactorForTxn and TxnForReactor provide one possible means of communication
 * between a Reactor and a Transaction, for example when using the ReactorDefault
 * implementation, but is not part of public API.
 */
trait ReactorForTxn {
  /**
   * Must be called after any box has a new value set in the transaction.
   * @param box The box whose value has been set
   * @param t The new value for the box
   * @param differentValue True if the value is different to the old value, false if it is the same and so is ignored.
   * @tparam T The type ov value in the box
   */
  def afterSet[T](box: Box[T], t: T, differentValue: Boolean): Unit
  def afterGet[T](box: BoxR[T]): Unit
  def registerReaction(r: Reaction): Unit
  def beforeCommit(): Unit
}

/**
 * ReactorForTxn and TxnForReactor provide one possible means of communication
 * between a Reactor and a Transaction, for example when using the ReactorDefault
 * implementation, but is not part of public API.
 */
trait TxnForReactor extends Txn {
  def reactionFinished()
  def clearReactionSourcesAndTargets(rid: Long)
  
  def targetsOfReaction(rid: Long): Set[Long]
  def sourcesOfReaction(rid: Long): Set[Long]
  
  def reactionsTargettingBox(bid: Long): Set[Long]
  def reactionsSourcingBox(bid: Long): Set[Long]
  
  def react(rid: Long)
  
  def addTargetForReaction(rid: Long, bid: Long)
  def addSourceForReaction(rid: Long, bid: Long)
}