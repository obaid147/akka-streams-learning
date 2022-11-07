package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import part4_techniques.IntegratingWithActors.DestinationActor.{StreamAck, StreamCompleted, StreamFailed, StreamInit}

import scala.concurrent.duration.DurationInt

object IntegratingWithActors extends App {
  implicit val system: ActorSystem = ActorSystem("IntegratingWithActors")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  class MyActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a String: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! 2 * n
      case _ =>
    }
  }
  val myActor = system.actorOf(Props[MyActor], "myActor")
  val numberSource = Source(1 to 10)

  /** actor as a flow **/
  implicit val timeout: Timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(myActor) // Flow[Int]->receives Int, ask[Int]->returns Int.(destination)
  // parallelism means how many messages can be in actors mailbox at a particular time before the actor starts back pressuring.

  // numberSource.via(actorBasedFlow).to(Sink.ignore).run()
  // numberSource.ask[Int](parallelism = 4)(myActor).to(Sink.ignore).run() // ask directly
  /* In reality when we do numberSource.ask, What akka does is ask.viaFlow*/


  /** actor as a source **/
  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  val materializedValue = actorPoweredSource.to(Sink.foreach[Int](n => println(s"Actor powered flow got number $n")))
  /*val materializedActorRef: ActorRef = materializedValue.run()
  materializedActorRef ! 10//materializedActorRef is waiting for messages, we sent 10 & got Actor powered flow got number 10
  materializedActorRef ! akka.actor.Status.Success("Completed")// terminating the stream*/


  /** actor as a sink **/
  /* Actor as a destination
  * - an init message
  * - an ack message to confirm the reception
  * - a completed message
  * - a function to generate a message in case the stream throws an exception.
  * */
  object DestinationActor {
    case object StreamInit
    case object StreamAck
    case object StreamCompleted
    case class StreamFailed(x: Throwable)
  }


  class DestinationActor extends Actor with ActorLogging {
    import DestinationActor._

    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized.")
        sender() ! StreamAck
      // we need to send ack msg back to stream otherwise they will be interpreted as back pressure
      case StreamCompleted =>
        log.info("Stream completed.")
        context.stop(self)
      case StreamFailed =>
        log.warning("Stream Failed.")
      case message =>
        log.info(s"Message $message has come to its final resting point.")
        sender() ! StreamAck
        // we need to send ack msg back to stream otherwise they will be interpreted as back pressure
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "DesActor")
  val actorPoweredSink = Sink.actorRefWithAck[Int]( // add lifecycle messages
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamCompleted,
    ackMessage = StreamAck,
    onFailureMessage = throwable => StreamFailed(throwable)//Fail is optional
  )

  numberSource.to(actorPoweredSink).run()

  // Sink.actorRef // not recommended, not able to back pressure

}
