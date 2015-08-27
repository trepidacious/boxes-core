package org.rebeam.boxes.core.free

import grizzled.slf4j.Logging

import scala.collection.immutable.Set

object Reactor extends Logging {

  val maxCycle = 10000

  /* Find all reactions that must be run based on deltas (i.e. any reactions sourcing a written box, or any newly
   * registered reaction). Run these using the provided rad, and return a rad with the reads/writes/reaction graph
   * changes caused by the reactions we ran appended.
   */
  def react(rad: RevisionAndDeltas, deltas: BoxDeltas): RevisionAndDeltas = {
    //TODO reimplement functional react, currently have an imperative implementation,
    //although the mutable state in reactIm shouldn't be visible externally
    reactImpure(rad, deltas)
  }

  def radWithReactionRemoved(rad: RevisionAndDeltas, rid: Long): RevisionAndDeltas =
    rad.appendDeltas(BoxDeltas.single(UpdateReactionGraph(rad.reactionGraph.updatedForReactionId(rid, Set.empty, Set.empty))))

  def runReactionScriptToDeltas(rad: RevisionAndDeltas, rid: Long, changedSources: Set[Box[_]]): BoxDeltas = {
    val script = rad.scriptForReactionId(rid).getOrElse(throw new RuntimeException("Missing reaction for id " + rid))
    rad.appendScript(script, false, changedSources)._3
  }

  def runReactionScript(rad: RevisionAndDeltas, rid: Long, changedSources: Set[Box[_]]): (RevisionAndDeltas, BoxDeltas) = {

    val script = rad.scriptForReactionId(rid).getOrElse(throw new RuntimeException("Missing reaction for id " + rid))

    val (rad2, result, scriptDeltas) = rad.appendScript(script, false, changedSources)

    //Use the deltas to work out new sources/targets for the reaction
    val sourceBoxes = scriptDeltas.deltas.foldLeft(Set.empty[Long])((s, d) => d match {
      case ReadBox(box) => s + box.id
      case _ => s
    })
    val targetBoxes = scriptDeltas.deltas.foldLeft(Set.empty[Long])((s, d) => d match {
      case WriteBox(box, _) => s + box.id
      case _ => s
    })
    val rg = rad2.reactionGraph.updatedForReactionId(rid, sourceBoxes, targetBoxes)

    //Add the reaction graph update to rad2, it reflects the state after applying the reaction, this
    //is our result
    (rad2.appendDeltas(BoxDeltas.single(UpdateReactionGraph(rg))), scriptDeltas)
  }

  def reactImpure(initialRad: RevisionAndDeltas, deltas: BoxDeltas): RevisionAndDeltas = {

    var finalRad = initialRad

    val reactionsPending = scala.collection.mutable.ArrayBuffer[Long]()

    val changedSourcesForReaction = new scala.collection.mutable.HashMap[Long, scala.collection.mutable.Set[Box[_]]] with scala.collection.mutable.MultiMap[Long, Box[_]]

    //Pend any newly created reactions, plus any reactions with the box as a source
    deltas.deltas.foreach{
      case CreateReaction(reaction, action) => reactionsPending += reaction.id
      case WriteBox(box, _) => {
        val sourcingReactions = finalRad.reactionGraph.reactionsSourcingBox(box.id)
        reactionsPending ++= sourcingReactions
        //Also add written box to changed sources for the reactions
        for (rid <- sourcingReactions) {
          changedSourcesForReaction.addBinding(rid, box)
        }
      }
      case _ => {}
    }

    //Ids of reactions that have failed
    val failedReactions = new scala.collection.mutable.HashSet[Long]()

    //Ids of reactions that may be in conflict with other reactions
    val conflictReactions = new scala.collection.mutable.HashSet[Long]()

    //Number of times each reaction has run in this cycle, by reaction id
    val reactionApplications = new scala.collection.mutable.HashMap[Long, Int]().withDefaultValue(0)

    //Keep cycling until we clear all reactions
    while (!reactionsPending.isEmpty) {
      val nextReaction = reactionsPending.remove(0)

      try {

        //Run the script for nextReaction, appending sources/targets in reaction graph as required
        //Also pass in the changed sources for this reaction, in case it needs this information
        //to decide how to act
        val changedSources = changedSourcesForReaction.get(nextReaction).getOrElse(Set.empty[Box[_]]).toSet
        val (newRad, reactionDeltas) = runReactionScript(finalRad, nextReaction, changedSources)
        finalRad = newRad

        //Check for too many applications
        val applications = reactionApplications.getOrElse(nextReaction, 0)
        if (applications + 1 > maxCycle) {
          throw new ReactionAppliedTooManyTimesInCycle("Reaction id " + nextReaction + " applied " + applications + " times, will be removed")
        } else {
          reactionApplications.put(nextReaction, applications + 1)
        }

        //We now have the correct targets for this reaction, so
        //we can track them for conflicts
        for {
          target <- finalRad.reactionGraph.targetsOfReaction(nextReaction)
          conflictReaction <- finalRad.reactionGraph.reactionsTargettingBox(target) if (conflictReaction != nextReaction)
        } conflictReactions.add(conflictReaction)

        //Now also pend any reactions that have had sources changed by this reaction,
        //and add the written boxes to changed sources for that reaction
        reactionDeltas.deltas.foreach{
          case WriteBox(box, _) =>
            for (sourcingReaction <- finalRad.reactionGraph.reactionsSourcingBox(box.id) if (sourcingReaction != nextReaction)) {
              if (!reactionsPending.contains(sourcingReaction)) reactionsPending += sourcingReaction
              changedSourcesForReaction.addBinding(sourcingReaction, box)
            }
          case _ => {}
        }

      } catch {
        //TODO If this is NOT a BoxException, need to respond better, but can't allow uncaught exception to just stop cycling
        case e:Exception => {
          logger.error("Reaction failed", e)

          //Remove the reaction completely from the system, but remember that it failed
          finalRad = radWithReactionRemoved(finalRad, nextReaction)
          conflictReactions.remove(nextReaction)
          failedReactions.add(nextReaction)
          val filtered = reactionsPending.filter(_ != nextReaction)
          reactionsPending.clear()
          reactionsPending ++= filtered
        }
      }

    }

    //Check that all reactions whose TARGETS were changed
    //by other reactions are still happy with the state of their targets.
    //If they are not, this indicates a conflict and should generate a warning.
    //Note this is NOT the same as when a reaction is applied then has a source
    //changed, this should just result in the reaction being reapplied without
    //the expectation of no writes to its targets.
    conflictReactions.foreach{ r =>

      val changedSources = changedSourcesForReaction.get(r).getOrElse(Set.empty[Box[_]]).toSet

      val deltas = runReactionScriptToDeltas(finalRad, r, changedSources)

      //If there are any deltas that change state, reaction is conflicting and fails
      if (finalRad.deltasWouldChange(deltas)) {
        logger.error("Reaction id " + r + " conflicted")
        //Remove the reaction completely from the system, but remember that it failed
        finalRad = radWithReactionRemoved(finalRad, r)
        failedReactions.add(r)
      }
    }

    if (!failedReactions.isEmpty) {
      logger.debug("Failed Reactions: " + failedReactions)
      throw new FailedReactionsException()
    }

    finalRad
  }

}
