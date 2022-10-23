package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {

  implicit val system: ActorSystem = ActorSystem("OperatorFusion")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleFlow2 = Flow[Int].map(x => x * 10)
  val simpleSink = Sink.foreach[Int](println)

  /** Operator Fusion*/
  /*simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run() // this runs on SAME ACTOR (Operator Fusion)
  // This is same as below
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        // flow operations
        val x2 = x + 1
        val x3 = x2 * 10
        // sink operation
        println(x3)
    }
  }
  // .run() equals to creating instance of actors
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")
  (1 to 1000).foreach(actor ! _) // will return same output as above*/

  // complex flows:-
  /*val complexFlow = Flow[Int].map{ x =>
    // simulating long computation by doing thread sleep
    Thread.sleep(1000)
    x + 1
  }
  val complexFlow2 = Flow[Int].map { x =>
    // simulating long computation by doing thread sleep
    Thread.sleep(1000)
    x * 10
  }*/

  // There will be 2sec delay on every output as both the Thread.sleeps are operating on same actor
  //simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  /** When operators are expensive, it is better to run them in parallel on different actors and for that,
   * We need async boundary */
  /*simpleSource.via(complexFlow).async // runs on one actor
    .via(complexFlow2).async // runs on another actor
    .to(simpleSink) // runs on third actor
    .run() // We are able to double our through-put
    /* run this now, we can see 1sec delay instead of 2sec delay on each element*/
*/

  /** Ordering Guarantees, without async boundaries, WE GET A1 B1 C1.....*/
 /* Source(1 to 3)
    .map{ element =>
      println(s"Flow A $element")
      element
    }
    .map { element =>
      println(s"Flow B $element")
      element
    }
    .map { element =>
      println(s"Flow C $element")
      element
    }
    .runWith(Sink.ignore)*/

  /** Ordering Guarantees, with async boundaries, WE GET A1 A2 A3, B1 B2 C1, B3 C2 C3.....*/
  Source(1 to 3)
    .map { element =>
      println(s"Flow A $element")
      element
    }.async
    .map { element =>
      println(s"Flow B $element")
      element
    }.async
    .map { element =>
      println(s"Flow C $element")
      element
    }.async
    .runWith(Sink.ignore)

  /**
   * When akka stream components are started in the stream ie: materialized, they are fused which means they are run
   * on the same actor.
   * So, We introduced async boundaries which we can use by calling async method on components in the stream.
   * By introducing an async boundaries, components will run on different actors and that yields better throughput
   * when operations are time consuming.
   *
   * So this is the kind of situations when async boundaries work best, when individual operations are expensive.
   * So we want to make them run in parallel, but we want to avoid async boundaries and stick to operator Fusion
   * when operations are comparable with a message.
   *
   * And finally, ordering guarantees in streams with and without async boundaries.
   */

}
