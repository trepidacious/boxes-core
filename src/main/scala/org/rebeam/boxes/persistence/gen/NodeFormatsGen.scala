package org.rebeam.boxes.persistence.gen

object NodeFormatsGen {

  /**
   * Generate a nodeFormat method of arbitrary arity
   *
   * Uses a template with the following values:
   * $fieldCount The number of fields in the product, e.g. "2"
   * $fieldTypes The parametric types of the product fields, e.g. "P1: Format, P2: Format, ..."
   * $constructorParameters The parameters to construct a new instance of the node, e.g. "Box[P1], Box[P2], ..."
   * $productNameParameters The parameters for the names of each field of the product, e.g. "name1: String, name2: String, ..."
   * $writeDictEntries The code to write all fields as dict entries, e.g. "writeDictEntry[P1](n, name1, 0, c, boxLinkStrategy)\nwriteDictEntry[P2](n, name2, 1, c, boxLinkStrategy)\n ..."
   * $useDictEntriesCases The cases to read all fields by calling useDictEntry, e.g. "case s if s == name1 => useDictEntry[P1](n, 0, c, link)\ncase s if s == name2 => useDictEntry[P2](n, 1, c, link)\n ..."
   *
   * @param n The arity of the product
   */
  private def genNodeFormat(n: Int) = {
    val indices = Range(1, n + 1)

    def format(f: (Int) => String, sep: String) = indices.map(f(_)).mkString(sep)

    val fieldCount =            "" + n

    val fieldTypes =            format(i => s"P$i: Format", ", ")

    val constructorParameters = format(i => s"Box[P$i]", ", ")

    val productNameParameters = format(i => s"name$i: String", ", ")

    val writeDictEntries =      format(i => s"_ <- writeDictEntry[P$i](n, name$i, ${i - 1}, boxLinkStrategy)", "\n        ")

    val useDictEntriesCases =   format(i => s"case s if s == name$i => useDictEntry[P$i](n, ${i - 1}, link)", "\n              ")

    s"""
      |  def nodeFormat$fieldCount[$fieldTypes, N <: Product](construct: ($constructorParameters) => N, default: BoxScript[N])
      |      ($productNameParameters,
      |      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {
      |
      |    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      |      import BoxWriterDeltaF._
      |      for {
      |        $writeDictEntries
      |        _ <- put(CloseDict)
      |      } yield ()
      |    }
      |
      |    def readEntries(n: N): BoxReaderScript[Unit] = {
      |      import BoxReaderDeltaF._
      |      for {
      |        t <- peek
      |        _ <- if (t == CloseDict) {
      |          nothing
      |        } else {
      |          pull flatMap {
      |            case DictEntry(fieldName, link) => fieldName match {
      |              $useDictEntriesCases
      |              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
      |            }
      |            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
      |          } flatMap {_ => readEntries(n)}
      |        }
      |      } yield ()
      |    }
      |
      |    def readEntriesAndClose = {
      |      import BoxReaderDeltaF._
      |      for {
      |        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
      |        _ <- readEntries(n)
      |        _ <- pullExpected(CloseDict)
      |      } yield n
      |    }
      |
      |    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
      |    def read = readNode(readEntriesAndClose)
      |
      |  }
    """.stripMargin
  }

  def main(args: Array[String]) {
    for (i <- Range(1, 23)) println(genNodeFormat(i))
  }

}
