package org.rebeam.boxes.persistence.formats

import scala.language.implicitConversions

import org.rebeam.boxes.persistence._
import org.rebeam.boxes.core._
import BoxTypes._

import scalaz._
import Scalaz._

//TODO should this have support for dict links?
object ProductFormats {

  private def writeDictEntry[T :Format](p: Product, name: String, index: Int): BoxWriterScript[Unit] = {
    import BoxWriterDeltaF._
    put(DictEntry(name)) flatMap (_ => implicitly[Format[T]].write(p.productElement(index).asInstanceOf[T]))
  }

  private def replaceField[T](n: Product, index: Int, boxId: Long)(implicit f: Format[T]) = {
    val t = n.productElement(index).asInstanceOf[T]
    f.replace(t, boxId)
  }

  private def modifyField[T](n: Product, index: Int, boxId: Long)(implicit f: Format[T]) = {
    val t = n.productElement(index).asInstanceOf[T]
    f.modify(t, boxId)
  }

  private def modifyBoxWithAction[P](box: Box[P], readsAction: Option[Reads[Action[P]]]) = {
    import BoxReaderDeltaF._
    //If we have a Reads[Action], use it to read an action from tokens
    //and then perform the action
    readsAction.map (r => {
      for {
        action <- r.read
        _ <- embedBoxScript(action.act(box))
      } yield ()
    }).getOrElse(nothing)
  }    

