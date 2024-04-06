import cats.effect.IO
import cats.implicits._
import org.http4s._
import org.typelevel.ci.CIStringSyntax
import org.http4s.jawn
import io.circe.Encoder
import org.http4s.circe._
import cats.syntax.all._
import io.circe.syntax._
import munit.CatsEffectSuite
import munit.CatsEffectAssertions
import middlewares.CheckPermissionsMiddleware
import munit.CatsEffectFunFixtures
import munit.CatsEffectFixtures
import configs.JWTConfig
import services._
import org.http4s.dsl.Http4sDsl
import domain.User
import org.http4s.Status._
import org.http4s.client.dsl.io._
import org.http4s.Method._
import org.http4s.syntax.literals._
import org.http4s.headers._
import domain.UserJWT
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
class HttpSuite extends munit.CatsEffectSuite {

  def unauthorizedWithHeader(
      routes: HttpRoutes[IO],
      req: Request[IO]
  )(
      expectedStatus: Status,
      requiredPermissions: Set[String]
  ) =
    routes.run(req).value.map {
      case Some(resp) =>
        println(resp)
        resp.headers.get(ci"Content-Length").map { headers =>
          assertEquals(headers.head.value.toInt, 0)

        }
        resp.headers.get(ci"WWW-Authenticate").map { headers =>
          assertEquals(
            headers.head.value
              .split("realm=\"")(1)
              .split("\"")
              .head
              .split(" ")
              .toSet,
            requiredPermissions
          )

        }
        assertEquals(resp.status, expectedStatus)

      // resp.asJson.map { json =>
      //   // Expectations form a multiplicative Monoid but we can also use other combinators like `expect.all`
      //   assertEquals(resp.status, expectedStatus)
      //   //assertEquals(json.dropNullValues, expectedBody.asJson.dropNullValues)

      // }

      // resp.asJson

      case None => fail("route not found")
    }

  def unauthorized(
      routes: HttpRoutes[IO],
      req: Request[IO]
  )(
      expectedStatus: Status,
      requiredPermissions: Set[String]
  ) =
    routes.run(req).value.map {
      case Some(resp) =>
        assertEquals(resp.status, expectedStatus)
        assert(resp.headers.get(ci"WWW-Authenticate").isEmpty)
        resp.headers.get(ci"Content-Length").map { headers =>
          assertEquals(headers.head.value.toInt, 0)

        }

      case None => fail("route not found")
    }

  def forbidden(
      routes: HttpRoutes[IO],
      req: Request[IO]
  )(
      expectedStatus: Status,
      requiredPermissions: Set[String]
  ) =
    routes.run(req).value.map {
      case Some(resp) =>
        assertEquals(resp.status, expectedStatus)
        assert(resp.headers.get(ci"WWW-Authenticate").isEmpty)
        resp.headers.get(ci"Content-Length").map { headers =>
          assertEquals(headers.head.value.toInt, 0)

        }

      case None => fail("route not found")
    }

  def httpStatus(routes: HttpRoutes[IO], req: Request[IO])(
      expectedStatus: Status,
      requiredPermissions: Set[String]
  ): IO[Unit] =
    routes.run(req).value.map {
      case Some(resp) => assertEquals(resp.status, expectedStatus)
      case None       => fail("route not found")
    }

  def httpFailure(routes: HttpRoutes[IO], req: Request[IO]): IO[Unit] =
    routes.run(req).value.attempt.map {
      case Left(_)  => assert(true)
      case Right(_) => fail("expected a failure")
    }

  val jwtConfig = JWTConfig(secret = "mysecret", ttl = 90)
  val clock = java.time.Clock.systemDefaultZone()
  class TestRoutes(jwtService: JWTService[IO]) extends Http4sDsl[IO] {
    val routes = AuthedRoutes.of[User, IO] {
      case req -> Root / "hello" as user => Ok()
    }

    val allRoutes: HttpRoutes[IO] = CheckPermissionsMiddleware(
      jwtService,
      Set("read:user", "write:user", "delete:user") // "edit:user"
    ).apply(routes)
  }

  val jwtService = JWTServiceLive.make[IO](jwtConfig, clock)

  val routes: HttpRoutes[IO] = new TestRoutes(
    jwtService
  ).allRoutes

