package part1_recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  val aCondition: Boolean = true

  def myFunction(x: Int): Int = if (x > 4) 1 else 0

  class Animal

  trait Carnivore {
    def eat(a: Animal): Unit
  }

  object Carnivore

  // generics
  abstract class MyList[+A] // covariant

  // method notations
  1 + 2 // INFIX notation for below
  1.+(2)

  // FP
  val anIncrementer: Int => Int = (x: Int) => x + 1 // apply method of this 'anIncrementer allow it to be called as fn()
  anIncrementer(10)

  // HOF map, flatMap, filter
  // forComprehensions are syntactic sugar for chains of map flatMap filter
  List(1, 2, 3).map(anIncrementer)

  // Monads -> Options, try

  // pattern matching
  val unknown: Any = 2
  val order = unknown match {
    case 1 => "one"
    case 2 => "two"
    case _ => "unknown"
  }

  try {
    throw new RuntimeException
  } catch {
    case e: Exception => println("Caught exception", e)
  }

  /** scala advanced ----------------------------------- */

  // multiThreading

  import scala.concurrent.ExecutionContext.Implicits.global // EC is manager of threads

  val future = Future {
    // long computation exec on some other thread
    100
  }

  // future expose map, flatMap, filter, recover, recoverWith.....
  future.onComplete { // it needed 2nd arg but we used global which is an implicit val injected by the compiler.
    case Success(value) => s"$value Successful"
    case Failure(exception) => s"Exception occurred:--- $exception"
  } // callback this PF is executed on some other thread

  val pf: PartialFunction[Int, Int] = {
    case 1 => 1010
    case 2 => 1000
    case _ => 9000
  } // will throw match error as they are based on pattern matching.


  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]

  def receive: AkkaReceive = {
    case 1 => println("1")
    case _ => println("unknown!")
  }

  // Implicits
  implicit val timeout: Int = 3000

  def setTimeout(f: () => Unit)(implicit timeout: Int): Unit = f()

  //  setTimeout(() => println())(timeout)
  setTimeout(() => println()) // other arg list is injected by compiler


  // conversions

  import scala.language.implicitConversions

  // 1) implicit methods
  case class Person(name: String) {
    def greet: String = s"Hi, my name is $name"
  }

  implicit def fromStringToPeron(name: String): Person = Person(name)

  "alp".greet // now, String itself has the greet method...
  // compiler calls fromStringToPeron on "alp", transforms it to a Person an the calls greet method on that

  fromStringToPeron("alp").greet // same as above...

  // 2) implicit classes
  implicit class Dog(name: String) {
    def bark(): Unit = println(s"Barking-$name")
  }

  "lassie".bark()
  // compiler constructs a new Dog from lassie String and then calls bark on it
  // same as new Dog("lassie").bark


  /** Implicit Organizations  where compiler looks for appropriate implicit value */
  // 1. implicits are fetched from LOCAL SCOPE where we define them ourselves
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1, 2, 3).sorted // compiler will inject numberOrdering as sorted takes implicit 2nd arg as we defined it locally...
  // output = List(3, 2, 1)

  // 2. Imported scope
  // Future creation needed implicit param, we imported global...

  // 3. Companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }
  // Now, we can
  //  List(Person("bob"), Person("alise")).sorted(Person.personOrdering)
  List(Person("bob"), Person("alice")).sorted
  // Person.personOrdering is injected by compiler as an appropriate implicit was found in Person companion object
  // OUTPUT List(Person("alice"), Person("bob"))

}
