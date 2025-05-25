package com.vplane.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.time.Instant
import java.util.UUID

// User domain
case class User(
                 id: UUID,
                 email: String,
                 username: String,
                 passwordHash: String,
                 isEmailVerified: Boolean,
                 emailVerificationToken: Option[String],
                 fcmToken: Option[String], // For Firebase notifications
                 createdAt: Instant,
                 updatedAt: Instant
               )

case class CreateUserRequest(
                              email: String,
                              username: String,
                              password: String
                            )

case class LoginRequest(
                         email: String,
                         password: String
                       )

case class LoginResponse(
                          token: String,
                          user: UserResponse
                        )

case class UserResponse(
                         id: UUID,
                         email: String,
                         username: String,
                         isEmailVerified: Boolean,
                         createdAt: Instant
                       )

case class EmailVerificationRequest(
                                     token: String
                                   )

case class UpdateFcmTokenRequest(
                                  fcmToken: String
                                )

// Note domain
enum NoteStatus:
  case Pending, Done, NotDone

case class Note(
                 id: UUID,
                 senderId: UUID,
                 receiverId: UUID,
                 title: String,
                 content: String,
                 status: NoteStatus,
                 deadline: Option[Instant],
                 createdAt: Instant,
                 updatedAt: Instant
               )

case class CreateNoteRequest(
                              receiverId: UUID,
                              title: String,
                              content: String,
                              deadline: Option[Instant]
                            )

case class UpdateNoteStatusRequest(
                                    status: NoteStatus
                                  )

case class NoteResponse(
                         id: UUID,
                         sender: UserResponse,
                         receiver: UserResponse,
                         title: String,
                         content: String,
                         status: NoteStatus,
                         deadline: Option[Instant],
                         createdAt: Instant,
                         updatedAt: Instant
                       )

case class NotesListResponse(
                              notes: List[NoteResponse],
                              total: Int,
                              page: Int,
                              pageSize: Int
                            )

// Error responses
case class ErrorResponse(
                          message: String,
                          code: String
                        )

case class ValidationError(
                            field: String,
                            message: String
                          )

case class ValidationErrorResponse(
                                    message: String,
                                    errors: List[ValidationError]
                                  )

// Pagination
case class PaginationParams(
                             page: Int = 1,
                             pageSize: Int = 20
                           )

// Firebase notification payload
case class FirebaseNotificationPayload(
                                        title: String,
                                        body: String,
                                        data: Map[String, String] = Map.empty
                                      )

// Twilio email payload
case class EmailPayload(
                         to: String,
                         subject: String,
                         body: String
                       )

//// Circe encoders/decoders
//object JsonCodecs:
//  given Encoder[UUID] = Encoder.encodeString.contramap(_.toString)
//  given Decoder[UUID] = Decoder.decodeString.emap(s =>
//    try Right(UUID.fromString(s))
//    catch case _: IllegalArgumentException => Left("Invalid UUID")
//  )

  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emap(s =>
    try Right(Instant.parse(s))
    catch case _: Exception => Left("Invalid timestamp")
  )

  given Encoder[NoteStatus] = Encoder.encodeString.contramap(_.toString.toLowerCase)
  given Decoder[NoteStatus] = Decoder.decodeString.emap {
    case "pending" => Right(NoteStatus.Pending)
    case "done" => Right(NoteStatus.Done)
    case "not_done" | "notdone" => Right(NoteStatus.NotDone)
    case other => Left(s"Invalid note status: $other")
  }

  // Derive codecs for case classes
  given Encoder[CreateUserRequest] = deriveEncoder
  given Decoder[CreateUserRequest] = deriveDecoder

  given Encoder[LoginRequest] = deriveEncoder
  given Decoder[LoginRequest] = deriveDecoder

  given Encoder[LoginResponse] = deriveEncoder
  given Decoder[LoginResponse] = deriveDecoder

  given Encoder[UserResponse] = deriveEncoder
  given Decoder[UserResponse] = deriveDecoder

  given Encoder[EmailVerificationRequest] = deriveEncoder
  given Decoder[EmailVerificationRequest] = deriveDecoder

  given Encoder[UpdateFcmTokenRequest] = deriveEncoder
  given Decoder[UpdateFcmTokenRequest] = deriveDecoder

  given Encoder[CreateNoteRequest] = deriveEncoder
  given Decoder[CreateNoteRequest] = deriveDecoder

  given Encoder[UpdateNoteStatusRequest] = deriveEncoder
  given Decoder[UpdateNoteStatusRequest] = deriveDecoder

  given Encoder[NoteResponse] = deriveEncoder
  given Decoder[NoteResponse] = deriveDecoder

  given Encoder[NotesListResponse] = deriveEncoder
  given Decoder[NotesListResponse] = deriveDecoder

  given Encoder[JwtClaims] = deriveEncoder
  given Decoder[JwtClaims] = deriveDecoder

object JsonErrorCodecs:
  given Encoder[ErrorResponse] = deriveEncoder
  given Decoder[ErrorResponse] = deriveDecoder

  given Encoder[ValidationError] = deriveEncoder
  given Decoder[ValidationError] = deriveDecoder

  given Encoder[ValidationErrorResponse] = deriveEncoder
  given Decoder[ValidationErrorResponse] = deriveDecoder

  given Encoder[FirebaseNotificationPayload] = deriveEncoder
  given Decoder[FirebaseNotificationPayload] = deriveDecoder

  given Encoder[EmailPayload] = deriveEncoder
  given Decoder[EmailPayload] = deriveDecoder
