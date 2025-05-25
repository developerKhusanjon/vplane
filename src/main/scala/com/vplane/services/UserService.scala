package com.vplane.services

import cats.effect.IO
import cats.implicits.*
import com.vplane.domain.*
import com.vplane.database.UserRepository
import java.util.UUID

trait UserService[F[_]]:
  def updateFcmToken(userId: UUID, request: UpdateFcmTokenRequest): F[Either[String, String]]
  def getUserById(userId: UUID): F[Either[String, UserResponse]]
  def searchUsers(query: String): F[List[UserResponse]]

class UserServiceImpl(userRepository: UserRepository[IO]) extends UserService[IO]:

  override def updateFcmToken(userId: UUID, request: UpdateFcmTokenRequest): IO[Either[String, String]] =
    for {
      userOpt <- userRepository.findById(userId)
      result <- userOpt match
        case None => IO.pure(Left("User not found"))
        case Some(_) =>
          userRepository.updateFcmToken(userId, request.fcmToken)
            .as(Right("FCM token updated successfully"))
    } yield result

  override def getUserById(userId: UUID): IO[Either[String, UserResponse]] =
    for {
      userOpt <- userRepository.findById(userId)
      result <- userOpt match
        case None => IO.pure(Left("User not found"))
        case Some(user) =>
          val userResponse = UserResponse(
            id = user.id,
            email = user.email,
            username = user.username,
            isEmailVerified = user.isEmailVerified,
            createdAt = user.createdAt
          )
          IO.pure(Right(userResponse))
    } yield result

  override def searchUsers(query: String): IO[List[UserResponse]] =
    // For simplicity, this is a basic implementation
    // In production, you might want to use full-text search or more sophisticated querying
    IO.pure(List.empty) // Placeholder - implement based on your search requirements
        