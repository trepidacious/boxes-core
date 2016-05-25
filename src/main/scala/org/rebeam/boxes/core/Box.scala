package org.rebeam.boxes.core

import java.util.concurrent.atomic.AtomicInteger

import BoxTypes._

sealed trait BoxL[A] {
  def apply(): BoxScript[A]
  def update(a: A): BoxScript[Unit]
}

/**
 * Provides a BoxR and BoxW. Will often read and write the "same" state, however doesn't need to.
 * 
 * This should be used instead of a Box wherever that Box needs to be read and written, but
 * no other features of the box are needed (for example the box id). This covers nearly all uses
 * of Boxes.
 *
 * In summary, Box itself should be used when we want to create an actual data storage location,
 * and BoxM (or BoxR/BoxW) when all we want to do is read/write that data. This means the primary
 * use of Box is to create Nodes - case classes where (some or all) fields are Boxes.
 */
case class BoxM[A](read: BoxScript[A], write: BoxW[A]) extends BoxL[A] {
  def apply() = read
  def update(a: A) = write(a)
}

class Box[T](val id: Long) extends Identifiable with BoxL[T] {

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

  def get(revision: Revision): T = revision.valueOf(this).getOrElse(throw new RuntimeException("Missing Box(" + this.id + ")"))
  def apply(revision: Revision): T = get(revision)

  //As scripts for read, write and read/write
  lazy val r = BoxScriptImports.get(this)
  lazy val w = (t: T) => BoxScriptImports.set(this, t)
  lazy val m = BoxM(r, w)
  def readAs[A >: T] = r.map(t => t: A)

  def apply() = BoxScriptImports.get(this)
  def update(t: T) = BoxScriptImports.set(this, t)

}

object Box {
  //TODO this should really be done better...
  private val nextId = new AtomicInteger(0)
  private[core] def newInstance[T](): Box[T] = new Box[T](nextId.getAndIncrement())
}

case class BoxState[+T](revision: Long, value: T)

//Note we use BoxChange as a key, and due to garbage collection we can't treat different changes as equal, so we use
//a normal class, and rely on different instances being unequal even if the revision is the same.
//However BoxChange is not a public API, and so is pretty much an implementation detail to get garbage collection
//to work as needed - retaining box values only as long as both the Box and the Revision where the Box had the value
//are retained.
private[core] class BoxChange(val revision: Long)

