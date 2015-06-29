import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer}
import akka.util.Timeout

import scala.concurrent.duration._


trait BaseStreamingFacilities {

  implicit val actorSystem = ActorSystem("Playground")
  implicit val flowMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(3 seconds)


}
