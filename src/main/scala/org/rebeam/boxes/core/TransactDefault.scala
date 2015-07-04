package org.rebeam.boxes.core

import java.util.concurrent.atomic.AtomicInteger

import org.rebeam.boxes.core.util._

import java.util.concurrent.{Executors, Executor}
import scala.collection.immutable._
import scala.collection.mutable.WeakHashMap
import scala.util.{Failure, Success, Try}

private class BoxDefault[T](val id: Long) extends Box[T] {
  /**
   * Store changes to this box, as a map from the Change to the State that was
   * set by that Change. Changes that form a revision are retained by RevisionDefault 
   * and used to look up States in refs.
   * Since this is a weak map, it does not retain Changes, they are retained only
   * by the revisions they are in.
   * When a BoxDefault is GCed, the changes are also GCed, allowing the States
   * of the Box (instances of T) to be removed.
   */
  private val changes = new WeakHashMap[Change, T]()
  
  private[core] def addChange(c: Change, t: T) = changes.put(c, t)
  private[core] def getValue(c: Change) = changes.get(c)
  
  override def toString = "BoxDefault id " + id + ", changes " + changes
}

private class Change(val revision: Long) {
  override def toString = "Change, revision " + revision
}

private object Change {
  def apply(revision: Long) = new Change(revision)
}

private object BoxDefault {
  private val nextId = new AtomicInteger(0)
  def apply[T](): Box[T] = new BoxDefault[T](nextId.getAndIncrement())
}

private class ReactionDefault(val id: Long) extends Reaction

private object ReactionDefault {
  private val nextId = new AtomicInteger(0)
  def apply() = new ReactionDefault(nextId.getAndIncrement())  
}

case class ReactionFunc(f: ReactorTxn => Unit) {
  def exec(txn: ReactorTxn) {
    f.apply(txn)
  }
}

private class RevisionDefault(val index: Long, val map: Map[Long, Change], reactionMap: Map[Long, ReactionFunc], val sources: BiMultiMap[Long, Long], val targets: BiMultiMap[Long, Long], val boxReactions: Map[Long, Set[Reaction]]) extends Revision {

  def stateOf[T](box: BoxR[T]): Option[State[T]] = for {
    change <- map.get(box.id)
    value <- box.asInstanceOf[BoxDefault[T]].getValue(change)
  } yield State(change.revision, value)
  
  def indexOf(box: BoxR[_]): Option[Long] = map.get(box.id).map(_.revision)
  
  def valueOf[T](box: BoxR[T]): Option[T] = {
    val change = map.get(box.id)
    change.flatMap(c => box.asInstanceOf[BoxDefault[T]].getValue(c))
  }

  def indexOfId(id: Long): Option[Long] = map.get(id).map(_.revision)

  def reactionOfId(id: Long): Option[ReactionFunc] = reactionMap.get(id)
  
  def updated(writes: Map[Box[_], _], deletes: List[Long], newReactions: Map[Reaction, ReactionFunc], reactionDeletes: List[Long], sources: BiMultiMap[Long, Long], targets: BiMultiMap[Long, Long], boxReactions: Map[Long, Set[Reaction]]) = {
    val newIndex = index + 1
    
    //Remove boxes that have been GCed, then add new ones
    val prunedMap = deletes.foldLeft(map){case (m, id) => m - id}
    
    val newMap = writes.foldLeft(prunedMap){case (m, (box, value)) =>
      val newChange = Change(newIndex)
      box.asInstanceOf[BoxDefault[Any]].addChange(newChange, value)
      m.updated(box.id, newChange)
    }
    
    //Remove reactions that have been GCed, then add new ones
    val prunedReactionMap = reactionDeletes.foldLeft(reactionMap){case (m, id) => m - id}
    val newReactionMap = newReactions.foldLeft(prunedReactionMap){case (m, (reaction, f)) => m.updated(reaction.id, f)}

    //Where boxes have been GCed, also remove the entry in boxReactions for that box - we only want the
    //boxReactions maps to retain reactions for revisions while the boxes are still reachable
    val prunedBoxReactions = deletes.foldLeft(boxReactions){case (m, id) => m - id}

    //Do not track sources and targets of removed reactions
    val prunedSources = sources.removedKeys(reactionDeletes.toSet)
    val prunedTargets = targets.removedKeys(reactionDeletes.toSet)
    
    new RevisionDefault(newIndex, newMap, newReactionMap, prunedSources, prunedTargets, prunedBoxReactions)
  } 

