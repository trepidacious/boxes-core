package org.rebeam.boxes.core

import org.rebeam.boxes.core._

import scalaz._
import Scalaz._

import BoxTypes._

import scala.annotation.tailrec
import scala.collection.immutable.Set
import BoxDelta._

object BoxScript {
  
  implicit class BoxInScript[T](b: Box[T]) {
    /** Get the value of this box in the context of a BoxScript - can be used in for-comprehensions */
    def get() = BoxDeltaF.get(b)

    /**
     * Set the value of this box in the context of a State - can be used in for-comprehensions
     * to set Box value in associated RevisionDelta
     */
    def set(t: T) = BoxDeltaF.set(b, t)

    def apply() = get()

    def update(t: T) = set(t)

    def attachReaction(reaction: Reaction) = BoxDeltaF.attachReactionToBox(reaction, b)
    def detachReaction(reaction: Reaction) = BoxDeltaF.detachReactionFromBox(reaction, b)

    def applyReaction(rScript: BoxScript[T]) = for {
      r <- BoxScript.createReaction(for {
        t <- rScript
        _ <- set(t)
      } yield ())
      _ <- b.attachReaction(r)
    } yield r

  }

  def create[T](t: T)                               = BoxDeltaF.create(t)
  def set[T](box: Box[T], t: T)                     = BoxDeltaF.set(box, t)
  def get[T](box: Box[T])                           = BoxDeltaF.get(box)
  def observe(observer: Observer)                   = BoxDeltaF.observe(observer)
  def unobserve(observer: Observer)                 = BoxDeltaF.unobserve(observer)
  def createReaction(action: BoxScript[Unit])       = BoxDeltaF.createReaction(action)
  def attachReactionToBox(r: Reaction, b: Box[_])   = BoxDeltaF.attachReactionToBox(r, b)
  def detachReactionFromBox(r: Reaction, b: Box[_]) = BoxDeltaF.detachReactionFromBox(r, b)
  def changedSources()                              = BoxDeltaF.changedSources()
  def just[T](t: T)                                 = BoxDeltaF.just(t)
  val nothing                                       = BoxDeltaF.nothing

  def modify[T](b: Box[T], f: T => T) = for {
    o <- b()
    _ <- b() = f(o)
  } yield o

  def cal[T](script: BoxScript[T]) = for {
    initial <- script
    box <- create(initial)
    reaction <- createReaction{
      for {
        result <- script
        _ <- box() = result
      } yield ()
    }
    _ <- box.attachReaction(reaction) //Attach the reaction to the box it updates, so that it will
                                      //not be GCed as long as the box is around. Remember that reactions
                                      //are not retained just by reading from or writing to boxes.
  } yield box

  implicit class BoxScriptPlus[A](s: BoxScript[A]) {
    final def andThen[B](f: => BoxScript[B]): BoxScript[B] = s flatMap (_ => f)
  }

  /**
   * Run a script and append the deltas it generates to an existing RevisionAndDeltas, creating a new
   * RevisionAndDeltas and a script result
   * @param script    The script to run
   * @param rad       The initial RevisionAndDeltas
   * @param runReactions  True to run reactions when they are created, or when boxes are written. False to ignore reactions
   * @tparam A        The result type of the script
   * @return          (new RevisionAndDeltas, script result, all deltas applied directly by the script - excluding those from reactions)
   */
  @tailrec final def run[A](script: BoxScript[A], rad: RevisionAndDeltas, boxDeltas: BoxDeltas, runReactions: Boolean = true, changedSources: Set[Box[_]] = Set.empty): (RevisionAndDeltas, A, BoxDeltas) = script.resume match {

    case -\/(CreateBoxDeltaF(t, toNext)) =>
      val (deltas, box) = rad.create(t)
      val next = toNext(box)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ReadBoxDeltaF(b, toNext)) =>
      val (deltas, value) = rad.get(b)
      val next = toNext(value)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(WriteBoxDeltaF(b, t, next)) =>
      val (deltas, box) = rad.set(b, t)
      val rad2 = rad.appendDeltas(deltas)
      //TODO - we can probably just skip calling react for efficiency if the write does not
      //change box state. Note that the reactor itself uses its own mechanism
      //to observe writes when applying reactions, unlike in old mutable-txn
      //system
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      run(next, rad3, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(CreateReactionDeltaF(action, toNext)) =>
      val (deltas, reaction) = rad.createReaction(action)
      val next = toNext(reaction)
      val rad2 = rad.appendDeltas(deltas)
      val rad3 = if (runReactions) Reactor.react(rad2, deltas) else rad2
      run(next, rad3, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ObserveDeltaF(obs, next)) =>
      val deltas = rad.observe(obs)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(UnobserveDeltaF(obs, next)) =>
      val deltas = rad.unobserve(obs)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(AttachReactionToBoxF(r, b, next)) =>
      val deltas = rad.attachReactionToBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(DetachReactionFromBoxF(r, b, next)) =>
      val deltas = rad.detachReactionFromBox(r, b)
      val rad2 = rad.appendDeltas(deltas)
      run(next, rad2, boxDeltas.append(deltas), runReactions, changedSources)

    case -\/(ChangedSourcesF(toNext)) =>
      val next = toNext(changedSources)
      run(next, rad, boxDeltas, runReactions, changedSources)

    case -\/(JustF(t, toNext)) =>
      val next = toNext(t)
      run(next, rad, boxDeltas, runReactions, changedSources)

    case \/-(x) => (rad, x.asInstanceOf[A], boxDeltas)
  }
}