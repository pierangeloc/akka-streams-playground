import akka.actor.ActorSystem
import akka.stream.io.{Framing, InputStreamSource}
import akka.stream.scaladsl.{Sink, FlowGraph}
import akka.stream.{ActorMaterializer, scaladsl}
import akka.util.{Timeout, ByteString}
import argonaut._, Argonaut._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.{Source, BufferedSource}
import scalaz.\/


object EarthquakeParser extends App {

  case class EarthquakeEvent(lat: Double, long: Double, elevation: Double, magnitude: Double, time: Long, place: String)

  implicit def EarthquakeEventDecodeJson: DecodeJson[EarthquakeEvent] = DecodeJson (
    c => for {
      lat <- (c --\ "geometry" --\ "coordinates" =\ 0).as[Double]
      long <- (c --\ "geometry" --\ "coordinates" =\ 1).as[Double]
      elev <- (c --\ "geometry" --\ "coordinates" =\ 2).as[Double]
      magnitude <- (c --\ "properties" --\ "mag").as[Double]
      time <- (c --\ "properties" --\ "time").as[Long]
      place <- (c --\ "properties" --\ "place").as[String]
    } yield EarthquakeEvent(lat, long, elev, magnitude, time, place)
  )

  def source(resource: String): scaladsl.Source[String, Future[Long]] = {
    val inputStream = getClass.getClassLoader.getResourceAsStream(resource)
    InputStreamSource(() => inputStream)
      .via(Framing.delimiter(ByteString(",\n"), Int.MaxValue))
      .map(bytestring => bytestring.decodeString("UTF-8"))
  }

  def jsonToEvent(s: String): Option[EarthquakeEvent] = {
    s.decodeOption[EarthquakeEvent]
  }

  def loggerSink[T]: Sink[T, Future[Int]] = Sink.fold(0) { (index, elem) => println(s"$index-th element: $elem"); index + 1}


  implicit val actorSystem = ActorSystem("Earthquakes")
  implicit val flowMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(3 seconds)

  source("all_month.geojson").map(jsonToEvent).to(loggerSink).run()



}
