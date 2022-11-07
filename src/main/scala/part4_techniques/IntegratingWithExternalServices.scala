package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object IntegratingWithExternalServices extends App {
  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  //import system.dispatcher // not recommended for mapAsync
  // own execution context ---> application.conf
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericService[A,B](element: A): Future[B] = ???

  /*
   example:- simplified PagerDuty. (Service to manage on call engineers)
   if something breaks in code/production..., an alert is issued and an on call engineers is notified
    via emailed/phone-call/some other kind of communication.
   */
  case class PagerEvent(application: String, description: String, date: Date)
  //                    app that broke,      desc of same app,    date at app broke.
  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal element in the pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFrontend", "A button doesn't work", new Date),
  ))

  object PagerService {
    private val engineers = List("John", "Mike", "Don")
    private val emails: Map[String, String] = Map(
      "John" -> "john@company.com", "Mike" -> "mike@company.com", "Don" -> "don@company.com"
    )
    def processEvent(pagerEvent: PagerEvent): Future[String] = Future{
      //val engineerIndex = pagerEvent.date.getDay
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page/notify the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000) // time spent to page the engineer.
      engineerEmail // return the email that was paged.
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 1)(event => PagerService.processEvent(event))
  /**
  * mapAsync The function that is applied to each event returns a Future that's y we use mapAsync.
  * mapAsync guarantees the relative order of output elements regardless of which future is faster or slower.
  * mapAsyncUnordered works the same way but does not confirm the order of output elements. but is FASTER...***
  * parallelism determines how many Futures can be run at the same time & if 1 Future fails, whole stream fails...*/

  val pagedEmailSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))
  pagedEngineerEmails.to(pagedEmailSink).run()

  /** Running Futures in streams implies that we may end up running lot of futures.
   * We should run futures in their own execution context not on the actorSystem dispatcher, they may starve for threads.
   * */
}


object MapAsyncWithAskingActors extends App {
  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val dispatcher: MessageDispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal element in the pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFrontend", "A button doesn't work", new Date),
  ))

  class PagerActor extends Actor with ActorLogging{
    private val engineers = List("John", "Mike", "Don")
    private val emails: Map[String, String] = Map(
      "John" -> "john@company.com", "Mike" -> "mike@company.com", "Don" -> "don@company.com"
    )

    private def processEvent(pagerEvent: PagerEvent) =  {
      //val engineerIndex = pagerEvent.date.getDay
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page/notify the engineer
      log.info(s"Sending engineer $engineer email: $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000) // time spent to page the engineer.
      engineerEmail // return the email that was paged.
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")

  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")

  import akka.util.Timeout
  import akka.pattern.ask

  implicit val timeout: Timeout = Timeout(3.seconds)
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])

  val pagedEmailSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))
  alternativePagedEngineerEmails.to(pagedEmailSink).run()

  // do not confuse mapAsync with Async(ASYNC boundary) which makes component/chain Stream to run on separate actor.
}
