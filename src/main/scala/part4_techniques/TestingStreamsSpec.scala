package part4_techniques

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TestingStreamsSpec
  extends TestKit(ActorSystem("TestingAkkaStreams"))
  with WordSpecLike with BeforeAndAfterAll {
  /* using these libraries
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion, ===> for special testable sinks and sources.
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,  ===> to write test actors.
    "org.scalatest" %% "scalatest" % scalaTestVersion   ===> to define test suite.
  */

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should{
    "satisfy basic assertions" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a+b)
      val futureSum = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(futureSum, 2.seconds)
      assert(sum == 55)
    }// Run akka stream, extract materialized value, run assertions on it...

    "integrate with test actors via materialized value" in {
      implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
      import akka.pattern.pipe
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)
      val probe = TestProbe()
      //A test probe is essentially a queryable mailbox which can be used in place of an actor and the
      // received messages can then be asserted...
      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref) // materialized value
      // PipeTo feeds eventually value of a future to an actor as a message.
      probe.expectMsg(55)
//      probe.expectMsgType[Int]
    }
    "integrate with a test-actor-based sink" in {
      val mySource = Source(1 to 5)
      val flow = Flow[Int].scan[Int](0)(_+_) // 0, 1, 3, 6, 10, 15.
      val streamUnderTest = mySource.via(flow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "CompletionMessage")

      streamUnderTest.to(probeSink).run()
      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with Streams Testkit Sink" in {
      val sourceUnderTest = Source(1 to 5).map(_*2)
      val testSink = TestSink.probe[Int]
      //sourceUnderTest.to(testSink)
      val materializedTestValue = sourceUnderTest.runWith(testSink)
      //runWith automatically selects materialized value of testSink
      materializedTestValue.request(5) // requesting 5 elements
      /** Sends a signal to testSink to request some demand from the sourceUnderTest*/
        .expectNext(2, 4, 6, 8, 10) // assertion method
        .expectComplete()// expect the termination of Stream after we get 2, 4, 6, 8, 10.

      // if we request FEWER messages then the expectComplete will not be fulfilled
        //      request(4); expectNext(2,3,6,8); expectComplete() will not be fulfilled. {timeout}
      // if we request MORE messages then the expectComplete will not be fulfilled
        //      request(4); expectNext(2,3,6,8,10); expectComplete() will not be fulfilled. {timeout}

    }

    "integrate with Streams Testkit Source" in {
      import system.dispatcher
      val sinkUnderTest = Sink.foreach[Int] {
        case 10 => throw new RuntimeException("Number 10 !!!")
        case _ =>
      }

      val testSource = TestSource.probe[Int]
      val materializedTestValue = testSource.toMat(sinkUnderTest)(Keep.both).run() // tuple2
      val (testPublisher,resultFuture) = materializedTestValue // decompose tuple2

      testPublisher.sendNext(1)
        .sendNext(5)
        .sendNext(12)
        .sendNext(10)
        .sendComplete()

      resultFuture.onComplete{
        case Success(_) => fail("the sink under test should have thrown exception on 10")
        case Failure(_) => //ok
      }
    }

    /**combining -Streams Testkit Source & -Streams Testkit Sink by testing a flow*/
    "test flows with a test source & a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 3)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val materializedValue = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (publisher, subscriber) = materializedValue

      publisher.sendNext(100)
        .sendNext(200)
        .sendNext(300)
        .sendNext(400)
        .sendComplete()
      // subscriber needs to request number of messages
      subscriber
        .request(4)
        .expectNext(300, 600, 900, 1200)
        .expectComplete()
    }
  }

}
