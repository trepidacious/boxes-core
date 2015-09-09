package org.rebeam.boxes.persistence

import org.rebeam.boxes.core._
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

  def write[T: Writes](t: T, output: OutputStream) = {
    val script = implicitly[Writes[T]].write(t)
    val writer = factory.writer(output)
    try {
      Shelf.runWriter(script, writer)
    } finally {
      writer.close()
    }
  }

  def read[T: Reads](input:InputStream) = {
    val script = implicitly[Reads[T]].read
    val reader = factory.reader(input)
    try {
      Shelf.runReaderOrException(script, reader)
    } finally {
      reader.close()
    }
  }

}