import cats.effect.IO
import cats.implicits._
import cats.syntax.all._

import configs.JWTConfig
import domain.User
import io.circe.syntax._
import io.circe.Encoder
import middlewares.CheckPermissionsMiddleware
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.jawn
import org.typelevel.ci.CIStringSyntax
import services._

class CheckPermissionsMiddlewareSuite extends munit.Http4sHttpRoutesSuite {

  private val requiredPermissions =
    Set("read:user", "write:user", "delete:user")

  val jwtConfig = JWTConfig(secret = "mysecret", ttl = 864000)
  val clock     = java.time.Clock.systemDefaultZone()

  class TestRoutes(jwtService: JWTService[IO]) extends Http4sDsl[IO] {

    val routes = AuthedRoutes.of[User, IO] { case req -> Root / "hello" as user =>
      Ok()
    }

    val allRoutes: HttpRoutes[IO] = CheckPermissionsMiddleware(
      jwtService,
      Set("read:user", "write:user", "delete:user")
    ).apply(routes)

  }

  override val routes: HttpRoutes[IO] = new TestRoutes(
    JWTServiceLive.make[IO](jwtConfig, clock)
  ).allRoutes

  test(GET(uri"/hello")).alias(
    "Without access token should yield unauthorized without WWW-Authenticate header"
  ) { resp =>
    assertEquals(resp.status, Unauthorized)
    resp
      .headers
      .get(ci"Content-Length")
      .map { headers =>
        assertEquals(headers.head.value.toInt, 0)

      }
    resp
      .headers
      .get(ci"WWW-Authenticate")
      .map { headers =>
        assertEquals(
          headers.head.value.split("realm=\"")(1).split("\"").head.split(" ").toSet,
          requiredPermissions
        )
      }
  }

  test(GET(uri"hello?name=Dino")).alias("No jwt token should result in 403") { response =>
    assertIO(response.as[String], "Hello, Dino.")

    //  expect(params.code).to.equal(403);
    //         expect(params.message).to.equal('Insufficient scope');
    //         expect(header).to.equal('WWW-Authenticate');
    //         expect(content.split('scope="')[1].split('"')[0]).to.equal(
    //           expectedScopes.join(' ')
    //         );

    response
  }

  private def check(
    io: IO[Response[IO]],
    expectedStatus: Status,
    expectedBody: Option[String] = None,
    expectedContentType: Option[`WWW-Authenticate`] = None,
    evaluateBody: Boolean = true
  ): IO[Unit] = io.flatMap { response =>
    assertEquals(response.status, expectedStatus)
    expectedContentType.get.values
    assert(response.headers.contains[`WWW-Authenticate`])
    assertEquals(response.headers.get[`WWW-Authenticate`], expectedContentType)
    response.as[String].assertEquals(expectedBody.getOrElse(""))
  }

}