  def productFormat0[P <: Product](construct: => P)
                                  (productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- put(CloseDict)
      } yield ()
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      pull flatMap {
        case OpenDict(_, _) =>
          for {
            _ <- pullExpected(CloseDict)
            p = construct
          } yield p

        case _ => throw new IncorrectTokenException("Expected OpenDict at start of Map[String, _]")
      }
    }
    
    def replace(p: P, boxId: Long) = BoxReaderDeltaF.nothing
    def modify(p: P, boxId: Long) = BoxReaderDeltaF.nothing
    def modifyBox(p: Box[P]) = BoxReaderDeltaF.nothing

  }
  
  /**
   * Format for a value class that just reads/writes the wrapped value
   */
  def valueClassFormat[P1: Format, P <: Product](construct: (P1) => P) : Format[P] = new Format[P] {
    def write(p: P): BoxWriterScript[Unit] = implicitly[Format[P1]].write(p.productElement(0).asInstanceOf[P1])
    def read: BoxReaderScript[P] = implicitly[Format[P1]].read map (v => construct(v))

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
    } yield ()

    //TODO accept a Reads[Action[P]]?
    def modifyBox(p: Box[P]) = BoxReaderDeltaF.nothing
  }

  // ################################################################
  // ################################################################
  // ##  Auto generated code for each different arity of Product   ##
  // ################################################################
  // ################################################################

  def productFormat1[P1: Format, P <: Product](construct: (P1) => P)
  (name1: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat2[P1: Format, P2: Format, P <: Product](construct: (P1, P2) => P)
  (name1: String, name2: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat3[P1: Format, P2: Format, P3: Format, P <: Product](construct: (P1, P2, P3) => P)
  (name1: String, name2: String, name3: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat4[P1: Format, P2: Format, P3: Format, P4: Format, P <: Product](construct: (P1, P2, P3, P4) => P)
  (name1: String, name2: String, name3: String, name4: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat5[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P <: Product](construct: (P1, P2, P3, P4, P5) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat6[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat7[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat8[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat9[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat10[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat11[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat12[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat13[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat14[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat15[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat16[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat17[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- writeDictEntry[P17](p, name17, 16)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None,
      p17: Option[P17] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else if (n == name17) implicitly[Format[P17]].read map (v => options.copy(p17 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16)),
            options.p17.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name17))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
      _ <- replaceField[P17](p, 16, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
      _ <- modifyField[P17](p, 16, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat18[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- writeDictEntry[P17](p, name17, 16)
        _ <- writeDictEntry[P18](p, name18, 17)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None,
      p17: Option[P17] = None,
      p18: Option[P18] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else if (n == name17) implicitly[Format[P17]].read map (v => options.copy(p17 = Some(v)))
              else if (n == name18) implicitly[Format[P18]].read map (v => options.copy(p18 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16)),
            options.p17.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name17)),
            options.p18.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name18))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
      _ <- replaceField[P17](p, 16, boxId)
      _ <- replaceField[P18](p, 17, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
      _ <- modifyField[P17](p, 16, boxId)
      _ <- modifyField[P18](p, 17, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat19[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- writeDictEntry[P17](p, name17, 16)
        _ <- writeDictEntry[P18](p, name18, 17)
        _ <- writeDictEntry[P19](p, name19, 18)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None,
      p17: Option[P17] = None,
      p18: Option[P18] = None,
      p19: Option[P19] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else if (n == name17) implicitly[Format[P17]].read map (v => options.copy(p17 = Some(v)))
              else if (n == name18) implicitly[Format[P18]].read map (v => options.copy(p18 = Some(v)))
              else if (n == name19) implicitly[Format[P19]].read map (v => options.copy(p19 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16)),
            options.p17.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name17)),
            options.p18.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name18)),
            options.p19.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name19))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
      _ <- replaceField[P17](p, 16, boxId)
      _ <- replaceField[P18](p, 17, boxId)
      _ <- replaceField[P19](p, 18, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
      _ <- modifyField[P17](p, 16, boxId)
      _ <- modifyField[P18](p, 17, boxId)
      _ <- modifyField[P19](p, 18, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat20[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P20: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String, name20: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- writeDictEntry[P17](p, name17, 16)
        _ <- writeDictEntry[P18](p, name18, 17)
        _ <- writeDictEntry[P19](p, name19, 18)
        _ <- writeDictEntry[P20](p, name20, 19)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None,
      p17: Option[P17] = None,
      p18: Option[P18] = None,
      p19: Option[P19] = None,
      p20: Option[P20] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else if (n == name17) implicitly[Format[P17]].read map (v => options.copy(p17 = Some(v)))
              else if (n == name18) implicitly[Format[P18]].read map (v => options.copy(p18 = Some(v)))
              else if (n == name19) implicitly[Format[P19]].read map (v => options.copy(p19 = Some(v)))
              else if (n == name20) implicitly[Format[P20]].read map (v => options.copy(p20 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16)),
            options.p17.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name17)),
            options.p18.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name18)),
            options.p19.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name19)),
            options.p20.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name20))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
      _ <- replaceField[P17](p, 16, boxId)
      _ <- replaceField[P18](p, 17, boxId)
      _ <- replaceField[P19](p, 18, boxId)
      _ <- replaceField[P20](p, 19, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
      _ <- modifyField[P17](p, 16, boxId)
      _ <- modifyField[P18](p, 17, boxId)
      _ <- modifyField[P19](p, 18, boxId)
      _ <- modifyField[P20](p, 19, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat21[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P20: Format, P21: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String, name20: String, name21: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- writeDictEntry[P17](p, name17, 16)
        _ <- writeDictEntry[P18](p, name18, 17)
        _ <- writeDictEntry[P19](p, name19, 18)
        _ <- writeDictEntry[P20](p, name20, 19)
        _ <- writeDictEntry[P21](p, name21, 20)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None,
      p17: Option[P17] = None,
      p18: Option[P18] = None,
      p19: Option[P19] = None,
      p20: Option[P20] = None,
      p21: Option[P21] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else if (n == name17) implicitly[Format[P17]].read map (v => options.copy(p17 = Some(v)))
              else if (n == name18) implicitly[Format[P18]].read map (v => options.copy(p18 = Some(v)))
              else if (n == name19) implicitly[Format[P19]].read map (v => options.copy(p19 = Some(v)))
              else if (n == name20) implicitly[Format[P20]].read map (v => options.copy(p20 = Some(v)))
              else if (n == name21) implicitly[Format[P21]].read map (v => options.copy(p21 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16)),
            options.p17.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name17)),
            options.p18.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name18)),
            options.p19.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name19)),
            options.p20.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name20)),
            options.p21.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name21))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
      _ <- replaceField[P17](p, 16, boxId)
      _ <- replaceField[P18](p, 17, boxId)
      _ <- replaceField[P19](p, 18, boxId)
      _ <- replaceField[P20](p, 19, boxId)
      _ <- replaceField[P21](p, 20, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
      _ <- modifyField[P17](p, 16, boxId)
      _ <- modifyField[P18](p, 17, boxId)
      _ <- modifyField[P19](p, 18, boxId)
      _ <- modifyField[P20](p, 19, boxId)
      _ <- modifyField[P21](p, 20, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    

  def productFormat22[P1: Format, P2: Format, P3: Format, P4: Format, P5: Format, P6: Format, P7: Format, P8: Format, P9: Format, P10: Format, P11: Format, P12: Format, P13: Format, P14: Format, P15: Format, P16: Format, P17: Format, P18: Format, P19: Format, P20: Format, P21: Format, P22: Format, P <: Product](construct: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) => P)
  (name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String, name8: String, name9: String, name10: String, name11: String, name12: String, name13: String, name14: String, name15: String, name16: String, name17: String, name18: String, name19: String, name20: String, name21: String, name22: String,
  productName: TokenName = NoName) : Format[P] = new Format[P] {

    def write(p: P): BoxWriterScript[Unit] = {
      import BoxWriterDeltaF._
      for {
        _ <- put(OpenDict(productName))
        _ <- writeDictEntry[P1](p, name1, 0)
        _ <- writeDictEntry[P2](p, name2, 1)
        _ <- writeDictEntry[P3](p, name3, 2)
        _ <- writeDictEntry[P4](p, name4, 3)
        _ <- writeDictEntry[P5](p, name5, 4)
        _ <- writeDictEntry[P6](p, name6, 5)
        _ <- writeDictEntry[P7](p, name7, 6)
        _ <- writeDictEntry[P8](p, name8, 7)
        _ <- writeDictEntry[P9](p, name9, 8)
        _ <- writeDictEntry[P10](p, name10, 9)
        _ <- writeDictEntry[P11](p, name11, 10)
        _ <- writeDictEntry[P12](p, name12, 11)
        _ <- writeDictEntry[P13](p, name13, 12)
        _ <- writeDictEntry[P14](p, name14, 13)
        _ <- writeDictEntry[P15](p, name15, 14)
        _ <- writeDictEntry[P16](p, name16, 15)
        _ <- writeDictEntry[P17](p, name17, 16)
        _ <- writeDictEntry[P18](p, name18, 17)
        _ <- writeDictEntry[P19](p, name19, 18)
        _ <- writeDictEntry[P20](p, name20, 19)
        _ <- writeDictEntry[P21](p, name21, 20)
        _ <- writeDictEntry[P22](p, name22, 21)
        _ <- put(CloseDict)
      } yield ()
    }

    case class Options(
      p1: Option[P1] = None,
      p2: Option[P2] = None,
      p3: Option[P3] = None,
      p4: Option[P4] = None,
      p5: Option[P5] = None,
      p6: Option[P6] = None,
      p7: Option[P7] = None,
      p8: Option[P8] = None,
      p9: Option[P9] = None,
      p10: Option[P10] = None,
      p11: Option[P11] = None,
      p12: Option[P12] = None,
      p13: Option[P13] = None,
      p14: Option[P14] = None,
      p15: Option[P15] = None,
      p16: Option[P16] = None,
      p17: Option[P17] = None,
      p18: Option[P18] = None,
      p19: Option[P19] = None,
      p20: Option[P20] = None,
      p21: Option[P21] = None,
      p22: Option[P22] = None
    )
                       
    def readOptions(options: Options): BoxReaderScript[Options] = {
      import BoxReaderDeltaF._
      for {
        t <- peek
        newOptions <- if (t == CloseDict) {
          just(options)
        } else {
          pull flatMap {
            case DictEntry(n, LinkEmpty) =>
              if (n == name1) implicitly[Format[P1]].read map (v => options.copy(p1 = Some(v)))
              else if (n == name2) implicitly[Format[P2]].read map (v => options.copy(p2 = Some(v)))
              else if (n == name3) implicitly[Format[P3]].read map (v => options.copy(p3 = Some(v)))
              else if (n == name4) implicitly[Format[P4]].read map (v => options.copy(p4 = Some(v)))
              else if (n == name5) implicitly[Format[P5]].read map (v => options.copy(p5 = Some(v)))
              else if (n == name6) implicitly[Format[P6]].read map (v => options.copy(p6 = Some(v)))
              else if (n == name7) implicitly[Format[P7]].read map (v => options.copy(p7 = Some(v)))
              else if (n == name8) implicitly[Format[P8]].read map (v => options.copy(p8 = Some(v)))
              else if (n == name9) implicitly[Format[P9]].read map (v => options.copy(p9 = Some(v)))
              else if (n == name10) implicitly[Format[P10]].read map (v => options.copy(p10 = Some(v)))
              else if (n == name11) implicitly[Format[P11]].read map (v => options.copy(p11 = Some(v)))
              else if (n == name12) implicitly[Format[P12]].read map (v => options.copy(p12 = Some(v)))
              else if (n == name13) implicitly[Format[P13]].read map (v => options.copy(p13 = Some(v)))
              else if (n == name14) implicitly[Format[P14]].read map (v => options.copy(p14 = Some(v)))
              else if (n == name15) implicitly[Format[P15]].read map (v => options.copy(p15 = Some(v)))
              else if (n == name16) implicitly[Format[P16]].read map (v => options.copy(p16 = Some(v)))
              else if (n == name17) implicitly[Format[P17]].read map (v => options.copy(p17 = Some(v)))
              else if (n == name18) implicitly[Format[P18]].read map (v => options.copy(p18 = Some(v)))
              else if (n == name19) implicitly[Format[P19]].read map (v => options.copy(p19 = Some(v)))
              else if (n == name20) implicitly[Format[P20]].read map (v => options.copy(p20 = Some(v)))
              else if (n == name21) implicitly[Format[P21]].read map (v => options.copy(p21 = Some(v)))
              else if (n == name22) implicitly[Format[P22]].read map (v => options.copy(p22 = Some(v)))
              else throw new IncorrectTokenException("Product format has unrecognised name " + n)

            case t => throw new IncorrectTokenException("Product format has unexpected token " + t)            
          } flatMap {readOptions(_)}
        }
      } yield newOptions
    }

    def read: BoxReaderScript[P] = {
      import BoxReaderDeltaF._
      for {
        maybeOpenDict <- pullFiltered(t => t match {
          case OpenDict(_, _) => true
          case _ => false
        })
        options <- readOptions(Options())
        p = construct(
            options.p1.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name1)),
            options.p2.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name2)),
            options.p3.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name3)),
            options.p4.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name4)),
            options.p5.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name5)),
            options.p6.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name6)),
            options.p7.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name7)),
            options.p8.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name8)),
            options.p9.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name9)),
            options.p10.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name10)),
            options.p11.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name11)),
            options.p12.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name12)),
            options.p13.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name13)),
            options.p14.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name14)),
            options.p15.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name15)),
            options.p16.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name16)),
            options.p17.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name17)),
            options.p18.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name18)),
            options.p19.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name19)),
            options.p20.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name20)),
            options.p21.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name21)),
            options.p22.getOrElse(throw new IncorrectTokenException("Product format has missing field " + name22))
          )
        _ <- pullExpected(CloseDict)
      } yield p
    }

    def replace(p: P, boxId: Long) = for {
      _ <- replaceField[P1](p, 0, boxId)
      _ <- replaceField[P2](p, 1, boxId)
      _ <- replaceField[P3](p, 2, boxId)
      _ <- replaceField[P4](p, 3, boxId)
      _ <- replaceField[P5](p, 4, boxId)
      _ <- replaceField[P6](p, 5, boxId)
      _ <- replaceField[P7](p, 6, boxId)
      _ <- replaceField[P8](p, 7, boxId)
      _ <- replaceField[P9](p, 8, boxId)
      _ <- replaceField[P10](p, 9, boxId)
      _ <- replaceField[P11](p, 10, boxId)
      _ <- replaceField[P12](p, 11, boxId)
      _ <- replaceField[P13](p, 12, boxId)
      _ <- replaceField[P14](p, 13, boxId)
      _ <- replaceField[P15](p, 14, boxId)
      _ <- replaceField[P16](p, 15, boxId)
      _ <- replaceField[P17](p, 16, boxId)
      _ <- replaceField[P18](p, 17, boxId)
      _ <- replaceField[P19](p, 18, boxId)
      _ <- replaceField[P20](p, 19, boxId)
      _ <- replaceField[P21](p, 20, boxId)
      _ <- replaceField[P22](p, 21, boxId)
    } yield ()

    def modify(p: P, boxId: Long) = for {
      _ <- modifyField[P1](p, 0, boxId)
      _ <- modifyField[P2](p, 1, boxId)
      _ <- modifyField[P3](p, 2, boxId)
      _ <- modifyField[P4](p, 3, boxId)
      _ <- modifyField[P5](p, 4, boxId)
      _ <- modifyField[P6](p, 5, boxId)
      _ <- modifyField[P7](p, 6, boxId)
      _ <- modifyField[P8](p, 7, boxId)
      _ <- modifyField[P9](p, 8, boxId)
      _ <- modifyField[P10](p, 9, boxId)
      _ <- modifyField[P11](p, 10, boxId)
      _ <- modifyField[P12](p, 11, boxId)
      _ <- modifyField[P13](p, 12, boxId)
      _ <- modifyField[P14](p, 13, boxId)
      _ <- modifyField[P15](p, 14, boxId)
      _ <- modifyField[P16](p, 15, boxId)
      _ <- modifyField[P17](p, 16, boxId)
      _ <- modifyField[P18](p, 17, boxId)
      _ <- modifyField[P19](p, 18, boxId)
      _ <- modifyField[P20](p, 19, boxId)
      _ <- modifyField[P21](p, 20, boxId)
      _ <- modifyField[P22](p, 21, boxId)
    } yield ()

    def modifyBox(box: Box[P]) = BoxReaderDeltaF.nothing

  }
    
}