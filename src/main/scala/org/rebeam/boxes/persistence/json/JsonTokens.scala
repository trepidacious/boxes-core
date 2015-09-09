package org.rebeam.boxes.persistence.json

import java.io._

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._

import scala.collection.mutable.Stack
import scala.util.Try

object JsonTokenWriter {
  def apply(writer: Writer, pretty: Boolean = false): TokenWriter = new JsonTokenWriter(writer, pretty)
}

class JsonTokenWriter(writer: Writer, pretty: Boolean = false) extends TokenWriter {

  private val tokens = Stack[Token]()
  private var previousToken:Option[Token] = None

  private def isReasonableBigDecimal(d: BigDecimal) = Try{BigDecimal(d.toString)}.isSuccess

  //We need to output a comma before a Prim, OpenDict, OpenArr or DictEntry, IFF we
  //have an array open, and our previous token is not the actual OpenArr (which
  //means we are the first entry in the Arr), or when we have a dict open, and
  //our previous token was not the actual OpenDict or a DictEntry.
  private def commaNeeded = {
    tokens.headOption match {
      case Some(OpenArr(name)) => previousToken != Some(OpenArr(name))
      case Some(OpenDict(name, link)) => previousToken match {
          case Some(OpenDict(_, _)) => false
          case Some(DictEntry(_, _)) => false
          case _ => true
        }
      case _ => false
    }
  }

  private def commaIfNeeded() = if (commaNeeded) println(",")

  private def printPrim[P](p:P) {
    commaIfNeeded()
    print("" + p)
  }

  private def quoted(s: String): String = JsonUtils.quote(s)

  private def print(s:String): Unit = writer.write(s)

  private def println(s:String): Unit = {
    writer.write(s)
    println()
  }

  private def println() = if (pretty) {
    print("\n")
    Range(0, tokens.count(t => t match {
      case OpenDict(_,_) => true
      case OpenArr(_) => true
      case _ => false
    })).foreach{i => writer.write("  ")}
  }

  private def prettySpace = if (pretty) " " else ""

  def write(t: Token): Unit = {
    t match {
      case OpenDict(name, link) =>
        commaIfNeeded()
        tokens.push(t)
        println("{")
        name match {
          case SignificantName(_) => throw new IncorrectTokenException("Cannot use OpenDict with " + name + " in json output")
          case _ => {}
        }
        link match {
          case LinkEmpty => {}
          case _ => throw new IncorrectTokenException("Cannot use OpenDict with " + link + " in json output")
        }

      case DictEntry(name, link) =>
        commaIfNeeded()
        link match {
          case LinkEmpty => {}
          case LinkId(id) =>
            println()
            print(quoted("_" + name + "_id") + ":" + prettySpace + id + ",")
          case LinkRef(id) => new IncorrectTokenException("Cannot use DictEntry with LinkRef in json output, found ref to " + id)
        }
        print(quoted(name) + ":" + prettySpace)

      case CloseDict =>
        tokens.pop() match {
          case OpenDict(name, link) => {
            println()
            print("}")
          }
          case _ => throw new RuntimeException("Mismatched CloseObj token")
        }

      //All primitives can be represented directly in Json. Note that numeric types
      //are distinguished by location
      case BooleanToken(p)   => printPrim(p)
      case IntToken(p)    => printPrim(p)
      case DoubleToken(p)    => printPrim(p)
      case StringToken(p)   => printPrim(quoted(p))
      case LongToken(p)    => printPrim(p)
      case FloatToken(p)    => printPrim(p)
      case BigIntToken(p)    => printPrim(p)
      case BigDecimalToken(p)    => if (isReasonableBigDecimal(p)) printPrim(p) else throw new IncorrectTokenException("Cannot write BigDecimal(" + p + ") since it will not parse when read back")

      case OpenArr(name) =>
        name match {
          case SignificantName(_) => throw new IncorrectTokenException("Cannot use " + name + " in json output")
          case _ => {}
        }
        commaIfNeeded()
        print("[")
        tokens.push(t)
        println()

      case CloseArr =>
        tokens.pop() match {
          case OpenArr(_) =>
            println()
            print("]")

          case _ => throw new RuntimeException("Mismatched CloseArr token")
        }

      case BoxToken(link) => throw new IncorrectTokenException("Cannot use " + t + " in json output")

      case NoneToken => {
        print("null")
      }

    }
    previousToken = Some(t)
  }

