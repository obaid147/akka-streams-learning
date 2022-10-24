package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, SinkShape, SourceShape}

object OpenGraphs extends App {

  implicit val system: ActorSystem = ActorSystem("openGraphs")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /* COMPLEX SOURCE
  * - A composite source that concatenates 2 sources
  * - emit all the elements from first source
  * - then all the elements from second source*/
  val firstSource = Source(1 to 10)
  val secondSource = Source(50 to 1000)

  val sourceGraph = Source.fromGraph( // instead of runnable graph that we can run directly, we create a component
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // declare components
      val concat = builder.add(Concat[Int](2)) // concat fan-in, multiple inputs single output.

      // tie them together
      firstSource ~> concat // ist input to concat
      secondSource ~> concat // 2nd input to concat
      //firstSource.concat(secondSource)

      SourceShape(concat.out) // instead of closed shape
    })

//  sourceGraph.to(Sink.foreach(println)).run() // ist source will be printed first fully, then 2nd source.

  /* COMPLEX SINKS
  * somebody in my company wrote 2 sinks
  * and i only have 1 source that emits only once
  * we would like to feed a source to both these sinks
  * */
  val sink1 = Sink.foreach[Int](x => println(s"sink 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"sink 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2)) // add a broadcast

      // tie components together
      broadcast ~> sink1
      broadcast ~> sink2


      SinkShape(broadcast.in) // return the shape
    })

//  firstSource.to(sinkGraph).run()

  /* COMPLEX FLOW
  * - flow1 that adds 1 to a number
  * - flow2 that does number * 10
  * */
  val flow1 = Flow[Int].map(x => x+1)
  val flow2 = Flow[Int].map(x => x * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      //flow1 ~> flow2 // flow / any component cannot be tied together using ~> operator only shapes have this...
      val flow1Shape = builder.add(flow1)
      val flow2Shape = builder.add(flow2)
      // MUTABLE builder adds the shape of flow1 & flow2 as a copy, Which can be used to link components.

      flow1Shape ~> flow2Shape

      FlowShape(flow1Shape.in, flow2Shape.out)
    })

  //firstSource.via(flowGraph).to(Sink.foreach[Int](println)).run()

  /* Create a flow from a sink and a source */
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits._

        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        /**our API requires a Flow but you want to provide a Sink and Source*/

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }
  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](x => x), Source(1 to 10))
  // There is no connection b/w elements, Danger is if elements go into sink are finished,
  // source has no way of stopping stream if this flow connects various parts of graph. That's why we use
  // fromSinkAndSourceCoupled() which sends termination signals and backpressure signals between these two
  // unconnected components.
}
