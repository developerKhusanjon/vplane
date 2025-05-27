package com.vplane.routes

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import com.vplane.domain.*
import com.vplane.services.{AuthService, UserService}
import com.vplane.middleware.AuthenticatedUser
import com.vplane.validation.Validator
import io.circe.syntax.*
import JsonCodecs.given
import org.http4s.server.AuthMiddleware
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class AuthRoutes(authService: AuthService[IO], userService: UserService[IO]):

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  val publicRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "register" =>
      for {
        request <- req.as[CreateUserRequest]
        validationResult = Validator.validateCreateUserRequest(request)
        response <- validationResult.fold(
          errors => BadRequest(ValidationErrorResponse(
            message = "Validation failed",
            errors = errors.toList
          ).asJson),
          _ => authService.register(request).flatMap {
            case Left(error) => BadRequest(ErrorResponse(error, "REGISTRATION_FAILED").asJson)
            case Right(message) => Created(Map("message" -> message).asJson)
          }
        )
      } yield response

    case req @ POST -> Root / "login" =>
      for {
        request <- req.as[LoginRequest]
        result <- authService.login(request)
        response <- result match
          case Left(error) => Unauthorized(ErrorResponse(error, "LOGIN_FAILED").asJson)
          case Right(loginResponse) => Ok(loginResponse.asJson)
      } yield response

    case GET -> Root / "verify-email" :? TokenQueryParam(token) =>
      authService.verifyEmail(token).flatMap {
        case Left(error) => BadRequest(ErrorResponse(error, "VERIFICATION_FAILED").asJson)
        case Right(message) => Ok(Map("message" -> message).asJson)
      }
  }

  def authenticatedRoutes(authMiddleware: AuthMiddleware[IO, AuthenticatedUser]): HttpRoutes[IO] =
    authMiddleware(AuthedRoutes.of[AuthenticatedUser, IO] {

      case GET -> Root / "me" as user =>
        for {
          result <- userService.getUserById(user.id)
          response <- result match
            case Left(error) => NotFound(ErrorResponse(error, "USER_NOT_FOUND").asJson)
            case Right(userResponse) => Ok(userResponse.asJson)
        } yield response

      case req @ PUT -> Root / "fcm-token" as user =>
        for {
          request <- req.as[UpdateFcmTokenRequest]
          result <- userService.updateFcmToken(user.id, request)
          response <- result match
            case Left(error) => BadRequest(ErrorResponse(error, "FCM_UPDATE_FAILED").asJson)
            case Right(message) => Ok(Map("message" -> message).asJson)
        } yield response

      case req @ POST -> Root / "refresh-token" as _ =>
        req.headers.get[headers.Authorization] match
          case Some(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
            authService.refreshToken(token).flatMap {
              case Left(error) => Unauthorized(ErrorResponse(error, "TOKEN_REFRESH_FAILED").asJson)
              case Right(newToken) => Ok(Map("token" -> newToken).asJson)
            }
          case _ => BadRequest(ErrorResponse("Missing token", "MISSING_TOKEN").asJson)
    })

  object TokenQueryParam extends QueryParamDecoderMatcher[String]("token")