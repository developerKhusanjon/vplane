package com.vplane.http

import cats.effect.*
import cats.implicits.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CORS, Logger as HttpLogger}
import org.http4s.server.Router
import com.vplane.config.AppConfig
import com.vplane.routes.{AuthRoutes, NoteRoutes, UserRoutes}
import com.vplane.middleware.AuthMiddlewareBuilder
import com.vplane.services.{AuthService, NoteService, UserService}
import com.comcast.ip4s.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class HttpServer(
                  authService: AuthService[IO],
                  noteService: NoteService[IO],
                  userService: UserService[IO],
                  config: AppConfig
                ):
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  def start: Resource[IO, Unit] =
    val authMiddleware = AuthMiddlewareBuilder(authService)

    val authRoutes = new AuthRoutes(authService, userService)
    val noteRoutes = new NoteRoutes(noteService)
    val userRoutes = new UserRoutes(userService)

    val httpApp = Router(
      "/api/auth" -> (authRoutes.publicRoutes <+> authRoutes.authenticatedRoutes(authMiddleware)),
      "/api/notes" -> noteRoutes.authenticatedRoutes(authMiddleware),
      "/api/users" -> userRoutes.authenticatedRoutes(authMiddleware),
      "/health" -> healthRoutes
    ).orNotFound

    val corsConfig = CORS.policy
      .withAllowOriginAll
      .withAllowHeadersAll
      .withAllowMethodsAll
      .withAllowCredentials(false)

    val finalHttpApp = corsConfig(HttpLogger.httpApp(logHeaders = true, logBody = false)(httpApp))

    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(config.server.host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(config.server.port).getOrElse(port"8080"))
      .withHttpApp(finalHttpApp)
      .build
      .evalTap(_ => Logger[IO].info(s"Server starting on ${config.server.host}:${config.server.port}"))
      .void

  private val healthRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok(Map("status" -> "healthy", "service" -> "note-messaging-app").asJson)
  }