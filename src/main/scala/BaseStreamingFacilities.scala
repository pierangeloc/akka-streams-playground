import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.util.Timeout

import scala.concurrent.duration._


trait BaseStreamingFacilities {

  implicit val actorSystem = ActorSystem("Playground")
  implicit val flowMaterializer = ActorFlowMaterializer()
  implicit val timeout = Timeout(3 seconds)


}
