package Setup

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink}

object Setup extends App{
  implicit val actorSystem: ActorSystem = ActorSystem("Setup")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  Source.single("Hello, Streams").to(Sink.foreach(println)).run()
}
