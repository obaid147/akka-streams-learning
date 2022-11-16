package part5_advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import scala.util.{Failure, Success}

object SubStreams extends App {
  implicit val system: ActorSystem = ActorSystem("SubStreams")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // #1- grouping a stream by a certain function
  val xs = List("Akka", "is", "amazing", "learning", "SubStreams")
  val wordSource = Source(xs)
  //println(xs.groupBy(word => if(word.isEmpty) '\0' else word.toLowerCase().charAt(0)))
  val groups = wordSource.groupBy(30, word => if (word.isEmpty) '\0' else word.toLowerCase().charAt(0))

  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"I just received $word and Count: $newCount")
    newCount
  }))
  //.run()

  /** Once we attach a consumer to a subFlow, every stream will have a different materialization of this component
   * xs.groupBy(word => if(word.isEmpty) '\0' else word.toLowerCase().charAt(0)), We got a map
   * Map(key, List(values)
   * When we run the stream, count becomes 2 when we reach "amazing" because it has "Akka" before it in the List
   * and does not create a new key......
   *
   * anotherExample
   * val xs1 = List("Akka", "is", "amazing", "learning", "SubStreams", "Locked")
   * When we run the stream, count becomes 2 when we reach "amazing" because it has "Akka" before it in the List
   * when we reach "Locked" count will be 2 as it can see "Learning" in the List with key as 'l' ...
   * */
  val xs1 = List("Akka", "is", "amazing", "learning", "SubStreams", "Locked")
  val s = Source(xs1)
  val g = s.groupBy(40, word => if (word.isEmpty) '\0' else word.toLowerCase().charAt(0))

  g.to(Sink.fold(0)((c, w) => {
    val nC = c + 1
    println(s"I just received $w and Count: $nC")
    nC
  }))
  //.run()

  /** Working ------------------->>>>>>>>>>>>>>>>>>>
   * If we have a Source and we apply a  method/function that creates subStreams like groupBy(e =>...) then,
   * for every single element, that thing will probably create new substrates.
   * In the case of groupBy, If that element creates a new key, which will create a new substring, then
   * if you want to attach a consumer, then every single substring will have a different instance of that consumer.
   * */


  // #2- merge subStreams back to master Stream
  /*val x = xs.groupBy(_.head).toList
  println(x.flatMap(a => a._2))*/
  val xs2 = List("I Love Akka", "Akka is Awesome", "Learning akka-streams")
  val textSource = Source(xs2)

  val totalCharCountFuture = textSource // split the source
    .groupBy(2, string => string.length % 2)
    .map(_.length) // expensive computation here
    .mergeSubstreamsWithParallelism(2)// merge result back in s single stream
    //.mergeSubstreams == mergeSubstreamsWithParallelism(Int.maxValue) for unbounded merge streams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)// aggregate the results
    .run()

  import system.dispatcher

  totalCharCountFuture.onComplete {
    case Success(v) => println(s"Total char count = $v")
    case Failure(ex) => println(s"Failed coz:- $ex")
  }

  // #3 splitting stream into SubStreams, When a condition is met.
  val text = "I Love Akka\n" +
    "Akka is Awesome\n" +
    "Learning akka-streams\n"

  // creating a new sub stream everytime we hit a \n (new line character)
  val anotherCharCharacterFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
    .filter(_ != '\n')
    .map(_ => 1) // each char counts as 1
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_+_))(Keep.right)
    .run()

  anotherCharCharacterFuture.onComplete{
    case Success(v) => println(s"Total char count alternative = $v")
    case Failure(ex) => println(s"Failed coz:- $ex")
  }

  // #4 flattening
  val source = Source(1 to 5)
//  source.flatMapConcat(x => Source(x to 3*x)).runWith(Sink.foreach[Int](println))
  source.flatMapMerge(2, x => Source(x to 3*x)).runWith(Sink.foreach[Int](println))
  // flatMapMerge will take at least 2 subStreams at once & will shove them out to the output with no order...
}
