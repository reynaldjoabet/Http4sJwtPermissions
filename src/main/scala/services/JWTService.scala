package services

import java.time.Instant

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Sync
import cats.effect.std
import cats.syntax.all._

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier.BaseVerification
import com.auth0.jwt.RegisteredClaims
import configs._
import domain._
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory

//import scala.jdk.javaapi.CollectionConverters._
trait JwtService[F[_]] {

  def createToken(user: UserJwt): F[UserToken]
  def verifyToken(token: String): F[User]
  def verifyToken1(token: String): F[Option[User]]

}

final class JwtServiceLive[F[_]: Sync: std.Console] private (
  jwtConfig: JwtConfig,
  clock: java.time.Clock
) extends JwtService[F] {

 
private  def generatedSecret(password:String)={
val salt = "salt".getBytes("UTF-8")
//A user-chosen password that can be used with password-based encryption
  val keySpec = new PBEKeySpec("password".toCharArray(), salt, 65536, 256)
//This class represents a factory for secret keys.
//Secret key factories operate only on secret (symmetric) keys
  val factory         = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
 factory.generateSecret(keySpec).getEncoded
}


  private val ISSUER            = "mycode.com"
  private val CLAIM_USERNAME    = "username"
  private val CLAIM_PERMISSIONS = "permissions"
  private val algorithm         = Algorithm.HMAC512("mysecret") // mysecret

  private val verifier = JWT
    .require(algorithm)
    .withIssuer(ISSUER)
    .withClaimPresence(CLAIM_USERNAME)
    .withClaimPresence(CLAIM_PERMISSIONS)
    .withClaimPresence(RegisteredClaims.SUBJECT)
    // .withClaimPresence(RegisteredClaims.AUDIENCE)
    .withClaimPresence(RegisteredClaims.ISSUED_AT)
    .withClaimPresence(RegisteredClaims.EXPIRES_AT)
    .asInstanceOf[BaseVerification]
    .build(clock)

  override def createToken(user: UserJwt): F[UserToken] = for {
    now        <- Sync[F].delay(clock.instant())
    expiration <- Sync[F].pure(now.plusSeconds(jwtConfig.ttl))
    token <- Sync[F].delay(
               JWT
                 .create()
                 .withIssuer(
                   ISSUER
                 ) // The "issuer" identifies the principal that issued the JWT assertion (same as "iss" claim in JWT)
                 .withIssuedAt(now)
                 .withExpiresAt(
                   expiration
                 ) // The "expires_at" indicates, when grant will expire, so we will reject assertion from "issuer" targeting "subject".
                 // .withAudience("")
                 .withSubject(
                   user.id.toString
                 ) // The "subject" identifies the principal that is the subject of the JWT
                 .withClaim(CLAIM_USERNAME, user.email)
                 // .withJWTId("")
                 // .withClaim("roles", List("admin").asJava)
                 .withClaim(CLAIM_PERMISSIONS, "read:user write:user delete:user").sign(algorithm)
             )
  } yield UserToken(user.email, token, expiration.getEpochSecond)

  override def verifyToken(token: String): F[User] = for {
    decoded <- Sync[F].delay(verifier.verify(token))
    uid <- Sync[F].delay(
             User(
               id = decoded.getSubject.toLong,
               email = decoded.getClaim(CLAIM_USERNAME).asString(),
               permissions =
                 decoded.getClaim(CLAIM_PERMISSIONS).asList(classOf[String]).asScala.toSet
             )
           )
  } yield uid

  override def verifyToken1(token: String): F[Option[User]] = (for {
    decoded <- Sync[F].delay(verifier.verify(token))
    uid <- Sync[F].delay(
             User(
               id = decoded.getSubject.toLong,
               email = decoded.getClaim(CLAIM_USERNAME).asString(),
               permissions = decoded.getClaim(CLAIM_PERMISSIONS).asString().split(" ").toSet
             )
           ) // .flatTap(std.Console[F].println(_))
  } yield uid).map(_.some).recover { f => println(f); None }

}

object JwtServiceLive {

  def make[F[_]: Sync: std.Console](
    jwtConfig: JwtConfig,
    clock: java.time.Clock
  ) = new JwtServiceLive[F](jwtConfig, clock)

}
