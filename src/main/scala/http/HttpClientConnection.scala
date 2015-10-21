package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.scaladsl.{Sink, Source, Flow}
import stream.BaseStreamingFacilities

import scala.concurrent.Future

object HttpClientConnection extends App with BaseStreamingFacilities {


  //This is a flow Request --> Response
  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = Http().outgoingConnection("google.com")
  //we must connect the flow to a request source, and consume it in a response sink
  val responseFuture = Source.single(HttpRequest(uri = "/"))
                        .via(connectionFlow).runWith(Sink.foreach(response => println(s"$response")))

}
