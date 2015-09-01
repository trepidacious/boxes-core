package org.rebeam.boxes.core

class BoxException(message: String = "") extends Exception(message)
class FailedReactionsException(message: String = "") extends BoxException(message)
class ConflictingReactionException(message: String = "") extends BoxException(message)
class ReactionAppliedTooManyTimesInCycle(message: String = "") extends BoxException(message)
