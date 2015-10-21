package stream

import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream

import akka.stream.io.{Framing, InputStreamSource}
import akka.stream.scaladsl._
import akka.util.ByteString
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future
import scala.util.Try

/**
 * Created by pierangelo on 29-6-15.
 */
object Parse extends App with StreamingFacilities {

  import scala.concurrent.ExecutionContext.Implicits.global

  val file = "/tmp/20150622.json.gz"
  val languages = List("it", "en", "es", "nl")

  def source(file: File): Source[String, Future[Long]] = {
    val compressed = new GZIPInputStream(new FileInputStream(file), 2 * 2 * 2 * 2 * 2 * 2 * 2 * 2 )

    //input stream from input file, goes through  a delimiter flow that transforms the continuous bytestring in input in a flow of bytestreams in output
    //delimited by the delimiter character
    InputStreamSource(() => compressed)
      .via(Framing.delimiter(ByteString('\n'), Int.MaxValue))
      .map(_.decodeString("UTF-8"))
  }

  case class WikidataElement(id: String, sites: Map[String, String])

  def parseItem(langs: Seq[String], line: String): Option[WikidataElement] = {
    Try(parse(line)).toOption.flatMap { json =>
        json \ "id" match {
          case JString(itemId) =>
            val sites = for {
              lang <- languages
              JString(title) <- json \ "sitelinks" \ s"${lang}wiki" \ "title"
            } yield lang -> title

            if (sites.isEmpty) None
            else Some(WikidataElement(itemId, sites.toMap))

          case _ => None
        }
      }
    }

  def parseJson(langs: Seq[String]): Flow[String, WikidataElement, Unit] = Flow[String].mapConcat(line => parseItem(langs, line).toList)
  def parseJsonAsync(langs: Seq[String]): Flow[String, WikidataElement, Unit] = Flow[String].mapAsyncUnordered(8)(line => Future(parseItem(langs, line))).collect {
                                                                                                                                                                    case Some(x) => x
                                                                                                                                                                  }

  //flow of booleans, true if all titles are the same in the required languages, false otherwise
  def checkSameTitles(langs: Seq[String]): Flow[WikidataElement, Boolean, Unit] = Flow[WikidataElement].filter(_.sites.keySet == langs.toSet).map { wikiElement =>
                                                                                                                                                    val values = wikiElement.sites.values
                                                                                                                                                    values.forall(_.equals(values.head))
                                                                                                                                                  }

  //count how many true vs false. Fold (left) the stream accumulating until it's gone
  def countSink: Sink[Boolean, Future[(Int, Int)]] = Sink.fold((0, 0)) {
    case ((t, f), true) => (t + 1, f)
    case ((t, f), false) => (t, f + 1)
  }

  //general purpose sink
  def logEveryNSink[T](n: Int): Sink[T, Future[Int]] = Sink.fold(0) { (k, wikiElement: T) =>
    {
      if(k % n == 0) println(s"Processing $k-th element: $wikiElement")
      k + 1
    }
  }

  //graph that logs AND checks same titles (fan-out)

  val graph = FlowGraph.closed(countSink) { implicit builder =>
    countSink => {
      import FlowGraph.Implicits._

      val broadcast = builder.add(Broadcast[WikidataElement](2))

      source(new File(file)).via(parseJson(languages)) ~> broadcast ~> logEveryNSink(1000)
                                                          broadcast ~> checkSameTitles(languages) ~> countSink
    }
  }


//  source(new File(file)).via(parseJson(languages)).to(logEveryNSink(100)).run()
  graph.run()
}
