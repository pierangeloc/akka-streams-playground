package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.{ServerBinding, IncomingConnection}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source}
import stream.StreamingFacilities

import scala.concurrent.Future

object HttpServer extends App with StreamingFacilities {

  /**
   * Server socket is translated into a source of incoming connections, which are in turn flows Request -> Response
   * _BackPressure is everywhere:_
   * - Connections are accepted only when pulled from downstream
   * - HttpRequest body is also a Source[ByteString]
   *
   * The ServerBinding materialized can be used to unbind, i.e. shutdown the server
   */
  val serverSource: Source[IncomingConnection, Future[ServerBinding]] = Http().bind(interface = "localhost", port = 8080)

  serverSource.to(Sink.foreach{
    connection: IncomingConnection => println("accepted connection from " + connection.remoteAddress)

    /**
     * Requests can be handled with flow Request ==> Response, or with Request -> Future[Response], or with Request -> Response
     */
    connection.handleWithSyncHandler(synchronousRequestHandler)
  }).run()

  /**
   * Synchronous handler function
   */
  val synchronousRequestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) => HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, "{code: 200, body: \"Hello World\"}"))
    case HttpRequest(HttpMethods.GET, Uri.Path("/salute"), _, _, _) => HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, "{code: 200, body: \"Buongiorno\"}"))
    case HttpRequest(HttpMethods.GET, Uri.Path("/crash"), _, _, _) => HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, "{code: 500, body: \"Boom!!!\"}"))

    case _ => HttpResponse(status = StatusCodes.InternalServerError)

  }

}
