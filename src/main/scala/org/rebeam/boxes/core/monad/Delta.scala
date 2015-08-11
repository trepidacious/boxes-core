package org.rebeam.boxes.core.monad

object Delta {
  def delta[A, D <: A => A, B](f: A => (List[D], B)) = new Delta(f)
  def applyDeltas[A, D <: A => A](a: A, d: List[D]) = d.foldLeft(a)((a, d) => d.apply(a))
}

/**
 * Delta applying a function to a "state" of type A, and returning a requested
 * list of deltas to apply to that state, plus a result of type B
 * @param f   Function to apply
 * @tparam A  Type of state/environment
 * @tparam D  Type of delta that can be applied to the state, which must be a function A => A
 * @tparam B  Type of result produced
 */
class Delta[A, D <: A => A, B](f: A => (List[D], B)) {

  def apply(a: A) = f(a)

  def map[C](g: B => C): Delta[A, D, C] = new Delta[A, D, C]({ a =>
    val (deltas, b) = f(a)
    (deltas, g(b))
  })

  def flatMap[C](g: B => Delta[A, D, C]): Delta[A, D, C] = new Delta[A, D, C]({ a =>
    val (d1, b) = f(a)
    val a2 = Delta.applyDeltas(a, d1)
    val (d2, c) = g(b).apply(a2)
    (d1 ++ d2, c)
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

  def put(key: String, value: String) = delta[M, MapDelta, Unit]((m: M) => (List(MapPut(key, value)), ()))
  def apply(key: String) = delta[M, MapDelta, String]((m: M) => (List(MapApply(key)), m.apply(key)))
  def clear() = delta[M, MapDelta, Unit]((m: M) => (List(MapClear()), ()))

  val map: Map[String, String] = Map.empty

  val x = for {
    _ <- put("name", "alice")
    a <- apply("name")
    _ <- put("name", "bob")
    b <- apply("name")
  } yield (a, b)

  val initialMap: M = Map.empty
  val (deltas, result) = x.apply(initialMap)
  val finalMap = applyDeltas(initialMap, deltas)

  println("Initial map " + initialMap + ", applied deltas " + deltas + ", final map " + finalMap)

}
