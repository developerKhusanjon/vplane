package com.vplane.routes

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import com.vplane.domain.*
import com.vplane.services.UserService
import com.vplane.middleware.AuthenticatedUser
import io.circe.syntax.*

import java.util.UUID
import com.vplane.domain.JsonErrorCodecs.given 
import com.vplane.domain.given 
import org.http4s.server.AuthMiddleware

class UserRoutes(userService: UserService[IO]):

  def authenticatedRoutes(authMiddleware: AuthMiddleware[IO, AuthenticatedUser]): HttpRoutes[IO] =
    authMiddleware(AuthedRoutes.of[AuthenticatedUser, IO] {

      case GET -> Root / "search" :? QueryQueryParam(query) as _ =>
        for {
          users <- userService.searchUsers(query.getOrElse(""))
          response <- Ok(users.asJson)
        } yield response

      case GET -> Root / UUIDVar(userId) as _ =>
        for {
          result <- userService.getUserById(userId)
          response <- result match
            case Right(user) => Ok(user.asJson)
            case Left(error) => NotFound(ErrorResponse(error, "USER_NOT_FOUND").asJson)
        } yield response
    })

  object QueryQueryParam extends OptionalQueryParamDecoderMatcher[String]("q")