package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, Zip}

object GraphBasics extends App {
  /** Writing complex Akka streams graphs
   * familiarize with Graph DSL.. akka-streams API
   *
   * non-linear components:-
   * fan-out:- single input and  multiple outputs.
   * fan-in:-  multiple inputs and single output.
   *
   * What we are doing is merging incrementer & multiplier to return Tuple2[Int,Int]
   * and then again broadcasting them to return a single value out of Tuple2[Int, Int]
   * */

  implicit val system: ActorSystem = ActorSystem("GraphBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(x => x + 1) // hard computation
  val multiplier = Flow[Int].map(x => x * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)

  /** STEP!1 - setting up fundamentals for the graph*/
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = Mutable data structure
      // must return shape
      // STEP 2, 3, & 4 is to mutate STEP 1 builder.
      import GraphDSL.Implicits._ // brings nice operators into scope

      /** STEP!2 - add the necessary components of this graph*/
      val broadcast = builder.add(Broadcast[Int](2)) /**fan-out operator, single input and 2 outputs*/
      val zip = builder.add(Zip[Int, Int]) /**fan-in operator, 2 inputs and single output*/

      /**STEP!3 - tying up the components*/
      input ~> broadcast
      // input(source) feeds into broadcast, broadcast feeds into increment and multiplier,
      // then inc and mul feeds into zip one by one,
      // then zip.out feeds into the sink.

      broadcast.out(0) ~> incrementer ~> zip.in0 // first value of tuple
      broadcast.out(1) ~> multiplier ~> zip.in1 // 2nd value of tuple

      zip.out ~> output // both values fed into sink

      /**STEP!4 return a closed shape*/
      ClosedShape // FREEZE builder's shape
    } //# 1 we create a graph
  ) //#2 from graph we create a runnable graph

  graph.run() // # 3 run the graph and materialize it.

  /** complex_graph_mechanism.jpg
   * we have a source that ~> feeds into fan-out(broadcast) component which take one input and have two outputs.
   * The input goes/Feeds(~>) into flow 1 and flow 2 to process.
   * After processing, both Flows Feed ~> into fan-in(ZIP) component that takes 2 inputs and one output.
   * The fan-in zips the inputs from 2 flows into a Tuple2 and Feeds ~> into sink.
   * After that we ClosedShape meaning FREEZE builder's shape which makes builder immutable.
   * */
}
