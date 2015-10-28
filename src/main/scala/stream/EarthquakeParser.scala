package stream

import akka.pattern._
import akka.stream.OverflowStrategy
import akka.stream.io.{Framing, InputStreamSource}
import akka.stream.scaladsl._
import akka.util.ByteString
import argonaut.Argonaut._
import argonaut._

import scala.concurrent.Future
import scala.concurrent.duration._


object EarthquakeParser extends StreamingFacilities {

  implicit val dispatcher = actorSystem.dispatcher


  val earthquakesDump = "all_month_asc.geojson"

  case class EarthquakeEvent(time: Long, lat: Double, long: Double, elevation: Double, magnitude: Double, place: String)

  implicit def EarthquakeEventDecodeJson: DecodeJson[EarthquakeEvent] = DecodeJson (
    c => for {
      lat <- (c --\ "geometry" --\ "coordinates" =\ 0).as[Double]
      long <- (c --\ "geometry" --\ "coordinates" =\ 1).as[Double]
      elev <- (c --\ "geometry" --\ "coordinates" =\ 2).as[Double]
      magnitude <- (c --\ "properties" --\ "mag").as[Double]
      time <- (c --\ "properties" --\ "time").as[Long]
      place <- (c --\ "properties" --\ "place").as[String]
    } yield EarthquakeEvent(time, lat, long, elev, magnitude, place)
  )

  /**
   * This source emits strings as they come from the resource
   * @param resource
   */
  def source(resource: String): Source[String, Future[Long]] = {
    println(s"getting events from $resource")
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

  /** Source of events extracted from json, when parsable **/
  def earthquakeEventsSource(s: String): Source[EarthquakeEvent, Future[Long]] = source(s).map(jsonToEvent).filter(_.isDefined).map(_.get)

  /** Source of adjacent events from json **/
  def adjacentEventsSource(s: String): Source[(EarthquakeEvent, EarthquakeEvent), Future[Long]] = earthquakeEventsSource(s).via(adjacentElementsExtractor[EarthquakeEvent])

  val scaleFactor = 500L
  def replayedEvents(s: String): Source[EarthquakeEvent, Future[Long]] = adjacentEventsSource(s).buffer(1, OverflowStrategy.backpressure).mapAsync[EarthquakeEvent](1) {
                                            case (event1: EarthquakeEvent, event2: EarthquakeEvent) => {
                                              val waitingTime = (event2.time - event1.time) / scaleFactor
                                              println(s"waiting $waitingTime [ms]")
                                              after( waitingTime.milliseconds , actorSystem.scheduler)(Future.successful(event2))
                                            }
                                          }

  def adjacentElementsExtractor[T] = Flow() { implicit builder =>
    import FlowGraph.Implicits._

    val broadcast = builder.add(Broadcast[T](2))
    val zip = builder.add(Zip[T, T]())

    broadcast.out(0) ~> zip.in0
    broadcast.out(1).drop(1) ~> zip.in1

    (broadcast.in, zip.out)
  }

  /**
   * Sink that just logs
   * @tparam T
   * @return
   */
  def loggerSink[T]: Sink[T, Future[Int]] = Sink.fold(0) { (index, elem) => println(s"$index-th element: $elem"); index + 1}


  //back pressure is defined in terms of readline events
//  earthquakeEventsSource("all_month.geojson")

//  adjacentEventsSource(earthquakesDump)
//                    .map(s => {StdIn.readLine(); s})
//                    .to(loggerSink).run()




//  replayedEvents(earthquakesDump).to(loggerSink).run()


//  adjacentEventsSource(earthquakesDump).mapAsync(1)(x => after(1000 millisecond, actorSystem.scheduler)(Future.successful(x)))

  //  adjacentEventsSource(earthquakesDump).map {
//                                            case (event1: EarthquakeEvent, event2: EarthquakeEvent) => {
//                                              val waitingTime = (event2.time - event1.time) / scaleFactor
//                                              println(s"waiting $waitingTime [ms]")
//                                              event2
//                                            }
//                                        }.buffer(1, OverflowStrategy.backpressure) .to(loggerSink).run()

}
