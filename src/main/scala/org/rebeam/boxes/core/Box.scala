package org.rebeam.boxes.core

import java.util.concurrent.atomic.AtomicInteger

import BoxTypes._

class Box[T](val id: Long) extends Identifiable {

  /**
   * Store changes to this box, as a map from the Change to the State that was
   * set by that Change. Changes that form a revision are retained by Revision
   * and used to look up States in refs.
   * Since this is a weak map, it does not retain Changes, they are retained only
   * by the revisions they are in.
   * When a Box is GCed, the changes are also GCed, allowing the States
   * of the Box (instances of T) to be removed.
   */
  private val changes = new scala.collection.mutable.WeakHashMap[BoxChange, T]()

  private[core] def addChange(c: BoxChange, t: T) = changes.put(c, t)

  private[core] def getValue(c: BoxChange) = changes.get(c)

  override def toString = "Box(" + id + ")"

  /** Get the value of this box in the context of a BoxScript - can be used in for-comprehensions */
  def get() = BoxDeltaF.get(this)

  /**
   * Set the value of this box in the context of a State - can be used in for-comprehensions
   * to set Box value in associated RevisionDelta
   */
  def set(t: T) = BoxDeltaF.set(this, t)

  def apply() = get()

  def update(t: T) = set(t)

  def get(revision: Revision): T = revision.valueOf(this).getOrElse(throw new RuntimeException("Missing Box(" + this.id + ")"))
  def apply(revision: Revision): T = get(revision)

  def attachReaction(reaction: Reaction) = BoxDeltaF.attachReactionToBox(reaction, this)
  def detachReaction(reaction: Reaction) = BoxDeltaF.detachReactionFromBox(reaction, this)

  def applyReaction(rScript: BoxScript[T]) = for {
    r <- BoxUtils.createReaction(for {
      t <- rScript
      _ <- set(t)
    } yield ())
    _ <- this.attachReaction(r)
  } yield r

}

object Box {
  private val nextId = new AtomicInteger(0)
  def apply[T](): Box[T] = new Box[T](nextId.getAndIncrement())
}

case class BoxState[+T](revision: Long, value: T)

//Note we use BoxChange as a key, and due to garbage collection we can't treat different changes as equal, so we use
//a normal class, and rely on different instances being unequal even if the revision is the same.
//However BoxChange is not a public API, and so is pretty much an implementation detail to get garbage collection
//to work as needed - retaining box values only as long as both the Box and the Revision where the Box had the value
//are retained.
class BoxChange(val revision: Long)
