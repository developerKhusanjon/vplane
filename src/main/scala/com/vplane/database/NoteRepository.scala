package com.vplane.database

import cats.effect.IO
import cats.implicits.*
import doobie.*
import doobie.postgres.implicits.*
import com.vplane.domain.*
import doobie.hikari.HikariTransactor
import doobie.implicits.* //

import java.time.Instant
import java.util.UUID

trait NoteRepository[F[_]]:
  def create(note: Note): F[Note]
  def findById(id: UUID): F[Option[Note]]
  def findByReceiver(receiverId: UUID, pagination: PaginationParams): F[(List[Note], Int)]
  def findBySender(senderId: UUID, pagination: PaginationParams): F[(List[Note], Int)]
  def updateStatus(noteId: UUID, status: NoteStatus, userId: UUID): F[Option[Note]]
  def findWithUsers(noteId: UUID): F[Option[(Note, User, User)]]

class DoobieNoteRepository(transactor: HikariTransactor[IO]) extends NoteRepository[IO]:

  override def create(note: Note): IO[Note] =
    sql"""
      INSERT INTO notes (id, sender_id, receiver_id, title, content, status, deadline, created_at, updated_at)
      VALUES (${note.id}, ${note.senderId}, ${note.receiverId}, ${note.title}, ${note.content}, ${note.status.toString.toLowerCase}, ${note.deadline}, ${note.createdAt}, ${note.updatedAt})
    """.update.run.transact(transactor).as(note)

  override def findById(id: UUID): IO[Option[Note]] =
    sql"""
      SELECT id, sender_id, receiver_id, title, content, status, deadline, created_at, updated_at
      FROM notes WHERE id = $id
    """.query[Note].option.transact(transactor)

  override def findByReceiver(receiverId: UUID, pagination: PaginationParams): IO[(List[Note], Int)] =
    val offset = (pagination.page - 1) * pagination.pageSize
    for {
      notes <- sql"""
        SELECT id, sender_id, receiver_id, title, content, status, deadline, created_at, updated_at
        FROM notes 
        WHERE receiver_id = $receiverId
        ORDER BY created_at DESC
        LIMIT ${pagination.pageSize} OFFSET $offset
      """.query[Note].to[List].transact(transactor)

      count <- sql"""
        SELECT COUNT(*) FROM notes WHERE receiver_id = $receiverId
      """.query[Int].unique.transact(transactor)
    } yield (notes, count)

  override def findBySender(senderId: UUID, pagination: PaginationParams): IO[(List[Note], Int)] =
    val offset = (pagination.page - 1) * pagination.pageSize
    for {
      notes <- sql"""
        SELECT id, sender_id, receiver_id, title, content, status, deadline, created_at, updated_at
        FROM notes 
        WHERE sender_id = $senderId
        ORDER BY created_at DESC
        LIMIT ${pagination.pageSize} OFFSET $offset
      """.query[Note].to[List].transact(transactor)

      count <- sql"""
        SELECT COUNT(*) FROM notes WHERE sender_id = $senderId
      """.query[Int].unique.transact(transactor)
    } yield (notes, count)

  override def updateStatus(noteId: UUID, status: NoteStatus, userId: UUID): IO[Option[Note]] =
    for {
      updated <- sql"""
        UPDATE notes 
        SET status = ${status.toString.toLowerCase}, updated_at = ${Instant.now()}
        WHERE id = $noteId AND receiver_id = $userId
      """.update.run.transact(transactor)

      note <- if (updated > 0) findById(noteId) else IO.pure(None)
    } yield note

  override def findWithUsers(noteId: UUID): IO[Option[(Note, User, User)]] =
    sql"""
      SELECT 
        n.id, n.sender_id, n.receiver_id, n.title, n.content, n.status, n.deadline, n.created_at, n.updated_at,
        s.id, s.email, s.username, s.password_hash, s.is_email_verified, s.email_verification_token, s.fcm_token, s.created_at, s.updated_at,
        r.id, r.email, r.username, r.password_hash, r.is_email_verified, r.email_verification_token, r.fcm_token, r.created_at, r.updated_at
      FROM notes n
      JOIN users s ON n.sender_id = s.id
      JOIN users r ON n.receiver_id = r.id
      WHERE n.id = $noteId
    """.query[(Note, User, User)].option.transact(transactor)

// Custom Meta instances for enums and UUIDs
//given Meta[UUID] = Meta[String].timap(UUID.fromString)(_.toString)
given Meta[NoteStatus] = Meta[String].timap {
  case "pending" => NoteStatus.Pending
  case "done" => NoteStatus.Done
  case "not_done" => NoteStatus.NotDone
}(_.toString.toLowerCase)
//given Meta[Instant] = Meta[java.time.Instant]