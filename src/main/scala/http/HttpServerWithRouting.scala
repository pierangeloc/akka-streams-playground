package http

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{MissingFormFieldRejection, Route}
import stream.StreamingFacilities
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

object HttpServerWithRouting extends App with StreamingFacilities {

  /**
   * We need to import akka.http.scaladsl.server.Directives._
   * and implicit converters for XML, `import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._`
   * The Route is actually a RequestContext => Future[RouteResult]
   * RequestContext wraps request with surrounding situation descriptions (e.g. which part of the request path hasn't been matched yet, execution context etc). Immutable but easy to reproduce
   * RouteResult result of the routing: Complete or Rejected
   *
   * ~ operator is a concatenation, it executes subsequent routes if the previous one could not handle the request
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
