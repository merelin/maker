package starling.utils.conversions

import starling.utils.{ImplicitConversions, Log}
import ImplicitConversions._
import scalaz.Scalaz._

trait RichAnys {
  implicit def enrichAny[T](value: T) = new RichAny(value)

  def notNull(namedValues: (String, Any)*) = namedValues.filter(_._2 == null)._1.ifDefined { namesOfMissingValues =>
    throw new IllegalArgumentException("Missing values: " + namesOfMissingValues.mkString(", "))
  }
}

class RichAny[T](protected val value: T) {
  lazy val trimmed = if (value == null) "" else value.toString.applyIf(_.length > 500, _.substring(0, 496) + " ...")

  def add[A, B](tuple: (A, B)): (T, A, B) = (value, tuple._1, tuple._2)
  def add[A, B, C](tuple: (A, B, C)): (T, A, B, C) = (value, tuple._1, tuple._2, tuple._3)
  def ||>[V](pfn: PartialFunction[T, V]): Option[V] = pfn.lift(value)
  def applyIf[V](condition: => Boolean, t: T => V, f: T => V): V = if (condition) t(value) else f(value)
  def applyIf(condition: => Boolean, t: T => T): T = if (condition) t(value) else value
  def applyIf(condition: T => Boolean, t: T => T): T = applyIf(condition(value), t)
  def applyAll(fns: (T => T)*): T = Function.chain(fns)(value)
  def update[V](actions: (T => V)*): T = { actions.foreach(_.apply(value)); value }
  def updateIt[V](actions: (T => V)*): T = update(actions : _*)
  def compareTo(other : T)(implicit ev : Ordering[T]) = ev.compare(value, other)

  def debugV[V <: AnyRef](action : T => V) : T = perform(Log.debug(action(value)))
  def info[V <: AnyRef](action : T => V) : T = perform(Log.info(action(value)))

  def assert(assertion: T => Boolean, message: => Any) : T = perform(Predef.assert(assertion(value), message))
  def deny(denial: T => Boolean, message: => Any) : T = perform(Predef.assert(!denial(value), message))
  def require(requirement: T => Boolean, message: => Any): T = perform(Predef.require(requirement(value), message + " " + trimmed))
  def desire(wish: T => Boolean, message: => Any): T = perform(if (!wish(value)) Log.warn(message + " " + trimmed))

  def isOneOf(values : T*) = values.contains(value)
  def isOneOf(values : Set[T]) = values.contains(value)
  val repeat : Seq[T] = Stream.continually(value).toSeq
  def partialMatch[V](pfn: PartialFunction[T, V]): Option[V] = ||>(pfn)
  def partialMatchO[V](pfn: PartialFunction[T, Option[V]]): Option[V] = partialMatch(pfn).flatOpt
  def safePartialMatch[V](message: => String)(pfn: PartialFunction[T, V]): Option[V] =
    try { ||>(pfn) } catch { case _ => Log.warn(message + ": " + value); None }

  def equalTo[V: Manifest](f: V => Boolean): Boolean = safeCast[V].map(f).getOrElse(false)
  def safeCast[V: Manifest]: Option[V] = implicitly[Manifest[V]].safeCast(value)
  def cast[V: Manifest]: V = implicitly[Manifest[V]].cast(value)
  def castOrElse[V: Manifest](alternative: T => V): V = implicitly[Manifest[V]].safeCast(value).getOrElse(alternative(value))
  def pair[V](f: T => V): (T, V) = value → f(value)
  def pairWithTraversable[V](f: T => Traversable[V]): scala.Traversable[(T, V)] = pair(f(value))
  def pair[V](t: Traversable[V]) = t.pair(value).swap
  def optPair[V](option: Option[V]): Option[(T, V)] = option.map(value → _)
  def optPair[V](f: T => Option[V]): Option[(T, V)] = value.optPair(f(value))

  def appendToStream(stream: Stream[T]): Stream[T] = stream.append(value #:: Stream.empty)

  def ::-(list: List[T]): List[T] = value :: list
  def ::-(anotherElem: T): List[T] = value :: anotherElem :: Nil
  def ->>[V](vs: V*): (T, List[V]) = (value, vs.toList)

  def const[V] = (v:V) => value

  private def perform(action: => Unit): T = { action; value }
}