package part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {

  implicit val system: ActorSystem = ActorSystem("MaterializingStreams")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // materializer is an object that allocates the right resources to the running an akka-stream.

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  // val simpleMaterializedValue: NotUsed = simpleGraph.run() // NotUsed means no value -> unit

  // we can change the NotUsed to a meaningful value.
  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.reduce[Int]((a, b) => a + b)
  // Sink receives Int type.
  /** materialized value is Future[Int],
   * which means after a sink has finished receiving elements,
   * It will expose a materialized value Future[Int] and
   * the Future will be completed with the sum of all these elements.
   *
   * The way we can have a materialized value is to start a running graph between source and the sink
   */

  //val sumFuture: Future[Int] = source.runWith(sink)

  // runWith materializes the stream and returns the materialized value of the given sink or source.
  // runWith connects source with the sink, returns simple graph and executes that graph by calling its run method.

  /** sumFuture is a materialized value obtained by running a graph connecting source and sink
   * Having this Future, We can call classic methods on it */

  import system.dispatcher
  /*
    sumFuture.onComplete {
      case Success(value) => println(s"The sum of all elements is: $value")
      case Failure(exception) => println(s"Cannot compute sum of elements, reason:- $exception")
    } // 55*/

  // When we use via, to... methods by default, the left materialized value is kept.

  // choosingMaterializedValue
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)
  //  simpleSource.via(simpleFlow).to(simpleSink).run()

  //  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  //  simpleSource.viaMat(simpleFlow)(Keep.right) // materialized value of source
  //  simpleSource.viaMat(simpleFlow)(Keep.both) // materialized value of source & sink(tuple)
  //  simpleSource.viaMat(simpleFlow)(Keep.none) // empty materialized value
  //  simpleSource.viaMat(simpleFlow)(Keep.left) // materialized value of sink,
  //  this composite component can further be connected to other components.
  val graph: RunnableGraph[Future[Done]] = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  // graph returns Future[Done], not meaningful but which just signals when this future is completed.
  val f: Future[Done] = graph.run()
  f.onComplete {
    case Success(_) => println("stream processing terminated.")
    case Failure(ex) => println(s"stream processing failed:---> $ex")
  }

  // #1 SYNTACTIC SUGAR of how to combine with a sink and start a runnable graph
  val sum = Source(1 to 5).runWith(Sink.reduce[Int](_+_)) // Source.to(Sink.reduce[Int](_+_))(Keep.right)
  // It also starts the graph using .run()
  // It uses the sink's materialized value

  // #2 shorter Version
  Source(1 to 5).runReduce[Int](_+_)

  // Run Components in backwards
  Sink.foreach(println)runWith Source.single(44) // source(...).to(sink...).run
  // always keeps the left component materialized value.

  // both ways
  Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)
  // It will connect the Flow with both the simpleSource and the Simple Sink
}
