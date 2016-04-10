package org.rebeam.boxes.persistence.formats

import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

/**
 * These formats are very similar to ProductFormats, with the important difference that they expect to read/write
 * case classes (Products) of Boxes, which we call "Nodes" for example case class Person(name: Box[String], age: Box[Int]).
 * We refer to the Node's class as N below. Writes as a Dict with an entry for each box, containing the value of the box.
 * The link in the DictEntry itself is used to provide an id for the Box if required, BoxTokens are not used since they
 * are redundant. This means that a Node containing Boxes of types B1, B2 etc. will be serialised similarly to a Product
 * containing unboxed values of B1, B2 etc. Refs are never used.
 *
 * There are some additional requirements/features when using these formats:
 *  1. When reading, the formats always create the instance of N via a provided default method of type (Txn) => N,
 *  which is expected to create a "default" case class with entirely new boxes.
 *  2. When reading, it is acceptable for some fields to be missing - they are left as defaults. The reading process
 *  simply creates a default N, then sets any boxes that are present in the tokens. This allows for transparent upgrading
 *  of data from a previous version of the class with fewer fields.
 *  3. When writing, boxes are all required NOT to be in the cache when written - that is, they are not referenced anywhere
 *  in the token stream leading up to the moment of writing. This means that we can guarantee it is acceptable to create
 *  a new Box when reading again.
 *
 *  By requiring (and enforcing) that Nodes use their own boxes and these boxes are not used in any other Nodes, we can
 *  provide for transparent upgrading of nodes by using default values. In addition, the provided default method of type
 *  (Txn) => N can create any required reactions when the nodes are created, providing an easy means of handling
 *  serialisation and deserialisation of reactions.
 */
object NodeFormats {

  private def writeDictEntry[T: Format](n: Product, name: String, index: Int, linkStrategy: NoDuplicatesLinkStrategy) = {

    import BoxWriterDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]
    val id = box.id

