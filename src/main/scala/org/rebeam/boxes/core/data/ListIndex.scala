package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

case class ListIndex[T](selection: Box[Option[T]], index: BoxM[Option[Int]])

object ListIndex {
  import ListIndexing._
  def apply[T](list: BoxR[List[T]], selectFirstByDefault: Boolean = true): BoxScript[ListIndex[T]] = for {

    //Make a new box for selection
    selection <- create(None: Option[T])

    //Keep the selection in the list
    selectionInList <- optionIsInList(list, selection, if (selectFirstByDefault) selectFirstAsOption[T] else selectNoneAsOption[T])

    //Attach reaction to selection so selection will be kept up to date until it is GCed
    _ <- selection.attachReaction(selectionInList)

  //Make a ListIndex, using a BoxM for view as integer index selection
  } yield ListIndex(selection, indexFromListAndOption(list, selection))
}
