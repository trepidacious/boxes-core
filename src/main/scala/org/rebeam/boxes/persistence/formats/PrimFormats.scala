package org.rebeam.boxes.persistence.formats

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._

object PrimFormats {
  import BoxReaderDeltaF._
  import BoxWriterDeltaF.put
  implicit val booleanFormat = new Format[Boolean] {
    def write(s: Boolean) = put(BooleanToken(s))
    def read = pullBoolean
  }
  implicit val intFormat = new Format[Int] {
    def write(s: Int) = put(IntToken(s))
    def read = pullInt
  }
  implicit val longFormat = new Format[Long] {
    def write(s: Long) = put(LongToken(s))
    def read = pullLong
  }
  implicit val floatFormat = new Format[Float] {
    def write(s: Float) = put(FloatToken(s))
    def read = pullFloat
  }
  implicit val doubleFormat = new Format[Double] {
    def write(s: Double) = put(DoubleToken(s))
    def read = pullDouble
  }
  implicit val bigIntFormat = new Format[BigInt] {
    def write(n: BigInt) = put(BigIntToken(n))
    def read = pullBigInt
  }
  implicit val bigDecimalFormat = new Format[BigDecimal] {
    def write(n: BigDecimal) = put(BigDecimalToken(n))
    def read = pullBigDecimal
  }
  implicit val stringFormat = new Format[String] {
    def write(s: String) = put(StringToken(s))
    def read = pullString
  }
}