  def close() {
    writer.flush
    writer.close
  }

}

object JsonTokenReader {
  def apply(reader: Reader): TokenReader = new JsonTokenReader(reader)
}

/**
 * Read tokens from a Reader, expecting JSON format string data
 *
 * Note that there are some considerations for handling JSON data properly:
 * JSON uses arbitrary precision decimals for all numbers. Whole numbers and non-whole numbers are not distinguished.
 * At least by convention (if not explicitly in the specification), strings for numbers with the same mathematical
 * value are considered to be equal, so "1" and "1.0" are equivalent. As a result of this, we will tolerate values
 * with decimal places where whole number types are expected (i.e. BigInt, Int or Long) - otherwise we would have to distinguish
 * between "1" and "1.0", and this does not seem to be in the spirit of JSON. For example see the note at
 * http://spacetelescope.github.io/understanding-json-schema/reference/numeric.html
 *
 *     JavaScript (and thus also JSON) does not have distinct types for integers and floating-point values.
 *     Therefore, JSON Schema can not use type alone to distinguish between integers and non-integers.
 *     The JSON Schema specification recommends, but does not require, that validators use the mathematical
 *     value to determine whether a number is an integer, and not the type alone.
 *
 * As a result of this, we will freely convert from any decimal value that is a whole number into whole number types,
 * provided that the value is within the range for the type. Equally we will convert from any JSON value with no decimal
 * place to floating point/decimal types (i.e. BigDecimal, Float or Double) providing the value is in range.
 *
 * Since JSON uses an arbitrary precision decimal representation of numbers, it may not be possible to convert from
 * this string into an exact Float or Double value (although BigDecimals can nearly always be produced - we don't
 * worry about running out of range on the 32 bit scale!). Therefore when producing Float or Double values, we just
 * check that the value is within range, and round the value to Float or Double precision - note that this may involve
 * losing precision even on whole numbers.
 * Stricter behaviour is technically possible, e.g. using isExactFloat, isBinaryFloat or isDecimalFloat
 * (or Double equivalents) - if such precision is important, you should probably be using BigDecimal anyway.
 *
 * @param reader  The reader providing JSON formatted string data
 */
class JsonTokenReader(reader: Reader) extends TokenReader {

  private val parser = JsonParser(reader)

  private var nextToken: Option[Token] = None

  override def peek: Token = {
    nextToken.getOrElse{
      val t = pullToken()
      nextToken = Some(t)
      t
    }
  }

  override def pull(): Token = {
    val t = peek
    nextToken = None
    t
  }

  @throws [IncorrectTokenException]
  override def pullInt(): Int = {
    val t = pull()
    t match {
      case BigIntToken(n) if n.isValidInt => n.toInt
      case BigDecimalToken(n) if n.isValidInt => n.toIntExact
      case _ => throw new IncorrectTokenException("Pulling an IntToken, got " + t)
    }

  }
  @throws [IncorrectTokenException]
  override def pullLong(): Long = {
    val t = pull()
    t match {
      case BigIntToken(n) if n.isValidLong => n.toLong
      case BigDecimalToken(n) if n.isValidLong => n.toLongExact
      case _ => throw new IncorrectTokenException("Expected a LongToken, got " + t)
    }
  }

  //The range we can represent as a Float without truncation
  private val minFloatAsBigDecimal = BigDecimal.decimal(Float.MinValue)
  private val maxFloatAsBigDecimal = BigDecimal.decimal(Float.MaxValue)

  private def bigDecimalToFloat(n: BigDecimal) = {
    if (n >= minFloatAsBigDecimal && n <= maxFloatAsBigDecimal) {
      Some(n.toFloat)
    } else {
      None
    }
  }

  //The range we can represent as a Double without truncation
  private val minDoubleAsBigDecimal = BigDecimal(Double.MinValue)
  private val maxDoubleAsBigDecimal = BigDecimal(Double.MaxValue)

  private def bigDecimalToDouble(n: BigDecimal) = {
    if (n >= minDoubleAsBigDecimal && n <= maxDoubleAsBigDecimal) {
      Some(n.toDouble)
    } else {
      None
    }
  }

