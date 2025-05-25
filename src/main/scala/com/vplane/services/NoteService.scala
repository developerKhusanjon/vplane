package com.vplane.services

import cats.effect.IO
import cats.implicits.*
import com.vplane.domain.*
import com.vplane.database.{NoteRepository, UserRepository}
import java.time.Instant
import java.util.UUID

trait NoteService[F[_]]:
  def createNote(request: CreateNoteRequest, senderId: UUID): F[Either[String, NoteResponse]]
  def getReceivedNotes(userId: UUID, pagination: PaginationParams): F[NotesListResponse]
  def getSentNotes(userId: UUID, pagination: PaginationParams): F[NotesListResponse]
  def updateNoteStatus(noteId: UUID, request: UpdateNoteStatusRequest, userId: UUID): F[Either[String, NoteResponse]]
  def getNoteById(noteId: UUID, userId: UUID): F[Either[String, NoteResponse]]

class NoteServiceImpl(
                       noteRepository: NoteRepository[IO],
                       userRepository: UserRepository[IO],
                       notificationService: NotificationService[IO]
                     ) extends NoteService[IO]:

  override def createNote(request: CreateNoteRequest, senderId: UUID): IO[Either[String, NoteResponse]] =
    for {
      senderOpt <- userRepository.findById(senderId)
      receiverOpt <- userRepository.findById(request.receiverId)
      result <- (senderOpt, receiverOpt) match
        case (Some(sender), Some(receiver)) =>
          if (senderId == request.receiverId) then
            IO.pure(Left("Cannot send note to yourself"))
          else
            val now = Instant.now()
            val note = Note(
              id = UUID.randomUUID(),
              senderId = senderId,
              receiverId = request.receiverId,
              title = request.title,
              content = request.content,
              status = NoteStatus.Pending,
              deadline = request.deadline,
              createdAt = now,
              updatedAt = now
            )
            for {
              savedNote <- noteRepository.create(note)
              _ <- notificationService.sendNoteNotification(receiver, sender, savedNote)
              noteResponse = createNoteResponse(savedNote, sender, receiver)
            } yield Right(noteResponse)
        case (None, _) => IO.pure(Left("Sender not found"))
        case (_, None) => IO.pure(Left("Receiver not found"))
    } yield result

  override def getReceivedNotes(userId: UUID, pagination: PaginationParams): IO[NotesListResponse] =
    for {
      result <- noteRepository.findByReceiver(userId, pagination)
      (notes, totalCount) = result
      noteResponses <- notes.traverse(note =>
        for {
          senderOpt <- userRepository.findById(note.senderId)
          receiverOpt <- userRepository.findById(note.receiverId)
        } yield (senderOpt, receiverOpt) match
          case (Some(sender), Some(receiver)) => Some(createNoteResponse(note, sender, receiver))
          case _ => None
      ).map(_.flatten)
    } yield NotesListResponse(
      notes = noteResponses,
      total = totalCount,
      page = pagination.page,
      pageSize = pagination.pageSize
    )

  override def getSentNotes(userId: UUID, pagination: PaginationParams): IO[NotesListResponse] =
    for {
      result <- noteRepository.findBySender(userId, pagination)
      (notes, totalCount) = result
      noteResponses <- notes.traverse(note =>
        for {
          senderOpt <- userRepository.findById(note.senderId)
          receiverOpt <- userRepository.findById(note.receiverId)
        } yield (senderOpt, receiverOpt) match
          case (Some(sender), Some(receiver)) => Some(createNoteResponse(note, sender, receiver))
          case _ => None
      ).map(_.flatten)
    } yield NotesListResponse(
      notes = noteResponses,
      total = totalCount,
      page = pagination.page,
      pageSize = pagination.pageSize
    )

  override def updateNoteStatus(noteId: UUID, request: UpdateNoteStatusRequest, userId: UUID): IO[Either[String, NoteResponse]] =
    for {
      noteOpt <- noteRepository.updateStatus(noteId, request.status, userId)
      result <- noteOpt match
        case None => IO.pure(Left("Note not found or you don't have permission to update it"))
        case Some(note) =>
          for {
            noteWithUsers <- noteRepository.findWithUsers(noteId)
            result <- noteWithUsers match
              case Some((_, sender, receiver)) =>
                val noteResponse = createNoteResponse(note, sender, receiver)
                // Notify sender about status change
                notificationService.sendStatusUpdateNotification(sender, receiver, note)
                  .as(Right(noteResponse))
              case None => IO.pure(Left("Note details not found"))
          } yield result
    } yield result

  override def getNoteById(noteId: UUID, userId: UUID): IO[Either[String, NoteResponse]] =
    for {
      noteWithUsersOpt <- noteRepository.findWithUsers(noteId)
      result <- noteWithUsersOpt match
        case None => IO.pure(Left("Note not found"))
        case Some((note, sender, receiver)) =>
          if (note.senderId == userId || note.receiverId == userId) then
            val noteResponse = createNoteResponse(note, sender, receiver)
            IO.pure(Right(noteResponse))
          else
            IO.pure(Left("You don't have permission to view this note"))
    } yield result

  private def createNoteResponse(note: Note, sender: User, receiver: User): NoteResponse =
    NoteResponse(
      id = note.id,
      sender = UserResponse(
        id = sender.id,
        email = sender.email,
        username = sender.username,
        isEmailVerified = sender.isEmailVerified,
        createdAt = sender.createdAt
      ),
      receiver = UserResponse(
        id = receiver.id,
        email = receiver.email,
        username = receiver.username,
        isEmailVerified = receiver.isEmailVerified,
        createdAt = receiver.createdAt
      ),
      title = note.title,
      content = note.content,
      status = note.status,
      deadline = note.deadline,
      createdAt = note.createdAt,
      updatedAt = note.updatedAt
    )
