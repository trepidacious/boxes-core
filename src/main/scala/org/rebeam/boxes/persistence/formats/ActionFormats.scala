package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scala.language.implicitConversions

object ActionFormats {
  
  /**
   * Extend an existing format with a modifyBox implementation
   * This is also lazy, so can be used with recursive classes.
   *
   * @param format A non-lazy format to which to lazily delegate
   * @param modifyBoxF Modify box function - produces a script that will
   *                    use tokens to modify the given box. 
   */
  def actionFormat[T](format: => Format[T], modifyBoxF: (Box[T]) => BoxReaderScript[Unit]) = new Format[T] {
    lazy val delegate = format
    def write(t: T) = delegate.write(t)
    def read = delegate.read
    def replace(t: T, boxId: Long) = delegate.replace(t, boxId)
    def modify(t: T, boxId: Long) = delegate.modify(t, boxId)
    def modifyBox(b: Box[T]) = modifyBoxF(b)
  }
  
  def actionFormat[T, A <: Action[T]](formatT: => Format[T])(implicit formatA: Format[A]) = {    
    import BoxReaderDeltaF._
    val mod = (b: Box[T]) => for {
      t <- peek
      _ <- if (t == EndToken) {
        nothing
      } else {
        for {
          action <- formatA.read
          _ <- embedBoxScript(action.act(b))
        } yield ()
      }
    } yield ()
    
    actionFormat[T](formatT, mod)
  }
  
  case class actionFormatBuilder[T](formatT: Format[T]) {
    def withAction[A <: Action[T]](formatA: Format[A]) = actionFormat(formatT)(formatA)
  }
  
  implicit def formatToActionFormatBuilder[T](formatT: Format[T]) = actionFormatBuilder[T](formatT)

}