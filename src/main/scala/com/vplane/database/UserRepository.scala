package com.vplane.database

import cats.effect.IO
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import com.vplane.domain.*
import doobie.hikari.HikariTransactor

import java.time.Instant
import java.util.UUID

trait UserRepository[F[_]]:
  def create(user: User): F[User]
  def findById(id: UUID): F[Option[User]]
  def findByEmail(email: String): F[Option[User]]
  def findByUsername(username: String): F[Option[User]]
  def findByEmailVerificationToken(token: String): F[Option[User]]
  def update(user: User): F[User]
  def verifyEmail(id: UUID): F[Unit]
  def updateFcmToken(userId: UUID, fcmToken: String): F[Unit]

class DoobieUserRepository(transactor: HikariTransactor[IO]) extends UserRepository[IO]:

  override def create(user: User): IO[User] =
    sql"""
      INSERT INTO users (id, email, username, password_hash, is_email_verified, email_verification_token, fcm_token, created_at, updated_at)
      VALUES (${user.id}, ${user.email}, ${user.username}, ${user.passwordHash}, ${user.isEmailVerified}, ${user.emailVerificationToken}, ${user.fcmToken}, ${user.createdAt}, ${user.updatedAt})
    """.update.run.transact(transactor).as(user)

  override def findById(id: UUID): IO[Option[User]] =
    sql"""
      SELECT id, email, username, password_hash, is_email_verified, email_verification_token, fcm_token, created_at, updated_at
      FROM users WHERE id = $id
    """.query[User].option.transact(transactor)

  override def findByEmail(email: String): IO[Option[User]] =
    sql"""
      SELECT id, email, username, password_hash, is_email_verified, email_verification_token, fcm_token, created_at, updated_at
      FROM users WHERE email = $email
    """.query[User].option.transact(transactor)

  override def findByUsername(username: String): IO[Option[User]] =
    sql"""
      SELECT id, email, username, password_hash, is_email_verified, email_verification_token, fcm_token, created_at, updated_at
      FROM users WHERE username = $username
    """.query[User].option.transact(transactor)

  override def findByEmailVerificationToken(token: String): IO[Option[User]] =
    sql"""
      SELECT id, email, username, password_hash, is_email_verified, email_verification_token, fcm_token, created_at, updated_at
      FROM users WHERE email_verification_token = $token
    """.query[User].option.transact(transactor)

  override def update(user: User): IO[User] =
    sql"""
      UPDATE users 
      SET email = ${user.email}, username = ${user.username}, password_hash = ${user.passwordHash}, 
          is_email_verified = ${user.isEmailVerified}, email_verification_token = ${user.emailVerificationToken}, 
          fcm_token = ${user.fcmToken}, updated_at = ${user.updatedAt}
      WHERE id = ${user.id}
    """.update.run.transact(transactor).as(user)

  override def verifyEmail(id: UUID): IO[Unit] =
    sql"""
      UPDATE users 
      SET is_email_verified = true, email_verification_token = null, updated_at = ${Instant.now()}
      WHERE id = $id
    """.update.run.transact(transactor).void

  override def updateFcmToken(userId: UUID, fcmToken: String): IO[Unit] =
    sql"""
      UPDATE users 
      SET fcm_token = $fcmToken, updated_at = ${Instant.now()}
      WHERE id = $userId
    """.update.run.transact(transactor).void