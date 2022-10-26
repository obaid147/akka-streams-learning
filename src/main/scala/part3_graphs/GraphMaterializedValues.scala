package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  // Expose materialized values in components built with GraphDSL

  implicit val system: ActorSystem = ActorSystem("graphMaterializedValue")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val wordSource = Source(List("Akka", "is", "nice", "also", "akka-streams"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)
  // counter sink counts how many strings get inside it &
  // sink.fold is a sink that actually exposes a materialized value.
  // ie:- counter is Sink[String, Future[Int]] where Future[Int] is materialized value of this SINK....

  /* create Composite component that acts like sink
  - print all strings which are lower case
  - also counts strings with < 5 chars.
  - expose materialized value out of < 5 char counter... using the first parameter of GraphDSL.create(???)
  * */

  /** Exposing materialized value of the component passed to GraphDSL.create....
   * before adding first param to GraphDSL.create:- [String, NotUsed], after adding:- String, Future[Int]*/
  // Step1
  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(printer, counter)((_, counterMatValue) => counterMatValue)/*component as param*/ {
      implicit builder => (printerShape, counterShape) =>/*HOF*/
      // we can pass both components to create() but we need to add 2nd arg to .create()
      import GraphDSL.Implicits._

      // step2 shapes
      val broadcast = builder.add(Broadcast[String](2))
      val lowerCaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

      // step3 - connections
      broadcast ~> lowerCaseFilter ~> printerShape
      broadcast ~> shortStringFilter ~> counterShape/*using HOF res here instead of component*/

      // Step4 the shape
      SinkShape(broadcast.in)
    }
  )

  import system.dispatcher
  val shortStringCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()
  shortStringCountFuture.onComplete{
    case Success(count) => println(s"Total short strings:- $count")
    case Failure(ex) => println(s"exception reason:- $ex")
  }
}

object Exercise extends App {
  /*
  *
  * */
  implicit val system: ActorSystem = ActorSystem("graphMaterializedValue")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((c, _) => c + 1)

    Flow.fromGraph(
      GraphDSL.create(counterSink) { implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)

        originalFlowShape ~> broadcast ~> counterSinkShape

        FlowShape(originalFlowShape.in, broadcast.out(1))
      })
  }

  val flow = Flow[Int].map(x => x)
  val source = Source(1 to 50)
  val sink = Sink.ignore

  import system.dispatcher
  val enhancedFlowFuture = source.viaMat(enhanceFlow(flow))(Keep.right).toMat(sink)(Keep.left).run()

  enhancedFlowFuture.onComplete{
    case Success(count) => println(s"$count elements went through the enhanced flow")
    case Failure(ex) => println(s"something failed:- $ex")
  }
}
