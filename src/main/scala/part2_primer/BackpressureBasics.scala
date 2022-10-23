package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object BackpressureBasics extends App {

  /** Backpressure is one of the fundamental features of reactive-streams.
   * -- Elements flow as response to demand from consumers.
   * -- Consumers trigger the flow of elements through a stream.
   * -- If I have a simple stream composed of a SOURCE a FLOW and a SINK, elements don't flow through the
   * stream unless there is a demand from that SINK.
   * -- SINK will issue demand from upstream and the FLOW in between will also issue demand from the SOURCE.
   * after that elements will start flowing.
   *
   * Backpressure is all about the synchronization of speed in between these asynchronous components.
   * -- If consumer is fast, all is well because the consumers are processing elements as soon as they are available.
   * -- if consumer is slow, then upstream is producing element faster then the consumer is able to process them.
   * In this case a special protocol will kick in, in which consumer will send a signal to upstream to slowdown.
   *
   * If the sink is slow, it will send a signal upstream(flow) for the producer to slowdown.
   * If the flow is unable to comply, then it will also send the signal to upstream to producer which will limit
   * the rate of production of the elements at the source.
   * So if the source slows down, then the incoming rate of elements at the flow will also slow down, which
   * will also slow down the flow of the entire stream.
   *
   * If the consumer sends more demand than the rate of the stream may increase.
   * This protocol is known as back pressure and is transparent to us programmers, although we can control it....
   * */

  implicit val system: ActorSystem = ActorSystem("BackpressureBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000) //simulating long processing
    println(s"Sink: $x")
  }

  //  fastSource.to(slowSink).run() // fusing
  //  fastSource.async.to(slowSink).run() // Backpressure

  val simpleFlow = Flow[Int].map { x =>
    println(s"incoming $x")
    x + 1 // forwarding x+1 to sink
  }

  fastSource.async
    .via(simpleFlow).async
    .to(slowSink)
  //    .run()
  /*
  * slowSink because it is slow, it sends backpressure signal to upstream. The simpleFlow, when it receives that
  * backpressure signal instead of forwarding the signal to source, It buffers internally no. of elements(incoming 1..)
  * until it had enough(default buffer element size is 16 elements)ie: the internal buffer gets full, it has no choice
  * but to backpressure signal to the fastSource a signal to the fast source and wait for it. Some more incoming
  * demand from the sink. So after the sink process some elements, the simple flow allowed more elements to go through.
  * and it buffered them again because the sink is still slow and this happens a bunch of times.
  * BUFFERING is happening here..
  * Component can react to backpressure in multiple ways. Buffering is one of them...
  * */

  /** Reacting to backpressure (in order):-
   * - 1 try to slow down if possible.
   * - 2 Buffer elements until there is more demand.
   * - 3 drop down elements from buffer if it overflows until there is more demand.
   * - 4 tear down / KILL the whole stream which is called failure.
   *
   * we as akka streams programmers can only control what happens to a component at this point 3
   * if the component has buffered enough elements and it's about to overflow.
   * */

  val bufferedFlow = simpleFlow.buffer(size = 10, overflowStrategy = OverflowStrategy.dropHead) //(number of elements to buffer, overflow strategy)
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
    .run()
  // we used bufferedFLow, It takes 10 elements, If it overflows, the strategy it will use is dropHead
  // This dropHead will drop the oldest element in the buffer to make room for the new one.

  /*
  * sink from 2-17: nobody is backpressure-ed
  * sink from 18-27: flow will buffer, flow will start dropping at next element
  * sink from 28-10000: flow will always drop the oldest element.
  * Contents of the buffer of the flow will be: 992 - 1001 => emitted to sink
  *  */

  /*Sink: 992
  Sink: 993
  Sink: 994
  Sink: 995
  Sink: 996
  Sink: 997
  Sink: 998
  Sink: 999
  Sink: 1000
  Sink: 1001 last 10 elements ended up being in the flow */
  /*Sink: 2
  Sink: 3
  Sink: 4
  Sink: 5
  Sink: 6
  Sink: 7
  Sink: 8
  Sink: 9
  Sink: 10
  Sink: 11
  Sink: 12
  Sink: 13
  Sink: 14
  Sink: 15
  Sink: 16
  Sink: 17 printing of 2 to 17 these were buffered at the sink ie: 16 and the flow buffers last 10 elements*/

  // Over flow strategy:-
  /*
  * dropHead:- drop the oldest
  * dropTail:- drop the newest
  * dropNew:- drop the exact element to be added = keep the buffer
  * dropBuffer:- drop the entire buffer
  * emit backpressure signal
  * fail
  * */
  println("-------------------------")
  // manually trigger backpressure ===> throttling
  import scala.concurrent.duration.DurationInt
  fastSource.throttle(2, 1.second) // emits at most 2 elements per second
    .runWith(Sink.foreach(println))
}