  def conflictsWith(t: TxnDefault) = {
    val start = t.revision.index
    t.reads.iterator.flatMap(indexOfId).exists(_>start) ||
    t.writes.keysIterator.flatMap(indexOf).exists(_>start)
  }
}


private class ShelfDefault extends Shelf {
  private val lock = RWLock()

  private var current = new RevisionDefault(0, Map.empty, Map.empty, BiMultiMap.empty, BiMultiMap.empty, Map.empty)

  private val retries = 10000
  
  private val watcher = new GCWatcher()
  private val reactionWatcher = new GCWatcher()
  
  private val views = new WeakHashSet[ViewDefault]()
  private val autos = new WeakHashSet[AutoDefault[_]]()
  
  def read[T](f: TxnR => T): T = f(new TxnRDefault(this, currentRevision))

//
//  def unview(v: View) = lock.write{
//    v match {
//      case vd: ViewDefault => views.remove(vd)
//      case _ => false
//    }
//  }
//
//  def auto[T](f: Txn => T) = auto(f, ShelfDefault.defaultExecutor, (t:T) => Unit)
//
//  def auto[T](f: Txn => T, exe: Executor = ShelfDefault.defaultExecutor, target: T => Unit = (t: T) => Unit): Auto = {
//    lock.write {
//      val auto = new AutoDefault(this, f, exe, target)
//      autos.add(auto)
//      auto.add(current)
//      auto
//    }
//  }
//
//  def unauto(a: Auto) = lock.write{
//    a match {
//      case ad: AutoDefault[_] => autos.remove(ad)
//      case _ => false
//    }
//  }

  def transactFromAuto[T](f: Txn => T): (T, TxnDefault) = {
    def tf(r: RevisionDefault) = new TxnDefault(this, r, ReactionImmediate)
    val result = transactRepeatedTry(f, tf, retries)
    (result._1, result._2)
  }

  def transact[T](f: Txn => T): T = transact(f, ReactionImmediate)
  
  def transact[T](f: Txn => T, p: ReactionPolicy): T = {
    def tf(r: RevisionDefault) = new TxnDefault(this, r, p)
    transactRepeatedTry(f, tf, retries)._1
  }

  def transactToRevision[T](f: Txn => T): (T, Revision) = transactToRevision(f, ReactionImmediate)
    
  def transactToRevision[T](f: Txn => T, p: ReactionPolicy): (T, Revision) = {
    def tf(r: RevisionDefault) = new TxnDefault(this, r, p)
    val result = transactRepeatedTry(f, tf, retries)
    (result._1, result._3)
  }

  def transactRepeatedTry[T, TT <: TxnDefault](f: Txn => T, tf: RevisionDefault => TT, retries: Int): (T, TT, Revision) = {
    Range(0, retries).view.map(_ => transactTry(f, tf)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))
  }
  
  private def transactTry[T, TT <: TxnDefault](f: Txn => T, transFactory: RevisionDefault => TT): Option[(T, TT, Revision)] = {
    val t = transFactory(currentRevision)
    val tryR = Try{
      val r = f(t)
      t.beforeCommit()
      r
    }
    
    //TODO note we could just lock long enough to get the current revision, and build the new map
    //outside the lock, then re-lock to attempt to make the new map the next revision, failing if
    //someone else got there first. This would make the write lock VERY brief, but potentially require
    //multiple rebuilds of the map before one "sticks"
    lock.write {
      
      tryR match {
        case Success(r) if !current.conflictsWith(t) =>
          //Watch new boxes, make new revision with GCed boxes deleted, and make the new updated revision
          watcher.watch(t.creates)
          reactionWatcher.watch(t.reactionCreates.keySet)
          val updated = current.updated(t.writes, watcher.deletes(), t.reactionCreates, reactionWatcher.deletes(), t.sources, t.targets, t.boxReactions)

          //Add new views and autos - they will get the update revision as their first revision
          for (view <- t.viewsToAdd) views.add(view)
          for (auto <- t.autosToAdd) autos.add(auto)

          //TODO there must be a neater way to handle the View/ViewDefault thing - maybe View should have an add method,
          //but then ViewDefault would have to accept Revision not RevisionDefault
          //Remove unwanted views and autos - they will not see this new revision
          for (view <- t.viewsToRemove) view match {case v: ViewDefault => views.remove(v)}
          for (auto <- t.autosToRemove) auto match {case a: AutoDefault[_] => autos.remove(a)}

          //Move to the new revision
          revise(updated)

          //Return the new revision
          Some((r, t, updated))

        case Failure(e: TxnEarlyFailException) => None  //Exception indicating early failure, e.g. due to conflict
        case Failure(e) => throw e                      //Exception that is not part of transaction system
        case _ => None                                  //Conflict
      }
    }
  }
  
  def currentRevision = lock.read { current }

  private def revise(updated: RevisionDefault) {
    current = updated
    
    //TODO this can be done outside the lock by just passing the new revision to a queue to be
    //consumed by another thread that actually updates views
    views.foreach(_.add(updated))
    autos.foreach(_.add(updated))
  }

}


