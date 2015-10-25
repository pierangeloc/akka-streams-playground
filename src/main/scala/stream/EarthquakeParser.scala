package stream

import akka.actor.ActorSystem
import akka.stream.io.{Framing, InputStreamSource}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, scaladsl}
import akka.util.{ByteString, Timeout}
import argonaut.Argonaut._
import argonaut._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn


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

  /**
   * This source emits strings as they come from the resource
   * @param resource
   */
  def source(resource: String): scaladsl.Source[String, Future[Long]] = {
    val inputStream = getClass.getClassLoader.getResourceAsStream(resource)
    InputStreamSource(() => inputStream)
      .via(Framing.delimiter(ByteString(",\n"), Int.MaxValue))
      .map(bytestring => bytestring.decodeString("UTF-8"))
  }

  /**
   * map String to decoded EarthquakeEvent
   * @param s
   */
  def jsonToEvent(s: String): Option[EarthquakeEvent] = {
    s.decodeOption[EarthquakeEvent]
  }

  /**
   * Sink that just logs
   * @tparam T
   * @return
   */
  def loggerSink[T]: Sink[T, Future[Int]] = Sink.fold(0) { (index, elem) => println(s"$index-th element: $elem"); index + 1}


  implicit val actorSystem = ActorSystem("Earthquakes")
  implicit val flowMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(3 seconds)

  //back pressure is defined in terms of readline events
  source("all_month.geojson").map(jsonToEvent)
                             .map( optionEvent => optionEvent.map(_.toString) getOrElse "Empty")
                             .map(s => {StdIn.readLine(); s})
                             .to(loggerSink).run()

}