    //NodeFormat will not use link references, but can use ids, for example for sharing box id with clients on network.
    //This is done by caching the box and using the id, but throwing an exception if we find the box is already cached.
    //This ensures that only one copy of the Box is in use in a Node, so that the Node technique of recreating Nodes
    //from default with new boxes is valid. Note that we don't really care if other Formats end up referencing our
    //Boxes later - we add them to cache when reading.
    for {
      cr <- cacheBox(box)
      _ <- if (cr.isCached) {
        throw new BoxCacheException("Box id " + id + " was already cached, but NodeFormats doesn't work with multiply-referenced Boxes")
      } else {
        val link = linkStrategy match {
          case IdLinks => LinkId(id)
          case EmptyLinks => LinkEmpty
        }

        for {
          //Open an entry for the Box
          _ <- put(DictEntry(name, link))

          //Cache the box whether we used LinkId or LinkEmpty - we use this to avoid duplicates in either case, and for possible references outside nodes
          _ <- cacheBox(box)
          v <- get(box) //Note we can't use box.get here since it returned BoxScript. Should maybe use implicits for this
          _ <- implicitly[Format[T]].write(v)
        } yield ()
      }

    } yield ()

  }

  private def useDictEntry[T :Format](n: Product, index: Int, link: Link) = {
    import BoxReaderDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]

    for {
      //We accept LinkEmpty, with nothing to do, or LinkId, in which case we putBox in case of any later references.
      //We do NOT accept LinkRef, since we never write one.
      _ <- link match {
        case LinkEmpty => nothing                    //No cache stuff to do
        case LinkId(id) => putCachedBox(id, box)     //Cache our box for anything using it later in stream
        case LinkRef(id) => throw new IncorrectTokenException("DictEntry must NOT have a LinkRef in a Node Dict, found ref to " + id)
      }
      v <- implicitly[Format[T]].read
      _ <- set(box, v)
    } yield ()
  }

  private def writeNode[N](n: N, name: TokenName, nodeLinkStrategy: LinkStrategy, writeEntriesAndClose: N => BoxWriterScript[Unit]) = {

    import BoxWriterDeltaF._

    nodeLinkStrategy match {
      case AllLinks =>
        cache(n) flatMap {
          case Cached(id) => put(OpenDict(name, LinkRef(id)))
          case New(id) => put (OpenDict(name, LinkId(id))) flatMap (_ => writeEntriesAndClose(n))
        }

      case IdLinks =>
        cache(n) flatMap {
          case Cached(id) => throw new NodeCacheException("Node " + n + " was already cached, but nodeLinkStrategy is " + nodeLinkStrategy)
          case New(id) => put(OpenDict(name, LinkId(id))) flatMap (_ => writeEntriesAndClose(n))
        }

      case EmptyLinks =>
        cache(n) flatMap {
          case Cached(id) => throw new NodeCacheException("Node " + n + " was already cached, but nodeLinkStrategy is " + nodeLinkStrategy)
          case New(id) => put (OpenDict(name, LinkEmpty)) flatMap (_ => writeEntriesAndClose(n))
        }
    }
  }

  private def readNode[N](readEntriesAndClose: BoxReaderScript[N]) = {
    import BoxReaderDeltaF._

    pull flatMap {
      case OpenDict(name, LinkEmpty) => readEntriesAndClose
      case OpenDict(name, LinkRef(id)) => getCached(id) map (_.asInstanceOf[N])
      case OpenDict(name, LinkId(id)) => for {
        n <- readEntriesAndClose
        _ <- putCached(id, n)
      } yield n
      case _ => throw new IncorrectTokenException("Expected OpenDict at start of Map[String, _]")
    }
  }

  private def replaceField[T](n: Product, index: Int, boxId: Long)(implicit f: Format[T]) = {
    import BoxReaderDeltaF._
  
    val box = n.productElement(index).asInstanceOf[Box[T]]
  
    //If this is our box, read a new value for it from tokens, set that new 
    //value and we are done
    if (box.id == boxId) {
      for {
        t <- peek
        //If we have some data to read, read it and use values
        _ <- if (t != EndToken) {
          for {
            newT <- f.read
            _ <- set(box, newT)
          } yield ()
          
        //There is no data left, so nothing to do - just return immediately
        } else {
          nothing            
        }
      } yield ()
      
    //If this is not our box, recurse to its contents
    } else {
      for {
        t <- get(box)
        _ <- f.replace(t, boxId)
      } yield ()
    }
  }

  // ############################################################
  // ############################################################
  // ##  Auto generated code for each different arity of Node  ##
  // ############################################################
  // ############################################################

  def nodeFormat1[P1: Format, N <: Product](construct: (Box[P1]) => N, default: BoxScript[N])
      (name1: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
    } yield ()

  }
    

  def nodeFormat2[P1: Format, P2: Format, N <: Product](construct: (Box[P1], Box[P2]) => N, default: BoxScript[N])
      (name1: String, name2: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
    } yield ()

  }
    

  def nodeFormat3[P1: Format, P2: Format, P3: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
    } yield ()

  }
    

  def nodeFormat4[P1: Format, P2: Format, P3: Format, P4: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
    } yield ()

  }
    

  def nodeFormat5[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
    } yield ()

  }
    

  def nodeFormat6[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
    } yield ()

  }
    

  def nodeFormat7[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
    } yield ()

  }
    

  def nodeFormat8[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
    } yield ()

  }
    

  def nodeFormat9[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
    } yield ()

  }
    

  def nodeFormat10[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
    } yield ()

  }
    

  def nodeFormat11[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
    } yield ()

  }
    

  def nodeFormat12[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
    } yield ()

  }
    

  def nodeFormat13[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
    } yield ()

  }
    

  def nodeFormat14[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
    } yield ()

  }
    

  def nodeFormat15[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
    } yield ()

  }
    

  def nodeFormat16[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
    } yield ()

  }
    

  def nodeFormat17[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16], Box[P17]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- writeDictEntry[P17](n, name17, 16, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case s if s == name17 => useDictEntry[P17](n, 16, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
      _ <- replaceField[P17](n, 16, boxId)
    } yield ()

  }
    

  def nodeFormat18[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16], Box[P17], Box[P18]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- writeDictEntry[P17](n, name17, 16, boxLinkStrategy)
        _ <- writeDictEntry[P18](n, name18, 17, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case s if s == name17 => useDictEntry[P17](n, 16, link)
              case s if s == name18 => useDictEntry[P18](n, 17, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
      _ <- replaceField[P17](n, 16, boxId)
      _ <- replaceField[P18](n, 17, boxId)
    } yield ()

  }
    

  def nodeFormat19[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16], Box[P17], Box[P18], Box[P19]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- writeDictEntry[P17](n, name17, 16, boxLinkStrategy)
        _ <- writeDictEntry[P18](n, name18, 17, boxLinkStrategy)
        _ <- writeDictEntry[P19](n, name19, 18, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case s if s == name17 => useDictEntry[P17](n, 16, link)
              case s if s == name18 => useDictEntry[P18](n, 17, link)
              case s if s == name19 => useDictEntry[P19](n, 18, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
      _ <- replaceField[P17](n, 16, boxId)
      _ <- replaceField[P18](n, 17, boxId)
      _ <- replaceField[P19](n, 18, boxId)
    } yield ()

  }
    

  def nodeFormat20[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P20: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16], Box[P17], Box[P18], Box[P19], Box[P20]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String, name20: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- writeDictEntry[P17](n, name17, 16, boxLinkStrategy)
        _ <- writeDictEntry[P18](n, name18, 17, boxLinkStrategy)
        _ <- writeDictEntry[P19](n, name19, 18, boxLinkStrategy)
        _ <- writeDictEntry[P20](n, name20, 19, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case s if s == name17 => useDictEntry[P17](n, 16, link)
              case s if s == name18 => useDictEntry[P18](n, 17, link)
              case s if s == name19 => useDictEntry[P19](n, 18, link)
              case s if s == name20 => useDictEntry[P20](n, 19, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
      _ <- replaceField[P17](n, 16, boxId)
      _ <- replaceField[P18](n, 17, boxId)
      _ <- replaceField[P19](n, 18, boxId)
      _ <- replaceField[P20](n, 19, boxId)
    } yield ()

  }
    

  def nodeFormat21[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P20: Format, P21: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16], Box[P17], Box[P18], Box[P19], Box[P20], Box[P21]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String, name20: String, name21: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- writeDictEntry[P17](n, name17, 16, boxLinkStrategy)
        _ <- writeDictEntry[P18](n, name18, 17, boxLinkStrategy)
        _ <- writeDictEntry[P19](n, name19, 18, boxLinkStrategy)
        _ <- writeDictEntry[P20](n, name20, 19, boxLinkStrategy)
        _ <- writeDictEntry[P21](n, name21, 20, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case s if s == name17 => useDictEntry[P17](n, 16, link)
              case s if s == name18 => useDictEntry[P18](n, 17, link)
              case s if s == name19 => useDictEntry[P19](n, 18, link)
              case s if s == name20 => useDictEntry[P20](n, 19, link)
              case s if s == name21 => useDictEntry[P21](n, 20, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
      _ <- replaceField[P17](n, 16, boxId)
      _ <- replaceField[P18](n, 17, boxId)
      _ <- replaceField[P19](n, 18, boxId)
      _ <- replaceField[P20](n, 19, boxId)
      _ <- replaceField[P21](n, 20, boxId)
    } yield ()

  }
    

  def nodeFormat22[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P20: Format, P21: Format, P22: Format, N <: Product](construct: (Box[P1], Box[P2], Box[P3], Box[P4], Box[P5], Box[P6], Box[P7], Box[P8], Box[P9], Box[P10], Box[P11], Box[P12], Box[P13], Box[P14], Box[P15], Box[P16], Box[P17], Box[P18], Box[P19], Box[P20], Box[P21], Box[P22]) => N, default: BoxScript[N])
      (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String, name20: String, name21: String, name22: String,
      nodeName: TokenName = NoName, boxLinkStrategy: NoDuplicatesLinkStrategy = EmptyLinks, nodeLinkStrategy: LinkStrategy = EmptyLinks) : Format[N] = new Format[N] {

    def writeEntriesAndClose(n: N): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- writeDictEntry[P1](n, name1, 0, boxLinkStrategy)
        _ <- writeDictEntry[P2](n, name2, 1, boxLinkStrategy)
        _ <- writeDictEntry[P3](n, name3, 2, boxLinkStrategy)
        _ <- writeDictEntry[P4](n, name4, 3, boxLinkStrategy)
        _ <- writeDictEntry[P5](n, name5, 4, boxLinkStrategy)
        _ <- writeDictEntry[P6](n, name6, 5, boxLinkStrategy)
        _ <- writeDictEntry[P7](n, name7, 6, boxLinkStrategy)
        _ <- writeDictEntry[P8](n, name8, 7, boxLinkStrategy)
        _ <- writeDictEntry[P9](n, name9, 8, boxLinkStrategy)
        _ <- writeDictEntry[P10](n, name10, 9, boxLinkStrategy)
        _ <- writeDictEntry[P11](n, name11, 10, boxLinkStrategy)
        _ <- writeDictEntry[P12](n, name12, 11, boxLinkStrategy)
        _ <- writeDictEntry[P13](n, name13, 12, boxLinkStrategy)
        _ <- writeDictEntry[P14](n, name14, 13, boxLinkStrategy)
        _ <- writeDictEntry[P15](n, name15, 14, boxLinkStrategy)
        _ <- writeDictEntry[P16](n, name16, 15, boxLinkStrategy)
        _ <- writeDictEntry[P17](n, name17, 16, boxLinkStrategy)
        _ <- writeDictEntry[P18](n, name18, 17, boxLinkStrategy)
        _ <- writeDictEntry[P19](n, name19, 18, boxLinkStrategy)
        _ <- writeDictEntry[P20](n, name20, 19, boxLinkStrategy)
        _ <- writeDictEntry[P21](n, name21, 20, boxLinkStrategy)
        _ <- writeDictEntry[P22](n, name22, 21, boxLinkStrategy)
        _ <- put(CloseDict)
      } yield ()
    }

    def readEntries(n: N): BoxReaderScript[Unit] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        _ <- if (t == CloseDict) {
          nothing
        } else {
          pull flatMap {
            case DictEntry(fieldName, link) => fieldName match {
              case s if s == name1 => useDictEntry[P1](n, 0, link)
              case s if s == name2 => useDictEntry[P2](n, 1, link)
              case s if s == name3 => useDictEntry[P3](n, 2, link)
              case s if s == name4 => useDictEntry[P4](n, 3, link)
              case s if s == name5 => useDictEntry[P5](n, 4, link)
              case s if s == name6 => useDictEntry[P6](n, 5, link)
              case s if s == name7 => useDictEntry[P7](n, 6, link)
              case s if s == name8 => useDictEntry[P8](n, 7, link)
              case s if s == name9 => useDictEntry[P9](n, 8, link)
              case s if s == name10 => useDictEntry[P10](n, 9, link)
              case s if s == name11 => useDictEntry[P11](n, 10, link)
              case s if s == name12 => useDictEntry[P12](n, 11, link)
              case s if s == name13 => useDictEntry[P13](n, 12, link)
              case s if s == name14 => useDictEntry[P14](n, 13, link)
              case s if s == name15 => useDictEntry[P15](n, 14, link)
              case s if s == name16 => useDictEntry[P16](n, 15, link)
              case s if s == name17 => useDictEntry[P17](n, 16, link)
              case s if s == name18 => useDictEntry[P18](n, 17, link)
              case s if s == name19 => useDictEntry[P19](n, 18, link)
              case s if s == name20 => useDictEntry[P20](n, 19, link)
              case s if s == name21 => useDictEntry[P21](n, 20, link)
              case s if s == name22 => useDictEntry[P22](n, 21, link)
              case x => throw new IncorrectTokenException("Unknown field name in Node dict " + x)
            }
            case x: Token => throw new IncorrectTokenException("Expected DictEntry in a Node Dict, got " + x)
          } flatMap {_ => readEntries(n)}
        }
      } yield ()
    }

    def readEntriesAndClose = {
      import BoxReaderDeltaF._
      for {
        n <- embedBoxScript(default)  //Note default is a BoxScript, so we need to embed it
        _ <- readEntries(n)
        _ <- pullExpected(CloseDict)
      } yield n
    }

    def write(n: N) = writeNode(n, nodeName, nodeLinkStrategy, writeEntriesAndClose)
    def read = readNode(readEntriesAndClose)

    def replace(n: N, boxId: Long) = for {
      _ <- replaceField[P1](n, 0, boxId)
      _ <- replaceField[P2](n, 1, boxId)
      _ <- replaceField[P3](n, 2, boxId)
      _ <- replaceField[P4](n, 3, boxId)
      _ <- replaceField[P5](n, 4, boxId)
      _ <- replaceField[P6](n, 5, boxId)
      _ <- replaceField[P7](n, 6, boxId)
      _ <- replaceField[P8](n, 7, boxId)
      _ <- replaceField[P9](n, 8, boxId)
      _ <- replaceField[P10](n, 9, boxId)
      _ <- replaceField[P11](n, 10, boxId)
      _ <- replaceField[P12](n, 11, boxId)
      _ <- replaceField[P13](n, 12, boxId)
      _ <- replaceField[P14](n, 13, boxId)
      _ <- replaceField[P15](n, 14, boxId)
      _ <- replaceField[P16](n, 15, boxId)
      _ <- replaceField[P17](n, 16, boxId)
      _ <- replaceField[P18](n, 17, boxId)
      _ <- replaceField[P19](n, 18, boxId)
      _ <- replaceField[P20](n, 19, boxId)
      _ <- replaceField[P21](n, 20, boxId)
      _ <- replaceField[P22](n, 21, boxId)
    } yield ()

  }


}