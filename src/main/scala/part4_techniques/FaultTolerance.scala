package part4_techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.util.Random

object FaultTolerance extends App {
  /** React to failures in streams */
  implicit val system: ActorSystem = ActorSystem("FaultTolerance")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /** #1- logging -> Monitoring elements completion, failure.*/
  val faultySource = Source(1 to 10).map(e => if(e == 5 ) throw new RuntimeException else e)
  // at 6, It terminates the whole stream...

  faultySource.log("trackingElements").to(Sink.ignore)//.run()
  /*
  *  Elements that go out of source are logged at DEBUG level.
  *  Elements that fail are logged at ERROR level.
  *  Default log level is INFO, we need to make changes in application.conf
  * */

  /**Recovering from logging exception
   * #2- Gracefully terminating a stream.... */
  faultySource.recover{
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource")
    .to(Sink.ignore)//.run()

  /** Replacing the entire stream with another stream
   * #3- recoverWith another Stream... */
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("recoverWithReturns")
    .to(Sink.ignore)//.run()
  /*If we plug this with consumer, If the faultySource fails, It will be with new Source == Source(90 to 99)
  * and will be attempted 3 time(first arg of recoverWithRetries())*/

  import scala.concurrent.duration.DurationInt
  /** #4 backoff supervision*/
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1.second, // first 1sec, 2nd 2secs, 3rd 4secs, 4th 4secs... till stream completes.
    maxBackoff = 30.seconds, // total seconds 30.
    randomFactor = 0.2 // prevents multiple resources to restart at same time if they fail
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(ele => if(ele == randomNumber) throw new RuntimeException else ele)
  })

  restartSource.log("restartBackoff")
    .to(Sink.ignore)
    //.run()

  /** #5- Supervision Strategy
   * Unlike Actors, akka-streams operators are not automatically subjected to a supervision-strategy
   * But they must be explicitly documented to support them and by-default they just fail.*/
  val simpleNumbers = Source(1 to 20).map(ele => if(ele == 10) throw new RuntimeException("Number10") else ele)
    .log("simpleNumbers")

  val supervisedNumbers = simpleNumbers.withAttributes(ActorAttributes.supervisionStrategy{
    // resume = skip faulty element,
    // stop = top the stream,
    // restart = same as resume nut will clear the internal state of the component.
    case _: RuntimeException => Resume
    case _ => Stop
  })
  // withAttributes() allows us to specify a range of configurations.

  supervisedNumbers.to(Sink.ignore)
    .run() /**notice the number 10 is skipped*/
}
