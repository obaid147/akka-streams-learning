package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy, UniformFanInShape}

object GraphCycles extends App {
  implicit val system: ActorSystem = ActorSystem("GraphCycles")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder => import GraphDSL.Implicits._

    val sourceShape = Source(1 to 100)
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map{
      x => println(s"Accelerating $x")
      x +1
    })

    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape

    ClosedShape
  }

  //RunnableGraph.fromGraph(accelerator).run() // When we run it, we have only 1 output> Accelerating 1
  /** This is because of cycle deadlock:-
   * because components start buffering elements and once their buffers are full,
   * they start back pressuring each other and nobody is there to process anymore elements through the Graph.
   * -- We need to override that buffer overflow strategy to dope some elements circulating through the graph to break
   * the dead lock...
   * Solution 1:- MergePreferred
   * Solution 2:- Flow.buffer(size, OverflowStrategy)
   * */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = Source(1 to 100)
    val mergeShape = builder.add(MergePreferred[Int](1)) // It has its preferred port
    val incrementerShape = builder.add(Flow[Int].map {
      x =>
        println(s"Accelerating $x")
        x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }
  //RunnableGraph.fromGraph(actualAccelerator).run()

  val repeaterAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = Source(1 to 100)
    val mergeShape = builder.add(MergePreferred[Int](1)) // It has its preferred port
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
                                                              // drops the oldest element out of the buffer.
        println(s"Accelerating $x")
        Thread.sleep(100)
        x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape.preferred <~ repeaterShape

    ClosedShape
  }
  RunnableGraph.fromGraph(repeaterAccelerator).run()

  /* If we add cycles to our graph, we risk deadlocking
      -add bounds to the number of elements in the cycle. eg 1 & 2

      boundedness vs liveness
  */
}

object CycleExercise extends App {
  implicit val system: ActorSystem = ActorSystem("GraphCycles")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /**
   * create a fan-in shape which takes 2 inputs, which will be fed with exactly 1 number.
   * output will emit infinite fibonacci seq based of those two numbers
   * Use ZipWith, cycles, MergePreferred to avoid dead locks.
   */

  val fibGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip = builder.add(Zip[BigInt, BigInt])
    val mergePreferred = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fibLogic = builder.add(Flow[(BigInt, BigInt)].map{ pair =>
      val last = pair._1
      val prev = pair._2

      Thread.sleep(300)
      (last+prev, last)
    })
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))

    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    zip.out ~> mergePreferred        ~> fibLogic ~> broadcast ~> extractLast
               mergePreferred.preferred    <~       broadcast

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fibGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val src1 = builder.add(Source.single[BigInt](1))
      val src2 = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](println))
      val fib  = builder.add(fibGenerator)

      src1 ~> fib.in(0)
      src2 ~> fib.in(1)
      fib.out ~> sink

      ClosedShape
    }
  )

  fibGraph.run()
}