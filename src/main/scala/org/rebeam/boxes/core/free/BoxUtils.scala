package org.rebeam.boxes.core.free

object BoxUtils {
  import BoxTypes._

  def atomic[A](s: BoxScript[A]): A = Shelf.atomic(s)

  def create[T](t: T)                     = BoxDeltaF.create(t)
  def set[T](box: Box[T], t: T)           = BoxDeltaF.set(box, t)
  def get[T](box: Box[T])                 = BoxDeltaF.get(box)
  def observe(observer: Observer)         = BoxDeltaF.observe(observer)
  def unobserve(observer: Observer)       = BoxDeltaF.unobserve(observer)
  def createReaction(action: BoxScript[Unit]) = BoxDeltaF.createReaction(action)
  def attachReactionToBox(r: Reaction, b: Box[_]) = BoxDeltaF.attachReactionToBox(r, b)
  def detachReactionFromBox(r: Reaction, b: Box[_]) = BoxDeltaF.detachReactionFromBox(r, b)
  def changedSources() = BoxDeltaF.changedSources()
  def just[T](t: T)                       = BoxDeltaF.just(t)

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

}
