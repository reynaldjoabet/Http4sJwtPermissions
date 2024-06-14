// package web

// import cats.effect.Async
// import cats.implicits._
// import services.authentication.AuthenticationService
// import services.health.HealthService
// import services.models.Context.RequestContext
// import services.playlist.PlaylistService
// import services.scheduling.ApiSchedulingService
// import services.user.UserService
// import services.video.ApiVideoService
// import web.middleware.Authenticator.AuthenticatedRequestContextMiddleware
// import web.middleware._
// import web.routes._
// import com.ruchij.core.messaging.Publisher
// import com.ruchij.core.messaging.models.HttpMetric
// import com.ruchij.core.services.asset.AssetService
// import com.ruchij.core.services.scheduling.models.DownloadProgress
// import com.ruchij.core.services.video.VideoAnalysisService
// import com.ruchij.core.types.JodaClock
// import fs2.Stream
// import fs2.compression.Compression
// import org.http4s.dsl.Http4sDsl
// import org.http4s.server.ContextRouter
// import org.http4s.server.middleware.{CORS, GZip}
// import org.http4s.{ContextRoutes, HttpApp}

// object Routes {

//   def apply[F[_]: Async: JodaClock: Compression](
//     userService: UserService[F],
//     apiVideoService: ApiVideoService[F],
//     videoAnalysisService: VideoAnalysisService[F],
//     apiSchedulingService: ApiSchedulingService[F],
//     playlistService: PlaylistService[F],
//     assetService: AssetService[F],
//     healthService: HealthService[F],
//     authenticationService: AuthenticationService[F],
//     downloadProgressStream: Stream[F, DownloadProgress],
//     metricPublisher: Publisher[F, HttpMetric],
//   ): HttpApp[F] = {
//     implicit val dsl: Http4sDsl[F] = new Http4sDsl[F] {}

//     val authMiddleware: AuthenticatedRequestContextMiddleware[F] =
//       Authenticator.middleware[F](authenticationService, strict = true)

//     val contextRoutes: ContextRoutes[RequestContext, F] =
//       WebServerRoutes[F] <+>
//         ContextRouter[F, RequestContext](
//           "/users" -> UserRoutes(userService, authenticationService),
//           "/authentication" -> AuthenticationRoutes(authenticationService),
//           "/schedule" -> authMiddleware(SchedulingRoutes(apiSchedulingService, downloadProgressStream)),
//           "/videos" -> authMiddleware(VideoRoutes(apiVideoService, videoAnalysisService)),
//           "/playlists" -> authMiddleware(PlaylistRoutes(playlistService)),
//           "/assets" -> authMiddleware(AssetRoutes(assetService)),
//           "/service" -> ServiceRoutes(healthService),
//         )

//     val cors =
//       CORS.policy
//         .withAllowCredentials(true)
//         .withAllowOriginHost { _ =>
//           true
//         }

//     MetricsMiddleware(metricPublisher) {
//       GZip {
//         cors {
//           RequestContextMiddleware {
//             ExceptionHandler {
//               NotFoundHandler { contextRoutes }
//             }
//           }
//         }
//       }
//     }
//   }
// }
