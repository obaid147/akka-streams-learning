package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}

object BidirectionalFlows extends App {

  implicit val system: ActorSystem = ActorSystem("BidirectionalFlows")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def encrypt(n: Int)(string: String): String = string.map(char => (char + n).toChar)
  def decrypt(n: Int)(string: String): String = string.map(char => (char - n).toChar)
  /*println(encrypt(3)("Akka"))
  println(decrypt(3)("Dnnd"))*/

  /*bidirectional flow (BiDiFlow)*/
  val biDiCryptoStaticGraph = GraphDSL.create() { implicit builder => //import GraphDSL.Implicits._

    val encryptionFLowShape = builder.add(Flow[String].map(elem => encrypt(3)(elem)))
    val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3))) // curried (elem)

    // BiDiShape(encryptionFLowShape.in, encryptionFLowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    BidiShape.fromFlows(encryptionFLowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("Akka", "is", "good")
  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBiDiGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShapes = builder.add(unencryptedSource)
      val encryptedSourceShapes = builder.add(encryptedSource)
      val biDi = builder.add(biDiCryptoStaticGraph)
      val encryptedSinkShape = Sink.foreach[String](s => println(s"encrypted: $s"))
      val decryptedSinkShape = Sink.foreach[String](s => println(s"decrypted: $s"))

      unencryptedSourceShapes ~> biDi.in1;  biDi.out1 ~> encryptedSinkShape
      decryptedSinkShape      <~ biDi.out2; biDi.in2  <~ encryptedSourceShapes
      /*encryptedSourceShapes ~> biDi.in2
      biDi.out2 ~> decryptedSinkShape*/

      ClosedShape
    })
  cryptoBiDiGraph.run()

  /*
  * These operations are mostly used for:-
  * - encryption/decryption
  * - encoding/decoding
  * - serializing/deserializing
  * Whenever we have some kind of reversible operation that we have to do using akka-streams,
  * think of a bidirectional flow.
  * */
}
