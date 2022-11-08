package part4_techniques

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Date

object AdvancedBackpressure extends App {
  /* Advanced Backpressure techniques:-
  * #1 Use buffers to control the rate of the flow of elements inside a STREAM.
  * #2 How to cope with a fast source which cannot be back pressured.
  * #3 How to deal with fast consumer by Extrapolating and Expanding.
  */

  implicit val system: ActorSystem = ActorSystem("AdvancedBackpressure")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // control backpressure if components in a stream are backpressure-able.
  val controlledFlow = Flow[Int].map(_ * 2).buffer(size = 10, overflowStrategy = OverflowStrategy.dropHead)

  case class PagerEvent(desc: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements in the data pipeline", new Date),
    PagerEvent("Number of HTTP 500 spiked", new Date),
    PagerEvent("A service stopped responding", new Date)
  )

  val eventSource = Source(events)

  val onCallEngineer = "dia@example.com" // lets say we have a fast service for fetching onCall Email
 // #1
  def sendEmail(notification: Notification): Unit =
    println(s"Dear, ${notification.email} -> event notification:-" +
      s" ${notification.pagerEvent.desc}, ${notification.pagerEvent.date}")//send an email

//  val flow = Flow[PagerEvent]
//  val sink = Sink.foreach[Notification](sendEmail)
  val notificationSink = Flow[PagerEvent].map(event => Notification(onCallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  // Standard way
  //eventSource.to(notificationSink).run()



  /**Timer based sources do not respond to backpressure*/
    // un-back-pressure-ble source
    /**
     * If the source cannot be backpressured, We should aggregate the incoming events & create single notification
     * when we receive demand from the sink
     * */
  // #2
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Dear, ${notification.email} -> event notification:-" +
      s" ${notification.pagerEvent.desc}, ${notification.pagerEvent.date}")
  }

  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      // conflate acts like fold, it combines elements but it emits the result only when downstream sends demand.
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"You have $nInstances events that require your attention", new Date, nInstances)
    })
    .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  eventSource.via(aggregateNotificationFlow).async // run this part of stream on one actor
    .to(Sink.foreach[Notification](sendEmailSlow))//.run() // and run this part of stream on another
  // This was an alternative backpressure


  /**
   * When Source is slow, We use extrapolate and expand techniques.

   * Extrapolate:-run a function from the last element emitted from upstream to compute further
   *              elements to be emitted downstream.
   * */

  import scala.concurrent.duration.DurationInt
  val slowCounter = Source(Stream.from(1)).throttle(1, 1.second)
  val hungrySink = Sink.foreach[Int](println)

  // extrapolator creates this iterator Iterator.from(element)) when there is unmet demand
  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element)) // or
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))
  // numbers will be repeated till source produces a new number
  slowCounter.via(repeater).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))
  /* expand works the same way as an extrapolator but it creates the Iterator all the time.*/
}
