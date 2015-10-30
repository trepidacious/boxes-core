package org.rebeam.boxes.core.data

import org.rebeam.boxes.core._
import BoxTypes._
import BoxUtils._
import BoxScriptImports._

//FIXME this stuff is all fairly awful - can maybe rewrite with shapeless

//An table like view that has some immutability.
//Will always return the same results for fieldName,
//fieldClass, recordCount, fieldCount.
//apply and editable may return different values, but
//only if they delegate to state held in Boxes, in much the
//same way as an immutable List that may hold mutable instances.
//Update allows for creating a new Ledger with modified contents,
//much like the copy constructor of a case class.
//Where the Ledger DOES delegate to mutable Boxes, it may
//actually perform the update in place, and in this case should
//return the same Ledger from update.
//In general this complies with the requirements for data
//in the Box system, that data is either immutable, or is accessed
//via a Box and so tracked for reads and writes.
trait Ledger {
  def apply(record: Int, field: Int): BoxScript[Any]
  def fieldName(field: Int): BoxScript[String]
  def fieldClass(field: Int): BoxScript[Class[_]]
  def recordCount(): BoxScript[Int]
  def fieldCount(): BoxScript[Int]
  
  def editable(record: Int, field: Int): BoxScript[Boolean]
  def updated(record: Int, field: Int, value: Any): BoxScript[Ledger]
}

//An immutable view of records of type T as a list of fields
trait RecordView[T] {
  def editable(record: Int, field: Int, recordValue: T): BoxScript[Boolean]
  def apply(record: Int, field: Int, recordValue: T): BoxScript[Any]
  def update(record: Int, field: Int, recordValue: T, fieldValue: Any): BoxScript[Unit]
  def fieldName(field: Int): BoxScript[String]
  def fieldClass(field: Int): BoxScript[Class[_]]
  def fieldCount(): BoxScript[Int]
}

/**
 * A Ledger that produces each record from one element of a list, using
 * a RecordView to convert that element to the fields of the record
 */
case class ListLedger[T](list: Seq[T], rView: RecordView[T]) extends Ledger {
  def apply(record: Int, field: Int) = rView(record, field, list(record))
  def fieldName(field: Int) = rView.fieldName(field)
  def fieldClass(field: Int) = rView.fieldClass(field)
  def recordCount() = just(list.size)
  def fieldCount()= rView.fieldCount
  
  def editable(record: Int, field: Int) = rView.editable(record, field, list(record))
  def updated(record: Int, field: Int, value: Any) = rView.update(record, field, list(record), value) andThen just(this)
}

/** 
 * Calculated Box that will always hold a ListLedger made from the 
 * current List and RecordView in the provided refs.
 */
object ListLedgerBox {
  def apply[T](list: BoxR[_ <: Seq[T]], rView: BoxR[RecordView[T]]): BoxScript[Box[Ledger]] = for {
    l <- list
    rv <- rView
    v <- create(ListLedger(l, rv): Ledger)
    //Note this will do nothing if list and view are the same, avoiding cycles
    r <- createReaction(for {
      l <- list
      rv <- rView
      _ <- v() = ListLedger(l, rv): Ledger
    } yield())
    _ <- v.attachReaction(r)
  } yield v
}