  @throws [IncorrectTokenException]
  override def pullFloat(): Float = {
    val t = pull()
    t match {
      case BigIntToken(n) => bigDecimalToFloat(BigDecimal(n)).getOrElse(throw new IncorrectTokenException("Expected a FloatToken, got " + t + "(out of range)"))
      case BigDecimalToken(n) => bigDecimalToFloat(n).getOrElse(throw new IncorrectTokenException("Expected a FloatToken, got " + t + "(out of range)"))
      case _ => throw new IncorrectTokenException("Expected a FloatToken, got " + t)
    }
  }
  @throws [IncorrectTokenException]
  override def pullDouble(): Double = {
    val t = pull()
    t match {
      case BigIntToken(n) => bigDecimalToDouble(BigDecimal(n)).getOrElse(throw new IncorrectTokenException("Expected a DoubleToken, got " + t + "(out of range)"))
      case BigDecimalToken(n) => bigDecimalToDouble(n).getOrElse(throw new IncorrectTokenException("Expected a DoubleToken, got " + t + "(out of range)"))
      case _ => throw new IncorrectTokenException("Expected a DoubleToken, got " + t)
    }
  }
  @throws [IncorrectTokenException]
  override def pullBigInt(): BigInt = {
    val t = pull()
    t match {
      case BigIntToken(i) => i
      case BigDecimalToken(n) => n.toBigIntExact().getOrElse(throw new IncorrectTokenException("Expected a BigIntToken, got a BigDecimalToken with non-exact value " + n))
      case _ => throw new IncorrectTokenException("Expected a BigIntToken, got " + t)
    }
  }
  @throws [IncorrectTokenException]
  override def pullBigDecimal(): BigDecimal = {
    val t = pull()
    t match {
      case BigDecimalToken(n) => n
      case BigIntToken(i) => BigDecimal(i)
      case _ => throw new IncorrectTokenException("Expected a BigDecimalToken, got " + t)
    }
  }

  private def pullToken(): Token = {
    parser.pull match {
      case JsonParser.OpenObj => OpenDict()
      case JsonParser.CloseObj => CloseDict
      case JsonParser.FieldStart(name) => DictEntry(name)
      case JsonParser.End => throw new IncorrectTokenException("Reached end of Json data")
      case JsonParser.StringVal(value) => StringToken(value)
      case JsonParser.IntVal(value) => BigIntToken(value)
      case JsonParser.DoubleVal(value) => BigDecimalToken(value)
      case JsonParser.BoolVal(value) => BooleanToken(value)
      case JsonParser.NullVal => NoneToken
      case JsonParser.OpenArr => OpenArr(NoName)
      case JsonParser.CloseArr => CloseArr
    }
  }

  override def close() {
    reader.close()
  }
}

class JsonReaderWriterFactory(pretty: Boolean = false) extends ReaderWriterFactory {
  val charset = "UTF-8"
  def reader(input:InputStream) = JsonTokenReader(new InputStreamReader(input, charset))
  def writer(output:OutputStream) = JsonTokenWriter(new OutputStreamWriter(output, charset), pretty)
}

class JsonIO(pretty: Boolean = false) extends IO(new JsonReaderWriterFactory(pretty)) {
  def toJsonString[T :Writes](rev: Revision, t: T) = {
    val sw = new StringWriter()
    val w = new JsonTokenWriter(sw, pretty)
    BoxWriterScript.run(Writing.write(t), rev, w)
    sw.toString
  }

  // def fromJsonString[T: Reads](s: String)(implicit txn: Txn): T = {
  //   val sr = new StringReader(s)
  //   val r = new JsonTokenReader(sr)
  //   val context = ReadContext(r, txn)
  //   Reading.read[T](context)
  // }
}

object JsonIO extends JsonIO(false)
object JsonPrettyIO extends JsonIO(true)

//  def readDBO(dbo: Any) = {
//    val r = MongoTokens.toTokens(dbo, aliases)
//    //Decode, so we run as a transaction, AND reactions are handled properly
//    Box.decode {
//      val t = codecByClass.read(r)
//      r.close
//      t
//    }
//  }
//
//  def writeDBO(t: Any) = {
//    val w = new StoringTokenWriter
//    Box.transact {
//      codecByClass.write(t, w)
//      w.close()
//    }
//    MongoTokens.toDBO(new StoringTokenReader(w.tokens:_*), aliases)
//  }

