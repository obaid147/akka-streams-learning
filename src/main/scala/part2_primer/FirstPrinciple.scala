package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}

import scala.concurrent.Future

object FirstPrinciple extends App {

  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")
  //  val materializer = ActorMaterializer()(system) // can pass ActorSystem or make ActorSystem Implicit
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  /** It allows the running of akka streams components */


  // source, Every akka-stream starts with a source which is an akka-streams component
  val source: Source[Int, NotUsed] = Source(1 to 10)

  // sink, Every akka-stream ends with a sink ie: component
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  // sink will call println function on Int, whatever type sink receives, it will call the specified function on it.

  /** In order to create akka stream, we need to connect source with a sink */
  val graph: RunnableGraph[NotUsed] = source.to(sink) // This is called a Graph

  //graph.run()(materializer) // without using implicit
  graph.run() // we need to keep our materializer implicit as graph.run() takes an implicit materializer as 2nd param.

  /** Numbers 1 to 10 are emitted from the source,
   * and are received in the sink,
   * and the sink calls the println function on each
   * */

  // Flows are akka-streams components who's sole job is to transform elements.
  val flow = Flow[Int].map(x => x + 1)

  // attaching flow to a source
  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow) // returns a new source

  val flowWithSink: Sink[Int, NotUsed] = flow.to(sink) // returns a sink

  // returns 2 to 11, there are the ways to run akka-streams
  sourceWithFlow.to(sink).run()
  source.to(flowWithSink).run()
  source.via(flow).to(sink).run()


  /** Sources can omit any type of object as long as they are immutable, serializable much like actor messages
   * because these components are based on actors.
   * NULLS are NOT ALLOWED as per the reactive streams specifications.
   * */

  val illegalSource = Source.single[String](null) // omit single string element
  //  illegalSource.to(Sink.foreach(println)).run() // NULL pointer exception...
  // USE OPTIONS Instead


                    /** Kinds of akka-streams components */
  // -- Various kinds of SOURCES
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1)) // infinite collection of integers, collection streams

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.fromFuture(Future(10))


  // -- Various kinds of SINKS
  val mostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // consumes element of type Int and just retrieves its head and then closes stream.
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  // -- Various kinds of FLOWS----------- mapped to collection operators.
  val mapFlow = Flow[Int].map(x => x + 2)
  val takeFlow = Flow[Int].take(5)// finite stream, if it receives numbers, it will take only first 5.
  // drop, filter
  /** We do not have flatMap in Streams */

  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlowGraph.run()

  //syntactic sugar
  val mapSource = Source(1 to 10).map(x => x*2) // Source(1 to 10).via(Flow[Int].map(x=>x+*2)

  //run streams directly
  mapSource.runForeach(println) // mapSource.to(sink).foreach[Int](println).run

  // OPERATORS == components

}
