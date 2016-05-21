package org.rebeam.boxes.persistence.formats

import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

class NodeFormatsBase {
  
  protected def writeDictEntry[T: Format](n: Product, name: String, index: Int, linkStrategy: NoDuplicatesLinkStrategy) = {

    import BoxWriterDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]

    //NodeFormat will not use link references, but can use ids, for example for sharing box id with clients on network.
    //This is done by caching the box and using the id, but throwing an exception if we find the box is already cached.
    //This ensures that only one copy of the Box is in use in a Node, so that the Node technique of recreating Nodes
    //from default with new boxes is valid. Note that we don't really care if other Formats end up referencing our
    //Boxes later - we add them to cache when reading.
    for {
      cr <- cache(box)
      _ <- cr match {
        case Cached(id) => throw new BoxCacheException("Box id " + box.id + " was already cached as id " + id + ", but NodeFormats doesn't work with multiply-referenced Boxes")
        case New(id) => 
          val link = linkStrategy match {
            case IdLinks => LinkId(id)
            case EmptyLinks => LinkEmpty
          }

          for {
            //Open an entry for the Box
            _ <- put(DictEntry(name, link))

            //Cache the box whether we used LinkId or LinkEmpty - we use this to avoid duplicates in either case, and for possible references outside nodes
            _ <- cache(box) //TODO this should already have been done on line 50?
            v <- get(box) //Note we can't use box.get here since it returned BoxScript. Should maybe use implicits for this
            _ <- implicitly[Format[T]].write(v)
          } yield ()
      }

    } yield ()

  }

  protected def useDictEntry[T :Format](n: Product, index: Int, link: Link) = {
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

  protected def writeNode[N](n: N, name: TokenName, nodeLinkStrategy: LinkStrategy, writeEntriesAndClose: N => BoxWriterScript[Unit]) = {

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

  protected def readNode[N](readEntriesAndClose: BoxReaderScript[N]) = {
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

  protected def replaceField[T](n: Product, index: Int, boxId: Long)(implicit f: Format[T]) = {
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

  protected def modifyField[T, N <: Product, A <: Action[N]](n: N, index: Int, boxId: Long, readsAction: Option[Reads[A]])(implicit f: Format[T]) = {
    import BoxReaderDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]

    //If this is our box and we have a readsAction, use it to read an action
    //from tokens and then perform that action
    if (box.id == boxId) {
      readsAction.map (r => {
        for {
          token <- peek
          
          //If we are out of tokens, action has already been performed,
          //so do nothing
          _ <- if (token == EndToken) {
            nothing
            
          //If we have tokens, read the action and perform it on the node
          } else {
            for {
              action <- r.read
              _ <- embedBoxScript(action.act(n))
            } yield ()
          }
        } yield ()
      }).getOrElse(nothing)
      
    //If this is not our box, recurse to its contents
    } else {
      for {
        t <- get(box)
        _ <- f.modify(t, boxId)
      } yield ()
    }
  }
}
