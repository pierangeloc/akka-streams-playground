package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.{ServerBinding, IncomingConnection}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.TextMessage.Strict
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebsocket}
import akka.stream.Outlet
import akka.stream.scaladsl._
import stream.{EarthquakeParser, StreamingFacilities}

import scala.concurrent.Future

/**
 * Serve the earthquake stream to each websocket, separately, with time dictated by server.
 * Client can pass in the ws request the time multiplier, which determines how fast we want events to be replayed
 */
object EarthquakeServer extends App with StreamingFacilities {


  val connectionsSource: Source[IncomingConnection, Future[ServerBinding]] = Http().bind("localhost", 8080)

  connectionsSource.runForeach(connection => connection.handleWithSyncHandler(webSocketRequestHandler))


  val echoFlow = Flow[Message].collect {case tm: TextMessage => TextMessage(Source.single("Hello ") ++ tm.textStream)}

  val eventsTriggerFlow = Flow() { implicit builder =>
    import FlowGraph.Implicits._

    val concat = builder.add(Merge[Message](2))
    val eventSource: Outlet[Strict] = builder.add(EarthquakeParser.replayedEvents(EarthquakeParser.earthquakesDump).map(event => TextMessage.Strict(event.toString)))

    val inputFlow = builder.add(Flow[Message].filter(_ => false))

    inputFlow   ~> concat
    eventSource ~> concat

    (inputFlow.inlet, concat.out)

  }

  val webSocketRequestHandler: HttpRequest => HttpResponse = {
    case request @ HttpRequest(HttpMethods.GET, Uri.Path("/websocket"), _, _, _) =>
      println("received request")
      request.header[UpgradeToWebsocket] match {
        case Some(websocketHeader) => websocketHeader.handleMessages(eventsTriggerFlow)
        case _ => HttpResponse(status = StatusCodes.Unauthorized)
      }
    case _ => HttpResponse(status = StatusCodes.Unauthorized)
  }

}
