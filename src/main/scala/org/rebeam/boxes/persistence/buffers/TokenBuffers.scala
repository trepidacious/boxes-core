package org.rebeam.boxes.persistence.buffers

import org.rebeam.boxes.persistence._
import scala.collection.mutable.ListBuffer
import org.rebeam.boxes.core._

object BufferTokenWriter {
  def apply() = new BufferTokenWriter()
}

class BufferTokenWriter extends TokenWriter {
  private val buffer = ListBuffer[Token]()
  override def write(t: Token) = buffer.append(t)
  def tokens = buffer.toList
  def close() {}
}

object BufferTokenReader {
  def apply(tokens: List[Token]): BufferTokenReader = new BufferTokenReader(tokens)
}

class BufferTokenReader(tokens: List[Token]) extends TokenReader {
  private val buffer = ListBuffer(tokens: _*)

  def peek: Token = buffer.headOption.getOrElse(EndToken)
  def pull(): Token = if (buffer.isEmpty) {
    EndToken
  } else {
    buffer.remove(0)
  }
  def close() {}
  
  def remainingTokens: List[Token] = buffer.toList
  
}

object BufferIO {
  def toTokens[T :Writes](t: T) = {
    val w = BufferTokenWriter()
    Shelf.runWriter(Writing.write(t), w)
    w.tokens
  }

  def toReader[T: Writes](t: T) = BufferTokenReader(toTokens(t))

  def fromTokens[T: Reads](tokens: List[Token]): T = {
    val r = BufferTokenReader(tokens)
    Shelf.runReaderOrException(Reading.read[T], r)
  }
  
}
