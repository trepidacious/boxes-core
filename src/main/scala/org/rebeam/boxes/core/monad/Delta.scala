package org.rebeam.boxes.core.monad

import scalaz.Monoid

object Delta {
  def delta[A, D <: A => A: Monoid, B](f: A => (D, B)) = new Delta(f)
}

/**
 * Delta applying a function to a "state" of type A, and returning a requested
 * list of deltas to apply to that state, plus a result of type B
 * @param f   Function to apply
 * @tparam A  Type of state/environment
 * @tparam D  Type of delta that can be applied to the state, which must be a function A => A
 * @tparam B  Type of result produced
 */
class Delta[A, D <: A => A: Monoid, B](f: A => (D, B)) {

  def apply(a: A) = f(a)

  def map[C](g: B => C): Delta[A, D, C] = new Delta[A, D, C]({ a =>
    val (d, b) = f(a)
    (d, g(b))
  })

  def flatMap[C](g: B => Delta[A, D, C]): Delta[A, D, C] = new Delta[A, D, C]({ a =>
    val (d1, b) = f(a)
    val a2 = d1.apply(a)
    val (d2, c) = g(b).apply(a2)
    (implicitly[Monoid[D]].append(d1, d2), c)
  })

}

object DeltaApp extends App {

  import Delta._

  type M = Map[String, String]

  sealed trait MapDelta extends Function1[M, M]

  case class MapPut(key: String, value: String) extends MapDelta {
    def apply(m: M) = m.updated(key, value)
    override def toString = "MapPut(" + key + ", " + value + ")"
  }
  case class MapApply(key: String) extends MapDelta {
    def apply(m: M) = m
    override def toString = "MapApply(" + key + ")"
  }
  case class MapClear() extends MapDelta {
    def apply(m: M) = Map.empty
    override def toString = "MapClear()"
  }

  case class MapDeltas(deltas: Vector[MapDelta]) extends Function1[M, M] {
    def apply(m: M) = deltas.foldLeft(m)((m, d) => d.apply(m))
    override def toString = "MapDeltas(" + deltas + ")"
  }

  def mapDeltasAppend(m1: MapDeltas, m2: => MapDeltas) = MapDeltas(m1.deltas ++ m2.deltas)

  implicit val MapDeltasMonoid = Monoid.instance(mapDeltasAppend, MapDeltas(Vector.empty))

  def put(key: String, value: String) = delta[M, MapDeltas, Unit]((m: M) => (MapDeltas(Vector(MapPut(key, value))), ()))
  def apply(key: String) = delta[M, MapDeltas, String]((m: M) => (MapDeltas(Vector(MapApply(key))), m.apply(key)))
  def clear() = delta[M, MapDeltas, Unit]((m: M) => (MapDeltas(Vector(MapClear())), ()))

  val map: Map[String, String] = Map.empty

  val x = for {
    _ <- put("name", "alice")
    a <- apply("name")
    _ <- put("name", "bob")
    b <- apply("name")
  } yield (a, b)

  val initialMap: M = Map.empty
  val (deltas, result) = x.apply(initialMap)
  val finalMap = deltas.apply(initialMap)

  println("Initial map " + initialMap + ", applied deltas " + deltas + ", final map " + finalMap + ", result " + result)

}
