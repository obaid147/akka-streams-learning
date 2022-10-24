package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}

object GraphBasicsExercise1 extends App {
  /**
   * exercise 1: Feed a source into 2 sinks at the same time
   */
  implicit val system: ActorSystem = ActorSystem("GraphBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val output1 = Sink.foreach[Int](x => println(s"First sink $x"))
  val output2 = Sink.foreach[Int](y => println(s"Second sink $y"))

  val sourceToTwoSinksGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

      import GraphDSL.Implicits._ // which gives us access to ~> operators

      val broadcast = builder.add(Broadcast[Int](2))

      input ~> broadcast ~> output1 // This is called implicit port numbering, which allocates right ports in order.
      broadcast ~> output2

      /*broadcast.out(0) ~> output1
      broadcast.out(1) ~> output2*/

      ClosedShape
    }
  )
  sourceToTwoSinksGraph.run()
}


object GraphBasicsExercise2 extends App {
  /** fast source and slow source,
   * These sources will ~> feed into a fan-in shape known as MERGE.
   * MERGE component will take any one element of its inputs and  will just push it out of its output.
   * This MERGE will ~> feed into a fan-out shape known as BALANCE.
   * BALANCE component will distribute the input elements equally to its outputs.
   * Then we are going to ~> feed them to two sinks.
   * */
  implicit val system: ActorSystem = ActorSystem("GraphBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import scala.concurrent.duration.DurationInt
  val input = Source(1 to 1000)
  val fastSource = input.throttle(5, 1.second)
  val slowSource = input.throttle(2, 1.second)

  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })
  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })

  /** balance graph take 2 different sources that produces outputs at different speed
   * and Even out the rate of production of the elements in between these two sources
   * and splits them equally in between two sinks.
   *
   * What we did is merged two different sources and balanced them.
   * */
  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

      import GraphDSL.Implicits._ // which gives us access to ~> operators

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge
      balance ~> sink2 //we cannot use ~> because merge already feeds into balance.

      ClosedShape
    }
  )
  balanceGraph.run()
}
