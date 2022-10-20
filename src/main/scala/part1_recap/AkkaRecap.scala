package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.ask
import akka.util.Timeout

object AkkaRecap extends App {
  case object CreateChild
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case CreateChild =>
        val child = context.actorOf(Props[Child], "ChildActor")
        child ! s"Hello ${child.path.name}"

      /*case "stashThis" => stash()
      case "change actor now" =>
        unstashAll()
        context.become(anotherHandler)*/

      case "change" => context.become(anotherHandler)
      case message => println(s"Received $message")
    }
    def anotherHandler: Receive = {
      case "new" =>
        println("In another handler")
        context.unbecome()
    }

    override def preStart(): Unit = println("I am starting...")

    /** When child actor throws an exception, Its suspended and parent actor is notified
     * supervisorStrategy kicks in and based on the cases and type of strategy,
     * the child actor is either restarted, resumed, stopped..... etc....
     * */
    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(){
      case _: RuntimeException => Restart
      case _ => Stop
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(message)
    }
  }

  // actor encapsulation
  val system = ActorSystem("SimpleActorRecap")
  // #1 we can only instantiate an actor through the actor system
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
  // #2 sending message is the only way to communicate with an actor
  simpleActor ! "hey1!!"
  // tell method, this sends an asynchronous message to the actor an the actor free to receive it at anytime in future.

  /*
    - messages are sent asynchronously.
    - many actor(in millions) can share a few dozen threads.
    - each massage is processed ATOMICALLY
      Akka guarantees no race conditions which means need to locking resources is not required.
   */

  // change actor behavior
  simpleActor ! "change" // change the handler
  simpleActor ! "new" // message handler is still the new handler...
  simpleActor ! "XYZ" // because of unbecome this works

  // stashing (stash(), unstashAll())

  // Actors can spawn other actors === parent child relationship
  simpleActor ! CreateChild


  // Once we create ActorSystem, Akka create three top levels parent for us,
  // these are called guardian ==>  /system, /user, /
  // user is parent of every actor we create, / is root guardian which is parent of all actors in ActorSystem.

  // Actor have a defined lifecycle, they can be
  // Started, Stopped, Suspended, Resumed, Restarted ...

  // Stopping, context.stop, actor ! PoisonPill, actor ! Kill

  // Logging => mixing in ActorLogging gives us debug(), info(), warning(), error()

  //Supervision -> How parent actors are going to respond to child actor failures "supervisorStrategy()"

  // config akka infrastructure: dispatchers, routers, mailboxes.

  // schedulers
  import system.dispatcher

  import scala.concurrent.duration._
  system.scheduler.scheduleOnce(5.seconds){
    simpleActor ! "delayed happy birthday"
  }

  //akka patterns, FiniteStateMachine & akka Ask pattern
  implicit val timeout = Timeout(3 seconds)
  val future = simpleActor ? "question"

  // Pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherActor")
  future.mapTo[String].pipeTo(anotherActor)
  /** when future is completed, its value is being sent to otherActor as a message */
}
