package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._

object PrimFormats {
  import BoxReaderDeltaF._
  import BoxWriterDeltaF.put
  implicit val booleanFormat = new Format[Boolean] {
    def write(s: Boolean) = put(BooleanToken(s))
    def read = pullBoolean
    def replace(s: Boolean, boxId: Long) = nothing
    def modify(s: Boolean, boxId: Long) = nothing
    def modifyBox(s: Box[Boolean]) = nothing  //Use replace instead
  }
  implicit val intFormat = new Format[Int] {
    def write(s: Int) = put(IntToken(s))
    def read = pullInt
    def replace(s: Int, boxId: Long) = nothing
    def modify(s: Int, boxId: Long) = nothing
    def modifyBox(s: Box[Int]) = nothing  //Use replace instead
  }
  implicit val longFormat = new Format[Long] {
    def write(s: Long) = put(LongToken(s))
    def read = pullLong
    def replace(s: Long, boxId: Long) = nothing
    def modify(s: Long, boxId: Long) = nothing
    def modifyBox(s: Box[Long]) = nothing  //Use replace instead
  }
  implicit val floatFormat = new Format[Float] {
    def write(s: Float) = put(FloatToken(s))
    def read = pullFloat
    def replace(s: Float, boxId: Long) = nothing
    def modify(s: Float, boxId: Long) = nothing
    def modifyBox(s: Box[Float]) = nothing  //Use replace instead
  }
  implicit val doubleFormat = new Format[Double] {
    def write(s: Double) = put(DoubleToken(s))
    def read = pullDouble
    def replace(s: Double, boxId: Long) = nothing
    def modify(s: Double, boxId: Long) = nothing
    def modifyBox(s: Box[Double]) = nothing  //Use replace instead
  }
  implicit val bigIntFormat = new Format[BigInt] {
    def write(n: BigInt) = put(BigIntToken(n))
    def read = pullBigInt
    def replace(s: BigInt, boxId: Long) = nothing
    def modify(s: BigInt, boxId: Long) = nothing
    def modifyBox(s: Box[BigInt]) = nothing  //Use replace instead
  }
  implicit val bigDecimalFormat = new Format[BigDecimal] {
    def write(n: BigDecimal) = put(BigDecimalToken(n))
    def read = pullBigDecimal
    def replace(s: BigDecimal, boxId: Long) = nothing
    def modify(s: BigDecimal, boxId: Long) = nothing
    def modifyBox(s: Box[BigDecimal]) = nothing  //Use replace instead
  }
  implicit val stringFormat = new Format[String] {
    def write(s: String) = put(StringToken(s))
    def read = pullString
    def replace(s: String, boxId: Long) = nothing
    def modify(s: String, boxId: Long) = nothing
    def modifyBox(s: Box[String]) = nothing  //Use replace instead
  }
}
