package com.vplane.services

import cats.effect.IO
import cats.implicits.*
import com.vplane.domain.*
import com.vplane.database.UserRepository
import com.vplane.config.JwtConfig
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.syntax.*
import com.vplane.services.PasswordUtils.*
import java.time.Instant
import java.util.UUID
import scala.util.{Failure, Success}
import com.vplane.domain.JsonCodecs.given

trait AuthService[F[_]]:
  def register(request: CreateUserRequest): F[Either[String, UserResponse]]
  def login(request: LoginRequest): F[Either[String, LoginResponse]]
  def verifyEmail(token: String): F[Either[String, String]]
  def validateToken(token: String): F[Either[String, JwtClaims]]
  def generateToken(user: User): F[String]

class AuthServiceImpl(
                       userRepository: UserRepository[IO],
                       jwtConfig: JwtConfig,
                       emailService: EmailService[IO]
                     ) extends AuthService[IO]:

  override def register(request: CreateUserRequest): IO[Either[String, UserResponse]] =
    for {
      existingByEmail <- userRepository.findByEmail(request.email)
      existingByUsername <- userRepository.findByUsername(request.username)
      result <- (existingByEmail, existingByUsername) match
        case (Some(_), _) => IO.pure(Left("Email already exists"))
        case (_, Some(_)) => IO.pure(Left("Username already exists"))
        case (None, None) =>
          for {
            passwordHash <- IO(request.password.bcryptBounded)
            verificationToken = UUID.randomUUID().toString
            now = Instant.now()
            user = User(
              id = UUID.randomUUID(),
              email = request.email,
              username = request.username,
              passwordHash = passwordHash,
              isEmailVerified = false,
              emailVerificationToken = Some(verificationToken),
              fcmToken = None,
              createdAt = now,
              updatedAt = now
            )
            savedUser <- userRepository.create(user)
            _ <- emailService.sendVerificationEmail(user.email, verificationToken)
          } yield Right(UserResponse(
            id = savedUser.id,
            email = savedUser.email,
            username = savedUser.username,
            isEmailVerified = savedUser.isEmailVerified,
            createdAt = savedUser.createdAt
          ))
    } yield result

  override def login(request: LoginRequest): IO[Either[String, LoginResponse]] =
    for {
      userOpt <- userRepository.findByEmail(request.email)
      result <- userOpt match
        case None => IO.pure(Left("Invalid credentials"))
        case Some(user) =>
          if (!user.isEmailVerified) then
            IO.pure(Left("Email not verified"))
          else if (request.password.isBcryptedBounded(user.passwordHash)) then
            for {
              token <- generateToken(user)
              userResponse = UserResponse(
                id = user.id,
                email = user.email,
                username = user.username,
                isEmailVerified = user.isEmailVerified,
                createdAt = user.createdAt
              )
            } yield Right(LoginResponse(token, userResponse))
          else
            IO.pure(Left("Invalid credentials"))
    } yield result

  override def verifyEmail(token: String): IO[Either[String, String]] =
    for {
      userOpt <- userRepository.findByEmailVerificationToken(token)
      result <- userOpt match
        case None => IO.pure(Left("Invalid verification token"))
        case Some(user) =>
          if (user.isEmailVerified) then
            IO.pure(Left("Email already verified"))
          else
            userRepository.verifyEmail(user.id).as(Right("Email verified successfully"))
    } yield result

  override def validateToken(token: String): IO[Either[String, JwtClaims]] =
    IO {
      JwtCirce.decode(token, jwtConfig.secret, Seq(JwtAlgorithm.HS256)) match
        case Success(jwtClaim) =>
          io.circe.parser.decode[JwtClaims](jwtClaim.content) match
            case Right(claims) =>
              if (claims.exp > Instant.now().getEpochSecond) then
                Right(claims)
              else
                Left("Token expired")
            case Left(_) => Left("Invalid token format")
        case Failure(_) => Left("Invalid token")
    }

  override def generateToken(user: User): IO[String] =
    IO {
      val expiration = Instant.now().plusSeconds(jwtConfig.expirationHours * 3600)
      val claims = JwtClaims(
        userId = user.id,
        email = user.email,
        exp = expiration.getEpochSecond
      )
      val jwtClaim = JwtClaim(
        content = claims.asJson.noSpaces,
        expiration = Some(expiration.getEpochSecond)
      )
      JwtCirce.encode(jwtClaim, jwtConfig.secret, JwtAlgorithm.HS256)
    }