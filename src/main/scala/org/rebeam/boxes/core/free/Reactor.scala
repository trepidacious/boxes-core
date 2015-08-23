package org.rebeam.boxes.core.free

import org.rebeam.boxes.core.free.BoxTypes.BoxScript
import grizzled.slf4j.Logging

import scala.collection.immutable.Set

object Reactor extends Logging{

  val maxCycle = 10000

  /* Find all reactions that must be run based on deltas (i.e. any reactions sourcing a written box, or any newly
   * registered reaction). Run these using the provided rad, and return a rad with the reads/writes/reaction graph
   * changes caused by the reactions we ran appended.
   */
  def react(rad: RevisionAndDeltas, deltas: BoxDeltas): RevisionAndDeltas = {
    println("Reacting to deltas " + deltas)

    //TODO reimplement functional react, currently have an imperative implementation,
    //the mutable state in reactIm shouldn't be visible externally
//    //Pend any newly created reactions, plus any reactions with the box as a source
//    val pendingReactionIds = deltas.deltas.foldLeft(Set.empty[Long]){
//      case (s, CreateReaction(reaction, action)) => s + reaction.id
//      case (s, WriteBox(box, _)) => s ++ rad.reactionGraph.reactionsSourcingBox(box.id)
//      case (s, _) => s
//    }
//
//    //Now apply the reactions
//    val reactedRad = pendingReactionIds.foldLeft(rad)((rad, rid) => {
//      rad.scriptForReactionId(rid) match {
//        case Some(script) =>
//          //Note we are evaluating reactions individually, so we don't want to run any triggered by the current
//          //reaction - we will manually check for conflicts
//          val (rad2, result, scriptDeltas) = rad.appendScript(script, false)
//
//          //Use the deltas to work out new sources/targets for the reaction
//          val sourceBoxes = scriptDeltas.deltas.foldLeft(Set.empty[Long])((s, d) => d match {
//            case ReadBox(box) => s + box.id
//            case _ => s
//          })
//          val targetBoxes = scriptDeltas.deltas.foldLeft(Set.empty[Long])((s, d) => d match {
//            case WriteBox(box, _) => s + box.id
//            case _ => s
//          })
//          val rg = rad2.reactionGraph.updatedForReactionId(rid, sourceBoxes, targetBoxes)
//
//          //Add the reaction graph update to rad2, it reflects the state after applying the reaction
//          rad2.appendDeltas(BoxDeltas.single(UpdateReactionGraph(rg)))
//
//        case None => rad
//      }
//    })
//
//    reactedRad
    reactIm(rad, deltas)
  }

  def radWithReactionRemoved(rad: RevisionAndDeltas, rid: Long): RevisionAndDeltas =
    rad.appendDeltas(BoxDeltas.single(UpdateReactionGraph(rad.reactionGraph.updatedForReactionId(rid, Set.empty, Set.empty))))

  def runReactionScriptToDeltas(rad: RevisionAndDeltas, rid: Long): BoxDeltas = {
    val script = rad.scriptForReactionId(rid).getOrElse(throw new RuntimeException("Missing reaction for id " + rid))
    rad.appendScript(script, false)._3
  }

  def runReactionScript(rad: RevisionAndDeltas, rid: Long): RevisionAndDeltas = {

    val script = rad.scriptForReactionId(rid).getOrElse(throw new RuntimeException("Missing reaction for id " + rid))

    val (rad2, result, scriptDeltas) = rad.appendScript(script, false)

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
    rad2.appendDeltas(BoxDeltas.single(UpdateReactionGraph(rg)))

  }

  def reactIm(initialRad: RevisionAndDeltas, deltas: BoxDeltas): RevisionAndDeltas = {

    var finalRad = initialRad

    val reactionsPending = scala.collection.mutable.ArrayBuffer[Long]()

    //Pend any newly created reactions, plus any reactions with the box as a source
    deltas.deltas.foreach{
      case CreateReaction(reaction, action) => reactionsPending += reaction.id
      case WriteBox(box, _) => reactionsPending ++= finalRad.reactionGraph.reactionsSourcingBox(box.id)
      case _ => {}
    }

    //Ids of reactions that have failed
    val failedReactions = new scala.collection.mutable.HashSet[Long]()

    //Ids of reactions that may be in conflict with other reactions
    val conflictReactions = new scala.collection.mutable.HashSet[Long]()

    //Number of times each reaction has run in this cycle
    val reactionApplications = new scala.collection.mutable.HashMap[Long, Int]().withDefaultValue(0)

    //Keep cycling until we clear all reactions
    while (!reactionsPending.isEmpty) {
      val nextReaction = reactionsPending.remove(0)

      try {

        //Run the script for nextReaction, appending sources/targets as required
        finalRad = runReactionScript(finalRad, nextReaction)

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

    //Check all reactions whose TARGETS were changed
    //by other reactions are still happy with the state of their targets,
    //if they are not, this indicates a conflict and should generate a warning.
    //Note this is NOT the same as when a reaction is applied then has a source
    //changed, this should just result in the reaction being reapplied without
    //the expectation of no writes to its targets.
    conflictReactions.foreach{ r =>
      val deltas = runReactionScriptToDeltas(finalRad, r)

      //If there are any deltas that change state, reaction is conflicting and fails
      val conflict = deltas.deltas.exists{
        case WriteBox(b, t) => finalRad.get(b) != t
        case CreateReaction(_, _) => true
        case CreateBox(_) => true
        case Observe(_) => true
        case Unobserve(_) => true
        case ReadBox(_) => false
        case UpdateReactionGraph(_) => false
      }
      if (conflict) {
        logger.error("Reaction id " + r + " conflicted")
        //Remove the reaction completely from the system, but remember that it failed
        finalRad = radWithReactionRemoved(finalRad, r)
        failedReactions.add(r)
      }
    }

    //TODO: replace this functionality - can provide changes to script interpreter, and add deltaF to retrieve
//    changedSourcesForReaction.clear()

    if (!failedReactions.isEmpty) {
      logger.debug("Failed Reactions: " + failedReactions)
      throw new FailedReactionsException()
    }

    finalRad
  }

}
