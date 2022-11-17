package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Graph, Inlet, Outlet, Shape}

import scala.collection.immutable

/** Creating own shapes
 * 1. Define a case class in most cases where you want to expose input and output ports.
 * 2. Use the abstract class Shape (extend)
 * 3. Define inlets and outlets to return a SEQ with a very well defined order of inputs and outputs.
 *    and method deepCopy which simply copy in-output ports in a clone of that class.
 * 4. Implement the case class of the component that will actually have that shape
 *     (using( GraphDSL.create) define logic inside and at the end return our own Shape with in-output shapes.
 * 5. Use the component in a runnable graph
 * */

object CustomGraphShapes extends App {

  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /**
   * fan-in means multiple inputs and a single output.
   * fan-in means single inputs and a multiple outputs.
   *
   * Create components with arbitrary input and output.
   * like multiple inputs and outputs.
   *
   * Example:- Create a generic Balance component with any number of inputs and outputs(M x N)
   * This component would feed a relatively equal number of elements to each of its and outputs,
   * regardless of the rate of production of its M inputs.
   *
   *
   * Normal Balance is a fan-in, we want multiple inputs and outputs, We can use Merge+Balance
   *
   * Merge us fan-out, It can take any number of inputs and Balance can take any number of outputs...
   * There is no such shape to return to GraphDSL
   * */

  // balance 2 x 3 shape
  case class Balance2By3(
    in0: Inlet[Int], // passing ports as members.
    in1: Inlet[Int],
    out0: Outlet[Int],
    out1: Outlet[Int],
    out2: Outlet[Int]
                   ) extends Shape { // shape is an abstract class with methods we need to implement
    // inlet[T] and outlet[T] are classes for the port, inlet 2 and outlet 3
    override val inlets: immutable.Seq[Inlet[_]] = List(in0, in1)

    override val outlets: immutable.Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2By3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  val balance2By3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2By3(merge.in(0), merge.in(1), balance.out(0), balance.out(1), balance.out(2))
  }

  val balance2By3Graph = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder =>
      import GraphDSL.Implicits._

      import scala.concurrent.duration.DurationInt
      val slowSource = Source(Stream.from(1)).throttle(1, 1.second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1.second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, elem: Int) => {
        println(s"Received $elem, current count $count, [Sink $index]")
        count + 1
      })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2By3 = builder.add(balance2By3Impl)

      slowSource ~> balance2By3.in0
      fastSource ~> balance2By3.in1

      balance2By3.out0 ~> sink1
      balance2By3.out1 ~> sink2
      balance2By3.out2 ~> sink3

      ClosedShape
    }
  )

  balance2By3Graph.run()
}



/*Exercise*/
object GeneralizedCustomShape extends App {
  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  case class Balance2x3[T](
    override val inlets: List[Inlet[T]],
    override val outlets: List[Outlet[T]]) extends Shape {

    override def deepCopy(): Shape = {
      Balance2x3(
        inlets.map(_.carbonCopy()),
        outlets.map(_.carbonCopy())
      )
    }
  }

  object Balance2x3 {
    def apply[T](inputCount: Int, outputCount: Int): Graph[Balance2x3[T], NotUsed] = {
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val merge = builder.add(Merge[T](inputCount))
        val balance = builder.add(Balance[T](outputCount))

        merge ~> balance

        Balance2x3(merge.inlets.toList, balance.outlets.toList)
      }
    }
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      import scala.concurrent.duration.DurationInt
      val slowSource = Source(Stream.from(1)).throttle(1, 1.second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1.second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, elem: Int) => {
        println(s"[Sink $index] Received $elem, current count $count")
        count + 1
      })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2By3 = builder.add(Balance2x3[Int](2, 3))

      slowSource ~> balance2By3.inlets.head
      fastSource ~> balance2By3.inlets(1)

      balance2By3.outlets.head ~> sink1
      balance2By3.outlets(1) ~> sink2
      balance2By3.outlets(2) ~> sink3

      ClosedShape
    }
  )

  balance2x3Graph.run()
}
