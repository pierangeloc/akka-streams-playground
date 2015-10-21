package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.scaladsl.{Sink, Source, Flow}
import stream.StreamingFacilities

import scala.concurrent.Future
import scala.util.Try

object HttpClientRequest extends App with StreamingFacilities {

  implicit val dispatcher = actorSystem.dispatcher

  /**
   * In this case we don't manage any connection, nor connection pool
   * Flow works the same way as the one of connection pool
   */

  val flow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Unit] = Http().superPool[Int]()

  Source.single((HttpRequest(uri = "http://google.com"), 11)).via(flow).to(Sink.foreach(response => println(response))).run()

  //without any flow, just get the future
  val eventualResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://twitter.com"))
  eventualResponse.foreach(response => println(response))

}