//case class FieldCompositeLedger(ledgers: Seq[Ledger]) extends Ledger {
//
//  def recordCount()(implicit txn: TxnR) = ledgers.foldLeft(ledgers.head.recordCount){(min, l) => math.min(l.recordCount, min)}
//  def fieldCount()(implicit txn: TxnR) = ledgers.foldLeft(0){(sum, l) => sum + l.fieldCount}
//  private def cumulativeFieldCount()(implicit txn: TxnR) = ledgers.scanLeft(0){(c, l) => c + l.fieldCount}.toList //Make cumulative field count, note starts with 0
//
//  private def ledgerAndFieldAndLedgerIndex(field: Int)(implicit txn: TxnR): (Ledger, Int, Int) = {
//    //Note that -1 is to allow for leading 0 in cumulativeFieldCount
//    val ledgerIndex = cumulativeFieldCount.indexWhere(c => c > field) - 1
//
//    //This happens if EITHER findIndexOf fails and returns -1, OR field is negative and so matches first entry in cumulativeFieldCount
//    if (ledgerIndex < 0) throw new IndexOutOfBoundsException("Field " + field + " is not in composite ledger")
//
//    (ledgers(ledgerIndex), field - cumulativeFieldCount.apply(ledgerIndex), ledgerIndex)
//  }
//
//  def apply(record: Int, field: Int)(implicit txn: TxnR) = {
//    val (l, f, _) = ledgerAndFieldAndLedgerIndex(field)
//    l.apply(record, f)
//  }
//
//  def fieldName(field: Int)(implicit txn: TxnR) = {
//    val (l, f, _) = ledgerAndFieldAndLedgerIndex(field)
//    l.fieldName(f)
//  }
//
//  def fieldClass(field: Int)(implicit txn: TxnR) = {
//    val (l, f, _) = ledgerAndFieldAndLedgerIndex(field)
//    l.fieldClass(f)
//  }
//
//  def editable(record: Int, field: Int)(implicit txn: TxnR): Boolean = {
//    val (l, f, _) = ledgerAndFieldAndLedgerIndex(field)
//    l.editable(record, f)
//  }
//
//  def updated(record: Int, field: Int, value:Any)(implicit txn: Txn) = {
//    val (l, f, li) = ledgerAndFieldAndLedgerIndex(field)
//    val newLedger = l.updated(record, f, value)
//    //Optimisation for ledgers that just update mutable data
//    //and return themselves - in that case we don't need to
//    //make a new FieldCompositeLedger, since it would just
//    //contain an equal list of ledgers anyway.
//    if (newLedger == l) {
//      this
//    } else {
//      val newList = ledgers.updated(li, newLedger)
//      FieldCompositeLedger(newList)
//    }
//  }
//
//}
//
///**
// * Calculated Box that will always hold a FieldCompositeListLedger made from the
// * Ledgers in the List in a Box
// */
//object FieldCompositeLedgerVar {
//  def apply[T](ledgers: Box[List[Ledger]])(implicit txn: Txn) = {
//    val v = Box(FieldCompositeLedger(ledgers()))
//    val ledgersReaction = txn.createReaction(implicit txnR => {
//      //Note this will do nothing if list and view are the same, avoiding cycles
//      v() = FieldCompositeLedger(ledgers())
//    })
//    v.retainReaction(ledgersReaction)
//    v
//  }
//}
//

object DirectRecordView{
  def apply[T](fieldName: String)(implicit valueManifest:Manifest[T]) = new DirectRecordView(fieldName)(valueManifest)
}


class DirectRecordView[T](fieldName: String)(implicit valueManifest: Manifest[T]) extends RecordView[T] {
  def editable(record: Int, field: Int, recordValue: T) = just(false)
  def apply(record: Int, field: Int, recordValue: T) = just(recordValue)
  def update(record: Int, field: Int, recordValue: T, fieldValue: Any) = nothing
  def fieldName(field: Int) = just(fieldName)
  def fieldClass(field: Int) = just(valueManifest.runtimeClass)
  def fieldCount() = just(1)
}

/**
 * Lens allowing reading of a "property" of a particular
 * data item within a Txn. Also associates a name and a class via a Manifest
 */
trait Lens[T, V] {
  def apply(t: T): BoxScript[V]
  def name: String
  def valueManifest: Manifest[V]
}

/**
 * Lens that also allows changing of the value of a property (mutation), within
 * a Txn
 */
trait MLens[T, V] extends Lens[T, V] {
  def update(t: T, v: V): BoxScript[Unit]
}

/**
 * MLens based on a BoxM and an access closure
 */
object MBoxLens {
  def apply[T, V](name: String, access: T => BoxM[V])(implicit valueManifest:Manifest[V]) = {
    new MLensDefault[T, V](
      name,
      t => access(t).read,
      (t, v) => access(t).write(v)
    )(valueManifest)
  }
}

