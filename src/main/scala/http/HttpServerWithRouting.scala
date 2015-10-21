package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{MissingFormFieldRejection, Route}
import stream.StreamingFacilities
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

object HttpServerWithRouting extends App with StreamingFacilities {

  /**
   * We need to import akka.http.scaladsl.server.Directives._
   * and implicit converters for XML import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
   * The Route is actually a RequestContext => Future[RouteResult]
   */
  val route: Route =
    get {
      pathSingleSlash {
        complete {
          <html>
            <body>
              Hello world!
            </body>
          </html>
        }
      } ~
      path("ping") {
        complete("Pong!")
      } ~
      path("crash") {
        complete("Boom!")
      } ~
      //code 400+
      path("reject") {
        reject(MissingFormFieldRejection("name"))
      } ~
      //code 500+
      path("fail") {
        failWith(new Exception("Go to the choppaaaa!!!"))
      }

    }


  //serve, as flow connection hander
  Http().bindAndHandle(route, "localhost", 8081)


}