object ShelfDefault {
  val defaultExecutorPoolSize = 8
  val defaultThreadFactory = DaemonThreadFactory()
  lazy val defaultExecutor: Executor = Executors.newFixedThreadPool(defaultExecutorPoolSize, defaultThreadFactory)

  def apply(): Shelf = new ShelfDefault
}


private class TxnRDefault(val shelf: ShelfDefault, val revision: RevisionDefault) extends TxnR {
  def get[T](box: BoxR[T]): T = revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box"))
}

private class TxnRLogging(val revision: RevisionDefault) extends TxnR {
  var reads = Set[Long]()
  def get[T](box: BoxR[T]): T = {
    val v = revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box " + box))
    reads = reads + box.id
    v
  }
}

private class TxnDefault(val shelf: ShelfDefault, val revision: RevisionDefault, val reactionPolicy: ReactionPolicy) extends TxnForReactor {
  
  var writes = Map[Box[_], Any]()
  var reads = Set[Long]()
  var creates = Set[Box[_]]()
  var reactionCreates = Map[Reaction, ReactionFunc]()
  var reactionIdCreates = Map[Long, ReactionFunc]()
  var sources = revision.sources
  var targets = revision.targets
  var boxReactions = revision.boxReactions
  
  var currentReactor: Option[ReactorDefault] = None

  var viewsToAdd = Set[ViewDefault]()
  var viewsToRemove = Set[View]()

  var autosToAdd = Set[AutoDefault[_]]()
  var autosToRemove = Set[Auto]()

  def view(f: TxnR => Unit): View = view(f, ShelfDefault.defaultExecutor, true)
  def view(f: TxnR => Unit, exe: Executor, onlyMostRecent: Boolean): View = {
    val v = new ViewDefault(shelf, f, exe, onlyMostRecent)
    viewsToAdd = viewsToAdd + v
    v
  }

  def unview(v: View) = viewsToRemove = viewsToRemove + v

  def auto[T](f: Txn => T): Auto = auto(f, ShelfDefault.defaultExecutor, (t:T) => Unit)
  def auto[T](f: Txn => T, exe: Executor, target: T => Unit): Auto = {
    val a = new AutoDefault[T](shelf, f, exe, target)
    autosToAdd = autosToAdd + a
    a
  }

  def unauto(a: Auto) = autosToRemove = autosToRemove + a

  def beforeCommit() {
    currentReactor.foreach(r=>{
      r.beforeCommit()
    })
  }
  
  def create[T](t: T): Box[T] = {
    val box = BoxDefault[T]()
    creates = creates + box
    writes = writes.updated(box, t)
    box
  }
  
  def withReactor[T](action: ReactorDefault => T): T = {
    currentReactor match {
      case Some(r) => action(r)
      case None =>
        val r = new ReactorDefault(this, reactionPolicy)
        currentReactor = Some(r)
        action(r)
    }
  }
  
  def reactionFinished() {
    currentReactor = None
  }


  def set[T](box: Box[T], t: T): Box[T] = {
    //If box value would not be changed, skip write
    if (_get(box) != t) {
      writes = writes.updated(box, t)
      withReactor(_.afterSet(box, t))
    }
    box
  }
  
  private def _get[T](box: BoxR[T]): T = writes.get(box.asInstanceOf[Box[_]]).asInstanceOf[Option[T]].getOrElse(revision.valueOf(box).getOrElse(
    throw new RuntimeException("Missing Box for id " + box.id)
  ))
  
  def get[T](box: BoxR[T]): T = {
    val v = _get(box)
    reads = reads + box.id
    //Only need to use a reactor if one is active
    currentReactor.foreach(_.afterGet(box))
    v
  }
  
  def createReaction(f: ReactorTxn => Unit): Reaction = {
    val reaction = ReactionDefault()
    val func = ReactionFunc(f)
    reactionCreates = reactionCreates.updated(reaction, func)
    reactionIdCreates = reactionIdCreates.updated(reaction.id, func)
    withReactor(_.registerReaction(reaction))
    reaction
  }
  
  def failEarly() = if (shelf.currentRevision.conflictsWith(this)) throw new TxnEarlyFailException
  
