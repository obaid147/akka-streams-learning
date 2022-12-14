package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}
import akka.stream._

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

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

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))

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

  //randomGeneratorSource.to(batcherSink).run()
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


  /** #3 A custom FLOW - a simple filter flow*/
  class FilterFlow[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort: Inlet[T] = Inlet[T]("filterIN")
    val outPort: Outlet[T] = Outlet[T]("filterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = pull(inPort)
      })
      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          try {
            val nxtEle = grab(inPort)
            if (predicate(nxtEle)) push(outPort, nxtEle) // if predicate is met, pass ele  on
            else pull(inPort) // otherwise ask for another element.

          } catch {
            case e: Throwable => failStage(e)
          }
        }
      })
    }
  }

  val myFilter = Flow.fromGraph(new FilterFlow[Int](_ > 50))
  randomGeneratorSource.via(myFilter).to(batcherSink)//.run()
  // backpressure also works out of the box.... emits 10 elements per second


  /** Materialized values in graph stages*/
  /** #4 Flow that counts number of elements that go through it*/
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort: Inlet[T] = Inlet[T]("counterIN")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)
    // val because we don't want shape to be evaluated everytime...

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise: Promise[Int] = Promise[Int]
      val logic: GraphStageLogic = new GraphStageLogic(shape){
        // setting mutable state
        var counter = 0
        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }
        })
        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            // extract the element
            val nxtEle = grab(inPort)
            counter += 1
            push(outPort, nxtEle)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }
      (logic, promise.future)
    }
  }

  val counterFlow: Flow[Int, Int, Future[Int]] = Flow.fromGraph(new CounterFlow[Int])
  val countFuture: Future[Int] = Source(1 to 10)
    //.map(x => if(x == 8) throw new RuntimeException("Reached 8") else x)// upStream
    .viaMat(counterFlow)(Keep.right)
    //.to(Sink.foreach[Int](println))// if upstream fails, onUpStreamFailure
    .to(Sink.foreach[Int]
      //if downstream fails onDownstreamFinish is called, simply terminates stream as no onDownStreamFailure method.
      (x => if(x == 8) throw new RuntimeException("Sink at 8") else println(x)))
    .run()

  import system.dispatcher
  countFuture.onComplete{
    case Success(count) => println(s"Total Number of elements passed = $count")
    case Failure(ex) => println(s"Counting failed coz:- $ex")
  }

  /** Handler Callbacks onPush onPull etc... are never call concurrently.
   *  so, we can safely access mutable states inside these handlers......
   *
   *  Drawback is that We should never ever expose mutable states outside these Handlers,
   *  eg: Future onComplete callbacks, this can break component encapsulation
   *      much the same way as we can break Actor encapsulation*/
}
