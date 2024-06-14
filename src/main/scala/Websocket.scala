import java.time.Instant

import scala.concurrent.duration._

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import fs2.{Pipe, Stream}

import com.comcast.ip4s._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers
import org.http4s.headers.`X-Forwarded-For`
import org.http4s.headers.`X-Forwarded-Proto`
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.Server
import org.http4s.websocket.WebSocketFrame
import org.http4s.HttpRoutes

object MainApp extends IOApp {

  def routes(ws: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root / "ws" =>
      val send: Stream[IO, WebSocketFrame] =
        Stream.awakeEvery[IO](1.second).evalMap(_ => IO(WebSocketFrame.Text("ok")))
      val receive: Pipe[IO, WebSocketFrame, Unit] =
        in => in.evalMap(frameIn => IO(println("in " + frameIn.length)))

      ws.build(send, receive)
    }

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpWebSocketApp(ws => routes(ws).orNotFound)
      .build

  def run(args: List[String]): IO[ExitCode] = {
    Stream
      .resource(
        serverResource >> Resource.never
      )
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
