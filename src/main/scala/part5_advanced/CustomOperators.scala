package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream._

import scala.collection.mutable
import scala.util.Random

object CustomOperators extends App {

  implicit val system: ActorSystem = ActorSystem("CustomOperators")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /** #1 A custom SOURCE which emits random numbers until cancelled.*/
  class RandomNumberGenerator(max: Int) extends GraphStage[/*Step-0 define the shape*/SourceShape[Int]] {
    /* Step-1 define the ports and component-specific members*/
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    /* Step-2 construct a new shape*/
    override def shape: SourceShape[Int] = SourceShape(outPort)

    /* Step-3 create the logic*/
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      /* Step-4 implement logic by setting handlers on our ports*/
      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          //emit a new element
          val nextNumber = Random.nextInt(max)
          //push it out of the outPort which will end up in sink
          push(outPort, nextNumber)

        } // onPull will be called when there is demand from downStream(Sink asking for new element in our case)
      }) // method from GraphStageLogic class
    }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(10))

//  val materializingRandomGeneratorSource =
//    randomGeneratorSource.runWith(Sink.foreach[Int](println))
  /**
   * Once we plug Source to any component and start the graph, that means we are materializing randomGeneratorSource.
   * When randomGeneratorSource is materialized, createLogic() method will be called by AKKA.
   * Then the GraphStageLogic will be constructed.
   * GraphStageLogic is fully implemented so, we don't need to implement any method, BUT
   * We should specify what should happen at the construction of this graph stage logic object.
   *
   * As long as there is demand from consumer, will keep supplying that port with new numbers.
   * */



  /** #2 A custom SINK that will print elements in batches of a given size.*/
  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {
    val inPort = Inlet[Int]("Batcher")

    override def shape: SinkShape[Int] = SinkShape(inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // preStart to send signal
      override def preStart(): Unit = pull(inPort) // signal demand to upstream for new element

      // mutable states
      val batch = new mutable.Queue[Int]

      setHandler(inPort, new InHandler {
        // when there is demand from upstream
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          /*Assume some complex computation here*/
          Thread.sleep(100)
          if(batch.size >= batchSize) println(s"New Batch ${batch.dequeueAll(_ => true).mkString("{", ", ", "}")}")

          pull(inPort) // send demand upstream...
        }

        // When stream terminates, we can flush out the batch
        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty){
            println(s"New Batch ${batch.dequeueAll(_ => true).mkString("{", ", ", "}")}")
            println("STREAM FINISHED...")
          }

        }
      })
    }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))

  randomGeneratorSource.to(batcherSink).run()
  /** Noting happens when we do this because:-
   * We are connecting batcherSink to a randomGeneratorSource where
   * randomGeneratorSource is waiting for upstream to send an element, (chicken and egg problem)
   * Each component is waiting for the other to give first signal.
   * line 64 signal demand to upstream for new element.
   * once this sends the first signal, the random number generator, will respond to that and will push another
   * element, which means that  onPush will be called and so on and so forth.
   *
   *
   * if Batcher takes a long time to run, the back pressure signal will automatically be sent to the
   * randomNumberGenerator and it will slow it down. (76, 77). Batches will print slowly.
   * slow consumer BACKPRESSURE a fast producer
   * */


  /*API Overview*/
  /*Start with some input toward methods.

   So in handlers which are attached to input-ports will need to interact with the upstream.
   That means you probably need to support one or more of these callbacks

    InHandler interacts with UPSTREAM:-(Callbacks)
    onPush:- which will be called when you receive an element on that input-port.
    onUpstreamFinish:- when upstream terminates.
    onUpStreamFailure:- when upstream throws an error...

   @@@@ Input Ports can check and retrieve elements:-
    METHODS---->
    pull:- signal demand from upstream
    grab:- takes an element. Fails if there is no elements to grab
    cancel:- tell upstream to terminate the entire upstream.
    isAvailable:- Check if input-port is available, Whether an element has been pushed to the port
    hasBeenPulled:- Whether demand has already been signaled on port
    isClosed:- checks if closed or not.

    OutHandler interacts with downstream:-(callbacks)
     OnPull:-This is mandatory and is called when demand has been signaled on this port.
     onDownstreamFinish:- There is no equivalent for the failure callback because if the downstream fails,
                          It will receive a cancel signal

   @@@@ Output Ports can send elements:-
    METHODS---->
    push:- Send an element
    complete:- Signal the termination of stream.
    fail:- Terminates the stream when an exception.
    isAvailable:- Checks if an element has already been pushed to the port.
    isClosed:-  checks if closed or not




   */
}