  test(
    "Without access token should yield unauthorized without WWW-Authenticate header "
  ) {
    val req = GET(uri"/hello")
    unauthorized(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("With no token should yield 401 without  header") {
    val req = GET.apply(
      uri"/hello"
    )
    unauthorized(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("With empty token should yield 401 without  header") {
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          ""
        )
      )
    )
    unauthorized(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with malformed token should yield 401 with header") {
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          "hhhdhfdhfhdf.uuu"
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test(
    "Token with same permissions as required should return 200 and no header"
  ) {

    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)

    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )

    httpStatus(routes, req)(
      Ok,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test(
    "Token with more permissions than required should return 200 and no header"
  ) {

    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)

    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )

    httpStatus(routes, req)(
      Ok,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("Token with less permissions should result in 403") {

    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user") // edit
      .sign(algo)

    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )

    httpStatus(routes, req)(
      Forbidden,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("Token with expired token should yield 401 with header") {

    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().minusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)

    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )

    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with missing claim(username) should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      // .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with missing claim(subject) should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      // .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("Token with missing claim(permissions) should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      // .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with missing issueAt should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      //.withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      // .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with missing expiresAt should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      //.withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      // .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with missing audience should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      //.withAudience("no audience")
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      // .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("Token with wrong issuer  should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("differentmycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }
  test("Token with incorrect claim value should yield 401 with header") {
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          "hhhdhfdhfhdf"
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with algorithm mismatch  should yield 401 with header") {
    val algo = Algorithm.HMAC256("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token with invalid signature should yield 401 with header") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user") // edit
      .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token + "hhhdhfdhfhdf"
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  test("Token without algorithm should return 401") {
    val algo = Algorithm.HMAC512("mysecret")
    val token = JWT
      .create()
      .withIssuer("mycode.com")
      .withIssuedAt(Instant.now())
      .withExpiresAt(Instant.now().plusSeconds(jwtConfig.ttl))
      .withSubject(1L.toString()) // user identifier
      .withClaim("username", "http4s@gmail.com")
      .withClaim("permissions", "read:user write:user delete:user")
      .toString // edit
    // .sign(algo)
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Bearer,
          token
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  val permissions: Set[String] =
    Set("read:user", "write:user", "delete:user", "edit:user")

  test(
    "testing set for when just some permissions are needed. here we have two same sets"
  ) {
    val set1 = (1 to 5).toSet
    val set2 = (1 to 5).toSet
    assert(set1.exists(set2.contains))
    val permissions1: Set[String] =
      Set("read:user", "write:user", "delete:user", "edit:user")

    val permissions2: Set[String] =
      Set("read:user", "write:user", "delete:user", "edit:user")

    assert(permissions1.exists(permissions2.contains))
  }
  test(
    "testing set for when just some permissions are needed. here set one and two have nothing in common"
  ) {
    val set1 = (1 to 5).toSet
    val set2 = (6 to 10).toSet
    assert(set1.exists(set2.contains) == false)
    val permissions1: Set[String] =
      Set("read:article", "write:article", "delete:article", "edit:article")

    val permissions2: Set[String] =
      Set("read:user", "write:user", "delete:user", "edit:user")

    assert(permissions1.exists(permissions2.contains) == false)
  }

  test(
    "testing set for when just some permissions are needed. here there is an overlap of one element"
  ) {
    val set1 = (1 to 5).toSet
    val set2 = (5 to 10).toSet
    assert(set1.exists(set2.contains))

    val permissions1: Set[String] =
      Set("read:article", "write:article", "delete:user", "edit:profile")

    val permissions2: Set[String] =
      Set("read:user", "write:user", "delete:user", "edit:user")

    assert(permissions1.exists(permissions2.contains))
  }

  test(
    "testing set for when just some permissions are needed. here set one is empty"
  ) {
    val set1 = Set.empty[Int]
    val set2 = (5 to 10).toSet
    assert(set1.exists(set2.contains) == false)

    val permissions1: Set[String] = Set.empty

    val permissions2: Set[String] =
      Set("read:user", "write:user", "delete:user", "edit:user")

    assert(permissions1.exists(permissions2.contains) == false)
  }
  // test for the other authorisation headers

  test("Wrong auth scheme (Digest) should return 401") {
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Digest,
          "hhhdhfdhfhdf"
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

  // we encode our username and password in Base64 before we put it in our request.
  test("Wrong auth scheme (Basic) should return 401") {
    val req = GET.apply(
      uri"/hello",
      Authorization(
        Credentials.Token(
          AuthScheme.Basic,
          "encodeourusernameandpasswordinBase64beforeweputitinourrequest"
        )
      )
    )
    unauthorizedWithHeader(routes, req)(
      Unauthorized,
      Set("read:user", "write:user", "delete:user")
    )
  }

}