/**
 * Lens based on a Box and an access closure
 */
object BoxLens {
  def apply[T, V](name: String, access: (T => BoxR[V]))(implicit valueManifest:Manifest[V]) = {
    new LensDefault[T, V](
      name,
      t => access(t)
    )(valueManifest)
  }
}

class LensDefault[T, V](val name:String, val read: T => BoxScript[V])(implicit val valueManifest:Manifest[V]) extends Lens[T, V] {
  def apply(t:T) = read(t)
}

class MLensDefault[T, V](val name:String, val read: T => BoxScript[V], val write: (T, V) => BoxScript[Unit])(implicit val valueManifest:Manifest[V]) extends MLens[T, V] {
  def apply(t:T) = read(t)
  def update(t:T, v:V) = write(t, v)
}

object LensRecordView {
  def apply[T](lenses: Lens[T,_]*) = new LensRecordView[T](lenses: _*)
}

class LensRecordView[T](lenses: Lens[T,_]*) extends RecordView[T] {

  //Note that in a RecordView with mutability, we would need to call Box methods,
  //but this view itself is immutable - the records may be mutable, but this is
  //irrelevant

  override def editable(record: Int, field: Int, recordValue: T) = just(lenses(field).isInstanceOf[MLens[_,_]])
  override def apply(record: Int, field: Int, recordValue: T) = lenses(field).asInstanceOf[Lens[T, Any]].apply(recordValue)

  override def update(record:Int, field:Int, recordValue:T, fieldValue:Any) = {
    lenses(field) match {
      case mLens:MLens[_,_] =>
        fieldValue match {

          //TODO there HAS to be a better way to do this. The problem is that the AnyVals don't have getClass, so
          //we need to match to get the class, then pass it through. At least there is a known, fixed set of classes
          //here, and we know they must match the manifest exactly
          case v:Boolean => tryUpdate(mLens, recordValue, fieldValue, classOf[Boolean])
          case v:Byte => tryUpdate(mLens, recordValue, fieldValue, classOf[Byte])
          case v:Char => tryUpdate(mLens, recordValue, fieldValue, classOf[Char])
          case v:Double => tryUpdate(mLens, recordValue, fieldValue, classOf[Double])
          case v:Long => tryUpdate(mLens, recordValue, fieldValue, classOf[Long])
          case v:Int => tryUpdate(mLens, recordValue, fieldValue, classOf[Int])
          case v:Short => tryUpdate(mLens, recordValue, fieldValue, classOf[Short])

          //Now we have an AnyRef, it is much easier
          case fieldValueRef:AnyRef =>
            if(!mLens.valueManifest.typeArguments.isEmpty) {
              throw new RuntimeException("Can only use MLens in LensRecordView for non-generic types")
            } else if (!mLens.valueManifest.runtimeClass.isAssignableFrom(fieldValueRef.getClass)) {
              throw new RuntimeException("Invalid value, expected a " + mLens.valueManifest.runtimeClass + " but got a " + fieldValueRef.getClass)
            } else {
              mLens.asInstanceOf[MLens[Any, Any]].update(recordValue, fieldValueRef)
            }

          case _ => throw new RuntimeException("Can't handle fieldValue " + fieldValue)
        }

      case _ => throw new RuntimeException("Code error - not a MLens for field " + field + ", but tried to update anyway")
    }
  }

  private def tryUpdate(mLens:MLens[_,_], recordValue:T, fieldValue:Any, c:Class[_]) = {
    if (mLens.valueManifest.runtimeClass == c) {
      mLens.asInstanceOf[MLens[Any, Any]].update(recordValue, fieldValue)
    } else {
      throw new RuntimeException("Invalid value, expected a " + mLens.valueManifest.runtimeClass + " but got a " + c)
    }
  }

  override def fieldName(field:Int) = just(lenses(field).name)
  override def fieldClass(field:Int) = just(lenses(field).valueManifest.runtimeClass)
  override def fieldCount() = just(lenses.size)

}

