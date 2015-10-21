package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.scaladsl.{Sink, Flow, Source}
import stream.StreamingFacilities

import scala.util.Try


object HttpClientConnectionPool extends App with StreamingFacilities {

  /**
    * Create a connection pool to a given endpoint (host, port). Pool is local to the actor system,
    * therefore it is started even before any materialization of any flow. The first connection is created only when
    * requests are actually sent over the flow
    *
    * Responses are sent independently from the requests, and matched through a req-res context of type T generic
    * When requests can't be sent as all connections are taken and not available, back pressure is applied to the source
    */

  //here we use Int as a context to pair request/responses
  private val poolClientFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), HostConnectionPool] = Http().cachedHostConnectionPool[Int]("google.com")

  Source.single((HttpRequest(uri = "/"), 10)).via(poolClientFlow).runWith(Sink.foreach(response => println(s"response: $response")))
}
