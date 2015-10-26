package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.{ServerBinding, IncomingConnection}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebsocket}
import akka.stream.scaladsl.{Flow, Source}
import stream.StreamingFacilities

import scala.concurrent.Future

/**
 * Serve the earthquake stream to each websocket, separately, with time dictated by server.
 * Client can pass in the ws request the time multiplier, which determines how fast we want events to be replayed
 */
object EarthquakeServer extends App with StreamingFacilities {

  val connectionsSource: Source[IncomingConnection, Future[ServerBinding]] = Http().bind("localhost", 9091)

  connectionsSource.runForeach(connection => connection.handleWithSyncHandler(webSocketRequestHandler))

  val echoFlow = Flow[Message].collect {case tm: TextMessage => TextMessage(Source.single("Hello ") ++ tm.textStream)}

  val webSocketRequestHandler: HttpRequest => HttpResponse = {
    case request @ HttpRequest(HttpMethods.GET, Uri.Path("/websocket"), _, _, _) =>
      println("received request")
      request.header[UpgradeToWebsocket] match {
        case Some(websocketHeader) => websocketHeader.handleMessages(echoFlow)
        case _ => HttpResponse(status = StatusCodes.Unauthorized)
      }
    case _ => HttpResponse(status = StatusCodes.Unauthorized)
  }

}
