package org.rebeam.boxes.core.data

import org.rebeam.boxes.core.{Txn, Shelf, Box}

import scala.collection.immutable._

trait ListIndices[T] {
  val selected: Box[Set[T]]
  val indices: Box[Set[Int]]
}

private class ListIndicesDefault[T](val selected: Box[Set[T]], val indices: Box[Set[Int]]) extends ListIndices[T]

object ListIndices {
  
  def now[T](list: Box[_ <: Seq[T]], selectAllByDefault: Boolean = true)(implicit shelf: Shelf) = shelf.transact(implicit txn => apply(list, selectAllByDefault))
  def apply[T](list: Box[_ <: Seq[T]], selectAllByDefault: Boolean = true)(implicit txn: Txn) = {
    val selected: Box[Set[T]] = Box(Set())
    val indices: Box[Set[Int]] = Box(Set())
    
    val r = txn.createReaction(implicit rt => {
      val lis = list()
      val sel = selected()
      val ind = indices()

      val cs = rt.changedSources

      def useFirstIndex() = {
        //Use first selected index, and try to preserve it
        ind.reduceLeftOption{(a, b) => Math.min(a, b)} match {
          case None =>
            default()

          case Some(i) if i < 0 =>
            indices() = Set(0)
            selected() = Set(lis.head)

          case Some(i) if i >= lis.size =>
            indices() = Set(lis.size-1)
            selected() = Set(lis(lis.size-1))

          case Some(i) =>
            indices() = Set(i)
            selected() = Set(lis(i))

        }
      }
      
      def useIndices() = {
        //Filter indices to be in list
        val newI = ind.filter(i => i >= 0 && i < lis.size)
        //If empty, use default
        if (newI.isEmpty) {
          default()
          
        //Otherwise use indices to update selection
        } else {
          selected() = newI.map(i => lis(i))
        }
      }

      def clear() = {
        indices() = Set()
        selected() = Set()        
      }
      
      def consistent = (ind, sel, lis) match {
        case (i, s, Nil) if i.isEmpty && s.isEmpty => true                  //Must select nothing in empty list
        case (i, s, _) if i.isEmpty && s.isEmpty => !selectAllByDefault     //In non-empty list, selecting nothing is ok iff we are not selecting all by default
        case (i, s, l) =>                                                  //If we have a selection and a list, then check all indices are in list, and selection matches indices
          val iInList = i.filter(i => i >= 0 && i < l.size)
          iInList.size == i.size && iInList.map(i => l(i)) == s
      }
      
      def default() = {
        if (!selectAllByDefault || lis.isEmpty) {
          indices() = Set()
          selected() = Set()
        } else {
          indices() = Range(0, lis.size).toSet
          selected() = indices().map(i => lis(i))
        }
      }
      
      //If we are already consistent, nothing to do
      if (consistent) {
//        println("Already consistent")
        
      //If list is empty, no selection
      } else if (lis.isEmpty) {
        println("Empty list, clearing")
        clear()
        
      //If just the index has changed, it is authoritative
      } else if (cs == Set(indices)) {
//        println("Just index changed, using indices " + i)
        useIndices()
        
      //Otherwise try to use the selection
      } else {
//        println("Trying selection")

        //Find indices of selection in list
        val newIndices = sel.map(lis.indexOf(_)).filter(_ > -1)
        
        //Some of selection is still in list, update index
        if (!newIndices.isEmpty) {
//          println("Selection is still at least partially in list, using")
          indices() = newIndices
          
        //Selection is completely missing from list, if just list has changed, use
        //first index to look up new selection
        } else if (cs == Set(list)) {
//          println("Selection not in list, using first index from " + i)
          useFirstIndex()
          
        //Otherwise just use default
        } else {
//          println("Defaulting")
          default()
        }
      }
      
    })
    
    selected.retainReaction(r)
    indices.retainReaction(r)
    
    new ListIndicesDefault(selected, indices): ListIndices[T]
  }
}