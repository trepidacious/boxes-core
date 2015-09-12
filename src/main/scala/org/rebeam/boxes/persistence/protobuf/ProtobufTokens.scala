package org.rebeam.boxes.persistence.protobuf

import org.rebeam.boxes.persistence._

import java.io.{IOException, InputStream, OutputStream}

import com.google.protobuf.{CodedInputStream, CodedOutputStream}

object ProtobufTokens {

  val openDict          = 1
  val dictEntry         = 2
  val closeDict         = 3

  val booleanToken      = 4
  val intToken          = 5
  val longToken         = 6
  val floatToken        = 7
  val doubleToken       = 8
  val stringToken       = 9
  val bigIntToken       = 10
  val bigDecimalToken   = 11

  val openArr           = 12
  val closeArr          = 13

  val boxToken          = 14

  val noneToken         = 15

  val linkRef           = 16
  val linkId            = 17
  val linkEmpty         = 18

  val noName            = 19
  val presentationName  = 20
  val significantName   = 21
}

object ProtobufTokenWriter {
  def apply(os: OutputStream) =
    new ProtobufTokenWriter(CodedOutputStream.newInstance(os), () => {os.flush();os.close()})
}

object ProtobufTokenReader{
  def apply(is: InputStream) = new ProtobufTokenReader(CodedInputStream.newInstance(is), () => is.close())
}

class ProtobufTokenReader(is: CodedInputStream, onClose: => Unit) extends TokenReader {

  private var nextToken: Option[Token] = None

  def peek = nextToken.getOrElse{
      val t = pullToken()
      nextToken = Some(t)
      t
    }

  def pull() = {
    val t = peek
    nextToken = None
    t
  }

  private def pullToken() : Token = {

    def readName() = is.readRawVarint32() match {
      case ProtobufTokens.noName => NoName
      case ProtobufTokens.presentationName => PresentationName(is.readString())
      case ProtobufTokens.significantName => SignificantName(is.readString())
      case _ => throw new IOException("Invalid name type")
    }

    def readLink() = is.readRawVarint32() match {
      case ProtobufTokens.linkRef => LinkRef(is.readRawVarint64())
      case ProtobufTokens.linkId => LinkId(is.readRawVarint64())
      case ProtobufTokens.linkEmpty => LinkEmpty
      case _ => throw new IOException("Invalid link type")
    }

    is.readRawVarint32() match {
      case ProtobufTokens.openDict => OpenDict(readName(), readLink())
      case ProtobufTokens.dictEntry => DictEntry(is.readString(), readLink())
      case ProtobufTokens.closeDict => CloseDict

      case ProtobufTokens.booleanToken => BooleanToken(is.readBool())
      case ProtobufTokens.intToken => IntToken(is.readRawVarint32())
      case ProtobufTokens.longToken => LongToken(is.readRawVarint64())
      case ProtobufTokens.floatToken => FloatToken(is.readFloat())
      case ProtobufTokens.doubleToken => DoubleToken(is.readDouble())
      case ProtobufTokens.stringToken => StringToken(is.readString())

      case ProtobufTokens.bigIntToken => BigIntToken(BigInt(is.readByteArray()))
      case ProtobufTokens.bigDecimalToken =>
        val unscaledValue = BigInt(is.readByteArray())
        val scale = is.readRawVarint32()
        BigDecimalToken(BigDecimal(unscaledValue, scale))

      case ProtobufTokens.openArr => OpenArr(readName())
      case ProtobufTokens.closeArr => CloseArr

      case ProtobufTokens.boxToken => BoxToken(readLink())

      case ProtobufTokens.noneToken => NoneToken

      case x => throw new IOException("Invalid token type " + x)
    }
  }

  def close() = onClose
}

class ProtobufTokenWriter(os: CodedOutputStream, onClose: => Unit) extends TokenWriter {

  def write(t: Token) {
    import ProtobufTokens._

    def writeLink(link: Link) = link match {
      case LinkEmpty => os.writeRawVarint32(linkEmpty)
      case LinkRef(id) =>
        os.writeRawVarint32(linkRef)
        os.writeRawVarint64(id)
      case LinkId(id) =>
        os.writeRawVarint32(linkId)
        os.writeRawVarint64(id)
    }

    def writeName(name: TokenName) = name match {
      case NoName => os.writeRawVarint32(noName)
      case PresentationName(nameString) =>
        os.writeRawVarint32(presentationName)
        os.writeStringNoTag(nameString)
      case SignificantName(nameString) =>
        os.writeRawVarint32(significantName)
        os.writeStringNoTag(nameString)
    }

    t match {

      case OpenDict(name, link) =>
        os.writeRawVarint32(openDict)
        writeName(name)
        writeLink(link)

      case DictEntry(name, link) =>
        os.writeRawVarint32(dictEntry)
        os.writeStringNoTag(name)
        writeLink(link)

      case CloseDict => os.writeRawVarint32(closeDict)

      case BooleanToken(p) =>
        os.writeRawVarint32(booleanToken)
        os.writeBoolNoTag(p)

      case IntToken(p) =>
        os.writeRawVarint32(intToken)
        os.writeRawVarint32(p)

      case LongToken(p) =>
        os.writeRawVarint32(longToken)
        os.writeRawVarint64(p)

      case FloatToken(p) =>
        os.writeRawVarint32(floatToken)
        os.writeFloatNoTag(p)

      case DoubleToken(p) =>
        os.writeRawVarint32(doubleToken)
        os.writeDoubleNoTag(p)

      case BigIntToken(p) =>
        os.writeRawVarint32(bigIntToken)
        os.writeByteArrayNoTag(p.toByteArray)

      case BigDecimalToken(p) =>
        os.writeRawVarint32(bigDecimalToken)
        os.writeByteArrayNoTag(p.bigDecimal.unscaledValue().toByteArray)
        os.writeRawVarint32(p.bigDecimal.scale())

      case StringToken(p) =>
        os.writeRawVarint32(stringToken)
        os.writeStringNoTag(p)

      case OpenArr(name) =>
        os.writeRawVarint32(openArr)
        writeName(name)

      case CloseArr => os.writeRawVarint32(closeArr)

      case BoxToken(link) =>
        os.writeRawVarint32(boxToken)
        writeLink(link)

      case NoneToken => os.writeRawVarint32(noneToken)
    }
  }

  def close(): Unit = {
    os.flush()
    onClose
  }
}

object ProtobufReaderWriterFactory extends ReaderWriterFactory {
  def reader(input:InputStream) = ProtobufTokenReader(input)
  def writer(output:OutputStream) = ProtobufTokenWriter(output)
}

object ProtobufIO extends IO(ProtobufReaderWriterFactory)
