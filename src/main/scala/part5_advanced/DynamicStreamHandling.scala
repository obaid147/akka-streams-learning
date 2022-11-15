package part5_advanced

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object DynamicStreamHandling extends App {
/**
 * Stop or abort a stream at runtime.
 * Dynamically add fan-in / fan-out branches.
*/

  implicit val system: ActorSystem = ActorSystem("DynamicStreaming")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  // #1 Kill switch
  /*
  * Kill switch is a special kind of flow that emits the same elements that go through it but
  * it materializes to a special value that has some additional methods.
  */

  val killSwitchFlow = KillSwitches.single[Int] // kills a single stream
  // Flow that receives elem of type Int & push to the o/p whatever the input is. It materializes to UniqueKillSwitch
  val counter = Source(Stream.from(1)).throttle(1, 1.second).log("Counter")
  val sink = Sink.ignore

  /*val killSwitch: UniqueKillSwitch = counter.viaMat(killSwitchFlow)(Keep.right).to(sink).run()

  system.scheduler.scheduleOnce(3.seconds){
    killSwitch.shutdown() // shutdown the stream
  }// need implicit EC*/


  // kill multiple streams, We don't need materialized value here.....********(SHARED KILL SWITCH)
  val anotherCounter = Source(Stream.from(1)).throttle(2, 1.second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("SingleKillSwitchForMultipleStreams")

  counter.via(sharedKillSwitch.flow).runWith(sink)//to(sink).run()
  anotherCounter.via(sharedKillSwitch.flow).runWith(sink)

  system.scheduler.scheduleOnce(3.seconds){
    sharedKillSwitch.shutdown()
  }

  /**Components can be materialized to any other value or even to component also..............
   * In the above case (to UniqueKillSwitch)
   * In the below case (to a Sink) and the materialized sink can be plug it to any flow/source...
   *
   * */
  // #2 Dynamically add fan-in/fan-out (MergeHub)
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  val materializedSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run()

  Source(1 to 5).runWith(materializedSink)
  counter.runWith(materializedSink)

  // BroadcastHub
  val dynamicBroadcast: Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]
  val materializedSource: Source[Int, NotUsed] = Source(1 to 100).runWith(dynamicBroadcast)

  val y: Future[Done] = materializedSource.runWith(sink)
  materializedSource.runWith(sink)
  materializedSource.runWith(Sink.foreach[Int](println))
}


object CombineMergeBroadcastHub extends App{
  /** A publisher-subscriber component
   * We can dynamically add Sources and Sinks to this component
   * Every single element produced by Source will be known by every single subscriber*/

  implicit val system: ActorSystem = ActorSystem("CombineMergeBroadcastHub")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val mergeHub = MergeHub.source[String].log("MergeHub")
  val bCastHub = BroadcastHub.sink[String]

  val (publisherPort, subscriberPort) = mergeHub.toMat(bCastHub)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(ele => println(s"received $ele")))
  subscriberPort.map(s => s.length).runWith(Sink.foreach(n => println(s"got number $n")))

  Source(List("Akka", "is", "great")).runWith(publisherPort)
  Source(List("I", "Love", "Scala")).runWith(publisherPort)
  Source.single("Streammmmmmmmmmmmeeeeeeees").runWith(publisherPort)
}