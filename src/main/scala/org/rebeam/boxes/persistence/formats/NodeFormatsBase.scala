package org.rebeam.boxes.persistence.formats

import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

class NodeFormatsBase {
  
  protected def writeDictEntry[T: Format](n: Product, name: String, index: Int, linkStrategy: LinkStrategy) = {

    import BoxWriterDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]

    //NodeFormat will not use link references, but can use ids, for example for sharing box id with clients on network.
    //This is done by caching the box and using the id, but throwing an exception if we find the box is already cached.
    //This ensures that only one copy of the Box is in use in a Node, so that the Node technique of recreating Nodes
    //from default with new boxes is valid. Note that we don't really care if other Formats end up referencing our
    //Boxes later - we add them to cache when reading.
    for {
      id <- getId(box)
      
      //Open an entry for the Box
      _ <- put(DictEntry(name, linkStrategy.link(id)))

      //Cache the box whether we used LinkId or LinkEmpty - we use this to avoid duplicates in either case, and for possible references outside nodes
      v <- get(box) //Note we can't use box.get here since it returned BoxScript. Should maybe use implicits for this
      _ <- implicitly[Format[T]].write(v)

    } yield ()

  }

  protected def useDictEntry[T :Format](n: Product, index: Int, link: Link) = {
    import BoxReaderDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]

    for {
      v <- implicitly[Format[T]].read
      _ <- set(box, v)
    } yield ()
  }

  protected def writeNode[N](n: N, name: TokenName, nodeLinkStrategy: LinkStrategy, writeEntriesAndClose: N => BoxWriterScript[Unit]) = {
    import BoxWriterDeltaF._
    for {
      id <- getId(n)
      _ <- put(OpenDict(name, nodeLinkStrategy.link(id))) flatMap (_ => writeEntriesAndClose(n))
    } yield ()
  }

  protected def readNode[N](readEntriesAndClose: BoxReaderScript[N]) = {
    import BoxReaderDeltaF._

    pull flatMap {
      case OpenDict(name, _) => readEntriesAndClose
      case _ => throw new IncorrectTokenException("Expected OpenDict at start of Map[String, _]")
    }
  }

  protected def replaceField[T](n: Product, index: Int, boxId: Long)(implicit f: Format[T]) = {
    import BoxReaderDeltaF._
  
    val box = n.productElement(index).asInstanceOf[Box[T]]
  
    //If this is our box, read a new value for it from tokens, set that new 
    //value and we are done
    for {
      id <- getId(box)
      _ <- if (id == boxId) {
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
    } yield ()
    
  }

  protected def modifyField[T, N <: Product, A <: Action[N]](n: N, index: Int, id: Long, readsAction: Option[Reads[A]])(implicit f: Format[T]) = {
    import BoxReaderDeltaF._

    val box = n.productElement(index).asInstanceOf[Box[T]]

    //FIXME in nodeFormatsN instances, first call something like first part of this function
    //to handle case that id matches node, then if NOT, call something like second part
    //on each field so we can recurse through boxes

    //If this node matches id and we have a readsAction, use it to read an action
    //from tokens and then perform that action on the node
    for {
      nodeId <- getId(n)
      _ <- if (nodeId == id) {
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
        
      //If we are not the identified node, recurse to boxes contents
      } else {
        for {
          t <- get(box)
          _ <- f.modify(t, id)
        } yield ()
      }
    } yield ()    
  }
}
