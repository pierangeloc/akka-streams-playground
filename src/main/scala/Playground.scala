import akka.pattern._
import akka.stream.OperationAttributes
import akka.stream.scaladsl.FlowGraph.Implicits._
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
/**
 * Created by pierangelo on 17-5-15.
 */
object Playground extends App with BaseStreamingFacilities {

  import scala.concurrent.ExecutionContext.Implicits.global

  val numbers = Source(List(1, 2, 3, 4, 5))
  val strings = Source(List("a", "b", "c"))

  numbers.runWith(Sink.foreach(println(_)))
//  numbers.to(Sink.foreach(println(_))).run()
//  run translates the description of the flow into actors, using the flow materializer

  val composite = Source() { implicit builder =>
    val zip = builder.add(Zip[Int, String]())

    numbers ~> zip.in0
    strings ~> zip.in1

    zip.out
  }

  composite.runWith(Sink.foreach(println(_)))


  val fastSource = Source(() => Iterator.from(0))
  //  this takes a full cpu
  //  fastSource.runForeach(println(_))


  //  back pressure in action: we slow down the iterator, so the cpu is at rest
  //  val slowedDown = fastSource.map(x => {Thread.sleep(1000); x })

  //  done nicely:
  val slowedDown = fastSource.mapAsync(20)(x => after(1 second, actorSystem.scheduler)(Future.successful(x)))
  //if we do instead mapAsync(1) we use only 1 parallelism level, and there are no bursts
//  slowedDown.runWith(Sink.foreach(println))

  //  we can just put a flow in between
  val bufferSingle: Flow[Int, Int, Unit] = Flow[Int].withAttributes(OperationAttributes.inputBuffer(1, 1))

    /**
     * 
              slowedDown
              .via(bufferSingle)
              .runForeach(println)
     *
     */


  /**
   *  TCP
   */

  //socket on random port on localhost
  //a server socket becomes a source of incoming connections
  val server = Tcp().bind("localhost", 0).to(Sink.foreach {
        //the incoming connection contains a flow ByteString => ByteString, where the incoming is the request, and the outgoing is the response
        //The join operator cross-connects source and sink, so the bytes incoming are just echoed in the response, through the identity Flow[ByteString].
    connection: Tcp.IncomingConnection => connection.flow.join(Flow[ByteString]).run()
  }).run() //here we really run the server

  //await the future returned from the materialization
  val serverBinding = Await.result(server, 1 second)

  //client is just a flow of ByteString, incoming requests, outgoing responses
  val client = Tcp().outgoingConnection(serverBinding.localAddress)

  //send a source of bytestring through the client flow, which returns them, maybe transformed, and print them out
    /**
     * Source(1 to 10).map(x => ByteString(s"hello $x")).via(client).map(_.decodeString("UTF-8")).runForeach(println)
     */

  import Protocols._
  val codec = BidiFlow((x: Message) => toBytes(x), (bytes: ByteString) => fromBytes(bytes))

  val protocol = codec atop framing

  val stack: Flow[Message, Message, Unit] = protocol join client

//  Source(1 to 10).map(x => Ping).via(stack).map(_.decodeString("UTF-8")).runForeach(println)


}
