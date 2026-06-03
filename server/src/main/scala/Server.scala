import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream.scaladsl.Flow
import scala.concurrent.ExecutionContext
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.duration._
import scala.io.StdIn

object EchoServer extends App{

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "pekko-ws")
    implicit val execution_context: ExecutionContext = system.executionContext
    implicit val materializer: Materializer = Materializer(system)

    val route = pathSingleSlash{
        getFromFile("client/src/main/resource/index.html")
    }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    bindingFuture.foreach { binding =>
        println(s"")
        println(s"Servidor corriendo en http://localhost:8080")
        println(s"  Presiona ENTER para detener...")
    }

    StdIn.readLine()

    bindingFuture
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
}