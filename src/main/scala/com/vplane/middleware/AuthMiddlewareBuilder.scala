package com.vplane.middleware

import cats.effect.IO
import cats.data.{Kleisli, OptionT}
import cats.implicits.*
import org.http4s.*
import org.http4s.server.AuthMiddleware
import org.http4s.headers.Authorization
import com.vplane.domain.{JwtClaims, ErrorResponse, JsonCodecs}
import com.vplane.services.AuthService
import io.circe.syntax.*
import org.http4s.circe.*
import com.vplane.domain.JsonErrorCodecs.given


case class AuthenticatedUser(claims: JwtClaims)

object AuthMiddlewareBuilder:
  def apply(authService: AuthService[IO]): AuthMiddleware[IO, AuthenticatedUser] =
    val authUser: Kleisli[IO, Request[IO], Either[String, AuthenticatedUser]] = Kleisli { request =>
      extractToken(request) match
        case Some(token) =>
          authService.validateToken(token).map {
            case Right(claims) => Right(AuthenticatedUser(claims))
            case Left(err) => Left(err)
          }
        case None =>
          IO.pure(Left("Missing token"))
    }

    val onFailure: AuthedRoutes[String, IO] = Kleisli { _ =>
      OptionT.liftF {
        Response[IO](Status.Unauthorized)
          .withEntity(ErrorResponse("Unauthorized", "AUTH_REQUIRED").asJson)
          .pure[IO]
      }
    }

    org.http4s.server.AuthMiddleware(authUser, onFailure)

  private def extractToken(request: Request[IO]): Option[String] =
    request.headers.get[Authorization].collect {
      case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => token
    }
