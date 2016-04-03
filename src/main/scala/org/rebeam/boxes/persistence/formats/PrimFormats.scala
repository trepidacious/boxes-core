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
  }
  implicit val intFormat = new Format[Int] {
    def write(s: Int) = put(IntToken(s))
    def read = pullInt
    def replace(s: Int, boxId: Long) = nothing
  }
  implicit val longFormat = new Format[Long] {
    def write(s: Long) = put(LongToken(s))
    def read = pullLong
    def replace(s: Long, boxId: Long) = nothing
  }
  implicit val floatFormat = new Format[Float] {
    def write(s: Float) = put(FloatToken(s))
    def read = pullFloat
    def replace(s: Float, boxId: Long) = nothing
  }
  implicit val doubleFormat = new Format[Double] {
    def write(s: Double) = put(DoubleToken(s))
    def read = pullDouble
    def replace(s: Double, boxId: Long) = nothing
  }
  implicit val bigIntFormat = new Format[BigInt] {
    def write(n: BigInt) = put(BigIntToken(n))
    def read = pullBigInt
    def replace(s: BigInt, boxId: Long) = nothing
  }
  implicit val bigDecimalFormat = new Format[BigDecimal] {
    def write(n: BigDecimal) = put(BigDecimalToken(n))
    def read = pullBigDecimal
    def replace(s: BigDecimal, boxId: Long) = nothing
  }
  implicit val stringFormat = new Format[String] {
    def write(s: String) = put(StringToken(s))
    def read = pullString
    def replace(s: String, boxId: Long) = nothing
  }
}