  override def boxRetainsReaction(box: BoxR[_], r: Reaction) {
    boxReactions = boxReactions.updated(box.id, boxReactions.getOrElse(box.id, Set.empty) + r)
  }

  override def boxReleasesReaction(box: BoxR[_], r: Reaction) {
    boxReactions = boxReactions.updated(box.id, boxReactions.getOrElse(box.id, Set.empty) - r)
  }
    
  def clearReactionSourcesAndTargets(rid: Long) {
    sources = sources.removedKey(rid)
    targets = targets.removedKey(rid)
  }
  
  def targetsOfReaction(rid: Long) = targets.valuesFor(rid)
  def sourcesOfReaction(rid: Long) = sources.valuesFor(rid)
  
  def reactionsTargettingBox(bid: Long) = targets.keysFor(bid)
  def reactionsSourcingBox(bid: Long) = sources.keysFor(bid)
  
  private def reactionFunctionForId(rid: Long): ReactionFunc = {
    //TODO we may get a missing reaction, if a reaction is GCed but is still pointed to by a box source/target
    reactionIdCreates.getOrElse(rid, revision.reactionOfId(rid).getOrElse(throw new RuntimeException("Missing Reaction")))
  }
  
  def react(rid: Long) = reactionFunctionForId(rid).exec(currentReactor.getOrElse(throw new RuntimeException("Missing Reactor")))
  
  def addTargetForReaction(rid: Long, bid: Long) = {
    targets = targets.updated(rid, targets.valuesFor(rid) + bid)
  }

  def addSourceForReaction(rid: Long, bid: Long) = {
    sources = sources.updated(rid, sources.valuesFor(rid) + bid)
  }

}

private class ViewDefault(val shelf: ShelfDefault, val f: TxnR => Unit, val exe: Executor, onlyMostRecent: Boolean = true) extends View {
  private val revisionQueue = new scala.collection.mutable.Queue[RevisionDefault]()
  private val lock = Lock()
  private var state: Option[(Long, Set[Long])] = None
  private var pending = false

  private def relevant(r: RevisionDefault) = {
    state match {
      case None => true
      case Some((index, reads)) => reads.iterator.flatMap(r.indexOfId).exists(_>index)
    }
  }
  
  private def go() {
    
    //If we have more revisions pending, try to run the next
    if (!revisionQueue.isEmpty) {
      val r = revisionQueue.dequeue()
      
      //If this revision is relevant (i.e. it has changes the view will read)
      //then run the transaction on it
      if (relevant(r)) {
        pending = true
        val t = new TxnRLogging(r)

        exe.execute(new Runnable() {
          override def run() = {
            //FIXME if this has an exception, it kills the View (i.e. it won't run on any future revisions).
            f(t)
            lock.run{
              state = Some((r.index, t.reads))
              go()
            }
          }
        })

      //If this revision is NOT relevant, try the next revision
      } else {
        go()
      }
      
    //If we have no more revisions, then stop for now
    } else {
      pending = false
    }
  }
  
  def add(r: RevisionDefault) {
    lock.run {
      if (onlyMostRecent) revisionQueue.clear()
      revisionQueue.enqueue(r)
      if (!pending) {
        go()
      }
    }
  }
}

private class AutoDefault[T](val shelf: ShelfDefault, val f: Txn => T, val exe: Executor, target: T => Unit = (t:T) => Unit) extends Auto {
  private val revisionQueue = new scala.collection.mutable.Queue[RevisionDefault]()
  private val lock = Lock()
  private var state: Option[(Long, Set[Long])] = None
  private var pending = false

  private def relevant(r: RevisionDefault) = {
    state match {
      case None => true
      case Some((index, reads)) => reads.iterator.flatMap(r.indexOfId).exists(_>index)
    }
  }
  
  private def go() {
    
    //If we have more revisions pending, try to run the next
    if (!revisionQueue.isEmpty) {
      val r = revisionQueue.dequeue()
      
      //If this revision is relevant (i.e. it has changes the transaction will read)
      //then run the transaction on it
      if (relevant(r)) {
        pending = true
        exe.execute(new Runnable() {
          override def run() = {
            val (result, t) = shelf.transactFromAuto(f)
            lock.run{
              state = Some((r.index, t.reads))
              go()
            }
            target(result)
          }
        })

      //If this revision is NOT relevant, try the next revision
      } else {
        go()
      }
      
    //If we have no more revisions, then stop for now
    } else {
      pending = false
    }
  }
  
  def add(r: RevisionDefault) {
    lock.run {
      revisionQueue.clear()
      revisionQueue.enqueue(r)
      if (!pending) {
        go()
      }
    }
  }
}
