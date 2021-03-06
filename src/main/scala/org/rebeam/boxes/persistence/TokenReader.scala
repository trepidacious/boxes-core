package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._

trait TokenReader {

  def peek: Token

  def pull(): Token

  @throws [IncorrectTokenException]
  def pullAndAssertEquals(t:Token) {
   val p = pull()
   if (p != t) throw new IncorrectTokenException("Expected " + t + ", got " + p)
  }

  @throws [IncorrectTokenException]
  def pullAndAssert(filter: Token => Boolean) {
   val p = pull()
   if (!filter(p)) throw new IncorrectTokenException("Assertion failed on " + p)
  }

  @throws [IncorrectTokenException]
  def pullBoolean(): Boolean = {
   val t = pull()
   t match {
     case BooleanToken(s) => s
     case _ => throw new IncorrectTokenException("Expected a BooleanToken, got " + t)
   }
  }
  @throws [IncorrectTokenException]
  def pullInt(): Int = {
   val t = pull()
   t match {
     case IntToken(s) => s
     case _ => throw new IncorrectTokenException("Expected an IntToken, got " + t)
   }
  }
  @throws [IncorrectTokenException]
  def pullLong(): Long = {
   val t = pull()
   t match {
     case LongToken(s) => s
     case _ => throw new IncorrectTokenException("Expected a LongToken, got " + t)
   }
  }
  @throws [IncorrectTokenException]
  def pullFloat(): Float = {
   val t = pull()
   t match {
     case FloatToken(s) => s
     case _ => throw new IncorrectTokenException("Expected a FloatToken, got " + t)
   }
  }
  @throws [IncorrectTokenException]
  def pullDouble(): Double = {
   val t = pull()
   t match {
     case DoubleToken(s) => s
     case _ => throw new IncorrectTokenException("Expected a DoubleToken, got " + t)
   }
  }
  @throws [IncorrectTokenException]
  def pullBigInt(): BigInt = {
   val t = pull()
   t match {
     case BigIntToken(i) => i
     case _ => throw new IncorrectTokenException("Expected a BigIntToken, got " + t)
   }
  }

  @throws [IncorrectTokenException]
  def pullBigDecimal(): BigDecimal = {
   val t = pull()
   t match {
     case BigDecimalToken(n) => n
     case _ => throw new IncorrectTokenException("Expected a BigDecimalToken, got " + t)
   }
  }
  @throws [IncorrectTokenException]
  def pullString(): String = {
   val t = pull()
   t match {
     case StringToken(s) => s
     case _ => throw new IncorrectTokenException("Expected a StringToken, got " + t)
   }
  }

  def close(): Unit
}