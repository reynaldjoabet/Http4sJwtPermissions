package middlewares

import org.http4s.AuthedRoutes
import cats._
import cats.data._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s._
import services.JWTService
import domain._
import domain.ResponseError._
import cats.syntax.all._
import org.http4s.server.AuthMiddleware

object CheckPermissionsMiddleware {
//authenticate()
  def checkPermissions[F[_]: MonadThrow](
      jwtService: JWTService[F],
      requiredPermissions: Set[String]
  ): Kleisli[F, Request[F], Either[ResponseError, User]] = Kleisli {
    req: Request[F] =>
      req.headers.get[Authorization] match {
        case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token)))
            if token.isEmpty =>
          Either.left[ResponseError, User](MissingAccessTokenResponse).pure
        case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
          jwtService
            .verifyToken1(token)
            .map {
              case None =>
                Either.left[ResponseError, User](
                  UnauthorizedResponse(requiredPermissions)
                )

              case Some(user) =>
                println(user)
                if (requiredPermissions.forall(user.permissions.contains))
                  Either.right[ResponseError, User](user)
                else
                  Either.left[ResponseError, User](
                    ForbiddenResponse
                  ) // should ne 404 for security reasons

            }
        case _ =>
          Either.left[ResponseError, User](MissingAccessTokenResponse).pure

      }
  }

  def authenticateUser[F[_]: MonadThrow](
      jwtService: JWTService[F],
      rolesAllowed: Set[String]
  ): Kleisli[({ type Y[X] = OptionT[F, X] })#Y, Request[F], User] = Kleisli {
    req: Request[F] =>
      req.headers.get[Authorization] match {
        case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
          OptionT
            .liftF(jwtService.verifyToken(token))
            .flatMap { user =>
              if (rolesAllowed.forall(user.permissions.contains))
                OptionT.pure(user)
              else OptionT.none[F, User]

            }
            .recoverWith(_ => OptionT.none[F, User])
        case _ => OptionT.none[F, User]
      }

  }

  def onFailure[F[_]: Monad]: AuthedRoutes[ResponseError, F] =
    Kleisli { request =>
      val dsl = Http4sDsl[F]
      import dsl._
      request.context match {
        // no additional info
        case MissingAccessTokenResponse =>
          OptionT.liftF(
            Response[F](
              Unauthorized,
              headers = Headers(`Content-Length`.zero)
            ).pure
          )
        case UnauthorizedResponse(scopes) =>
          OptionT.liftF(
            Unauthorized(
              `WWW-Authenticate`(
                Challenge("Bearer ", scopes.mkString(" "))
              )
            )
          )
        case ForbiddenResponse =>
          OptionT.liftF(Forbidden()) // should be 404
      }

    }

  def apply[F[_]: MonadThrow](
      jwtService: JWTService[F],
      requiredPermissions: Set[String]
  ): AuthMiddleware[F, User] =
    AuthMiddleware(checkPermissions(jwtService, requiredPermissions), onFailure)

}
