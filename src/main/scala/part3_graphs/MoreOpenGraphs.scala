package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, UniformFanInShape}

import java.util.Date

object MoreOpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem("MoreOpenGraph")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /*
  Example:- Max3 operator
  * - 3 inputs of type int.
  * - and whenever we have value in each of these inputs, this component will push out
  * - the maximum of the 3.
   */

  //STEP1
  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // STEP2 define auxiliary shapes
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => math.max(a, b)))

    // STEP3
    max1.out ~> max2.in0
    // The output from first comparison max1 is going to feed into one of the inputs of the max2

    // STEP4 define a shape that has 3 inputs and a single output.
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
    // (output is from max2 shape) and (two inputs from max1 & unconnected input of max2)

  }

  val s1 = Source(1 to 10)
  val s2 = s1.map(_ => 5)
  val s3 = Source((1 to 10).reverse)
  val sink = Sink.foreach[Int](x => println(s"max is $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val max3Shape = builder.add(max3StaticGraph) // our own components can be used here...

      s1 ~> max3Shape.in(0)
      s2 ~> max3Shape.in(1)
      s3 ~> max3Shape.in(2)
      max3Shape.out ~> sink

      ClosedShape
    }
  )

  max3RunnableGraph.run()
  // same for the UniformFanOutShape()
}

object SuspiciousTransaction extends App {
  /*
    * Non-uniform fan-out shape.
    * - Process bank transactions.
    * - suspicious transaction if withdrawal amount > 100000

    * - streams components for transaction
    *  - output1: let the trx go through unmodified
    *  - output2: suspicious txn ids
    */
  implicit val system: ActorSystem = ActorSystem("suspiciousTxn")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("19191919", "Kal", "Jik", 100, new Date),
    Transaction("20292929", "Zal", "Jik", 100000, new Date),
    Transaction("98199101", "Jik", "Bob", 70000, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txId => println(s"suspicious transaction id: $txId"))

  // 1
  val suspiciousTransactionStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    //2 define shapes
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTransactionFilter = builder.add(Flow[Transaction].filter(txn => txn.amount >= 100000))
    val transactionIdExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    //3 tie shapes together
    broadcast.out(0) ~> suspiciousTransactionFilter ~> transactionIdExtractor

    // 4 return shape
    new FanOutShape2(broadcast.in, broadcast.out(1), transactionIdExtractor.out)

  }

  val suspiciousTransactionRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val suspiciousTransactionShape = builder.add(suspiciousTransactionStaticGraph)

      transactionSource ~> suspiciousTransactionShape.in
      suspiciousTransactionShape.out0 ~> bankProcessor
      suspiciousTransactionShape.out1 ~> suspiciousAnalysisService

      ClosedShape
    }
  )
  suspiciousTransactionRunnableGraph.run()

}

