package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

object FirstPrincipleExercise extends App {

  /*
  Create akka stream that takes the names of persons,
  then we will keep first 2 names with length greater than 5 characters
  print them to console
   */
  implicit val system: ActorSystem = ActorSystem("Exercise")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val persons: List[String] = List("person1", "obaid", "person2", "fayaz", "person3")

  /*val source = Source(persons)
  val sink = Sink.foreach[String](println)
  val takeFlow = Flow[String].take(2)
  val filterFlow = Flow[String].filter(x => x.length > 5)
  source.via(filterFlow).via(takeFlow).to(sink).run()*/

  /*val filterSource = Source(persons).filter(_.length > 5).via(Flow[String].take(2))
  filterSource.runForeach(println)*/

  Source(persons).filter(_.length>5).take(2).runForeach(println)

}
