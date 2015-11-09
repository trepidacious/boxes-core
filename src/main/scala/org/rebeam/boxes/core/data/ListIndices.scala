package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

case class ListIndices[T](selection: Box[Set[T]], indices: BoxM[Set[Int]])

object ListIndices {
  import ListIndexing._

  def apply[T](list: BoxR[List[T]], selectAllByDefault: Boolean = true): BoxScript[ListIndices[T]] = for {

    //Make a new box for selection
    selection <- create(Set.empty[T])

    //Keep the selection in the list
    selectionInList <- setIsInList(list, selection, if (selectAllByDefault) selectAllAsSet[T] else selectNoneAsSet[T])

    //Attach reaction to selection so selection will be kept up to date until it is GCed
    _ <- selection.attachReaction(selectionInList)

  //Make a ListIndex, using a BoxM for view as integer index selection
  } yield ListIndices(selection, indexFromListAndSet(list, selection))
}
