package com.vplane.services

import cats.effect.IO
import cats.implicits.*
import com.vplane.domain.*
import com.vplane.config.FirebaseConfig
import sttp.client3.*
import sttp.client3.circe.*
import io.circe.generic.auto.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.io.{FileInputStream}
import scala.io.Source
import io.circe.Json
import io.circe.parser._

trait NotificationService[F[_]]:
  def sendNoteNotification(receiver: User, sender: User, note: Note): F[Unit]
  def sendStatusUpdateNotification(sender: User, receiver: User, note: Note): F[Unit]

case class FirebaseMessage(
                            message: FirebaseMessagePayload
                          )

case class FirebaseMessagePayload(
                                   token: String,
                                   notification: FirebaseNotification,
                                   data: Map[String, String] = Map.empty
                                 )

case class FirebaseNotification(
                                 title: String,
                                 body: String
                               )

case class FirebaseAccessToken(
                                access_token: String,
                                expires_in: Int,
                                token_type: String
                              )

class FirebaseNotificationService(
                                   config: FirebaseConfig,
                                   backend: SttpBackend[IO, Any]
                                 ) extends NotificationService[IO]:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def sendNoteNotification(receiver: User, sender: User, note: Note): IO[Unit] =
    receiver.fcmToken match
      case Some(fcmToken) =>
        val notification = FirebaseNotification(
          title = s"New note from ${sender.username}",
          body = note.title
        )
        val data = Map(
          "noteId" -> note.id.toString,
          "senderId" -> sender.id.toString,
          "type" -> "new_note"
        )
        sendNotification(fcmToken, notification, data)
      case None =>
        Logger[IO].debug(s"No FCM token for user ${receiver.id}")

  override def sendStatusUpdateNotification(sender: User, receiver: User, note: Note): IO[Unit] =
    sender.fcmToken match
      case Some(fcmToken) =>
        val statusText = note.status match
          case NoteStatus.Done => "marked as done"
          case NoteStatus.NotDone => "marked as not done"
          case NoteStatus.Pending => "reset to pending"

        val notification = FirebaseNotification(
          title = s"Note status updated",
          body = s"${receiver.username} ${statusText} your note: ${note.title}"
        )
        val data = Map(
          "noteId" -> note.id.toString,
          "receiverId" -> receiver.id.toString,
          "type" -> "status_update",
          "status" -> note.status.toString.toLowerCase
        )
        sendNotification(fcmToken, notification, data)
      case None =>
        Logger[IO].debug(s"No FCM token for user ${sender.id}")

  private def sendNotification(
                                fcmToken: String,
                                notification: FirebaseNotification,
                                data: Map[String, String]
                              ): IO[Unit] =
    for {
      accessToken <- getAccessToken
      _ <- sendFirebaseMessage(fcmToken, notification, data, accessToken)
    } yield ()

  private def getAccessToken: IO[String] =
    // In a real implementation, you would use Google's OAuth2 libraries
    // This is a simplified version - you should implement proper OAuth2 flow
    IO {
      // Read service account key file
      val serviceAccountJson = Source.fromFile(config.serviceAccountKeyPath).mkString
      // Extract client_email and private_key from the JSON
      // Generate JWT token and exchange for access token
      // For now, returning a placeholder
      "placeholder-access-token"
    }.handleErrorWith { error =>
      Logger[IO].error(error)("Failed to get Firebase access token") *>
        IO.raiseError(new RuntimeException("Could not obtain Firebase access token"))
    }

  private def sendFirebaseMessage(
                                   fcmToken: String,
                                   notification: FirebaseNotification,
                                   data: Map[String, String],
                                   accessToken: String
                                 ): IO[Unit] =
    val message = FirebaseMessage(
      FirebaseMessagePayload(
        token = fcmToken,
        notification = notification,
        data = data
      )
    )

    val request = basicRequest
      .post(uri"https://fcm.googleapis.com/v1/projects/${config.projectId}/messages:send")
      .auth.bearer(accessToken)
      .body(message)
      .response(asString)

    backend.send(request).flatMap { response =>
      response.code.code match
        case code if code >= 200 && code < 300 =>
          Logger[IO].info("Firebase notification sent successfully")
        case 404 =>
          Logger[IO].warn(s"FCM token not found or invalid: $fcmToken")
        case _ =>
          Logger[IO].error(s"Failed to send Firebase notification: ${response.body}")
    }.handleErrorWith { error =>
      Logger[IO].error(error)("Error sending Firebase notification")
    }

// Mock implementations for testing/development
class MockEmailService extends EmailService[IO]:
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def sendVerificationEmail(email: String, token: String): IO[Unit] =
    Logger[IO].info(s"Mock: Sending verification email to $email with token $token")

  override def sendPasswordResetEmail(email: String, token: String): IO[Unit] =
    Logger[IO].info(s"Mock: Sending password reset email to $email with token $token")

class MockNotificationService extends NotificationService[IO]:
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def sendNoteNotification(receiver: User, sender: User, note: Note): IO[Unit] =
    Logger[IO].info(s"Mock: Sending note notification to ${receiver.username} from ${sender.username}: ${note.title}")

  override def sendStatusUpdateNotification(sender: User, receiver: User, note: Note): IO[Unit] =
    Logger[IO].info(s"Mock: Sending status update notification to ${sender.username} about note ${note.title} status: ${note.status}")
