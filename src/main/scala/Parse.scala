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
object Parse extends App with BaseStreamingFacilities {

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

  //this is a source of WikidataElement
  val transformedSource: Source[WikidataElement, Future[Long]] = source(new File(file)).via(parseJson(languages))

  //general purpose sink
  def logEveryNSink[T](n: Int): Sink[T, Future[Int]] = Sink.fold(0) { (k, wikiElement: T) =>
    {
      if(k % n == 0) println(s"Processing $k-th element: $wikiElement")
      k + 1
    }
  }

  transformedSource.to(logEveryNSink(100)).run()

}
