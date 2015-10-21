package stream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._


trait StreamingFacilities {

  implicit val actorSystem = ActorSystem("Playground")
  implicit val flowMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(3 seconds)


}
