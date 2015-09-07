package org.rebeam.boxes.persistence

import org.rebeam.boxes.core.BoxTypes._
import java.io.{InputStream, OutputStream}

object Writing {
  def write[T](obj: T)(implicit writes: Writes[T]): BoxWriterScript[Unit] = writes.write(obj)
}
object Reading {
  def read[T]()(implicit reads: Reads[T]): BoxReaderScript[T] = reads.read
}

trait ReaderWriterFactory {
  def reader(input: InputStream): TokenReader
  def writer(output: OutputStream): TokenWriter
}

class IO(val factory: ReaderWriterFactory) {

  // def write[T: Writes](t: T, output: OutputStream) = {
  //   val target = factory.writer(output)
  //   implicitly[Writes[T]].write(t)
  //   target.close()
  // }

  // def read[T: Reads](input:InputStream)(implicit txn: Txn) = {
  //   val source = factory.reader(input)
  //   val context = ReadContext(source, txn)
  //   val t = implicitly[Reads[T]].read(context)
  //   source.close()
  //   t
  // }

  // def writeNow[T: Writes](t: T, output: OutputStream)(implicit shelf: Shelf) = shelf.read(implicit txn => write(t, output))

  // def readNow[T: Reads](input:InputStream)(implicit shelf: Shelf) = shelf.transact(implicit txn => read(input), ReactionBeforeCommit)

}