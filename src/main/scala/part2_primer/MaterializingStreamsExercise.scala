package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future

object MaterializingStreamsExercise extends App {

  /** Run the following in as many different ways as we can....
   *
   * - #1 return the last element out of a Source (USE sink.last)
   * - #2 computer total wordCount out of a stream of sentences
   * (USE Flow.map, Flow.fold/sink.fold, and Flow.reduce/sink.reduce)
   * */

  implicit val system: ActorSystem = ActorSystem("MaterializingExercise")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import system.dispatcher
  val f1: Future[Int] = Source(1 to 5).runWith(Sink.last)
  val f2: Future[Int] = Source(1 to 5).toMat(Sink.last)(Keep.right).run()
  f1.map(println)
  f2.map(println)

  import system.dispatcher
  //  f.map(println)
  /*
   * f.onComplete{case Success(v) => println(v) case Failure(ex) => println(ex)}
   */

  val sentences = List("This is it", "is it that", "a laptop", "sentence for this exercise")
  val sourceCount = Source(sentences)

  val wordCountSink = Sink.fold[Int, String](0)((a, b) => a + b.split(" ").length)
  val g1: Future[Int] = sourceCount.toMat(wordCountSink)(Keep.right).run()
  val g2: Future[Int] = sourceCount.runWith(wordCountSink)
  val g3: Future[Int] = sourceCount.runFold(0)((a, b) => a + b.split(" ").length)

  val wordCountFlow = Flow[String].fold[Int](0)((x, y) => x + y.split(" ").length)
  val g4: Future[Int] = sourceCount.via(wordCountFlow).toMat(Sink.head)(Keep.right).run()

  val g5: Future[Int] = sourceCount.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
  val g6: Future[Int] = sourceCount.via(wordCountFlow).runWith(Sink.head) // same as g4
  val g7 = wordCountFlow.runWith(sourceCount, Sink.head)._2 // Tuple, we use _2 which is materialized value of Sink.head


  /*val x1: Source[Int, NotUsed] = sourceCount.via(Flow[String].fold(0)((x, y) => x + y.length))
  val z1: Future[Done] = x1.runWith(Sink.foreach[Int](println))

  //  val x2 = sourceCount.via(Flow[String].fold(0)((x, y) => x + y.length)).to(Sink.reduce(_+_))

  val simpleSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  val simpleFlow = Flow[String].fold(0)((x,y) => x + y.length)
  simpleFlow.runWith(sourceCount, simpleSink)

  sourceCount.viaMat(simpleFlow)(Keep.both).toMat(simpleSink)(Keep.both)*/

}
