package com.vplane.services

import cats.effect.IO
import cats.implicits.*
import com.vplane.config.TwilioConfig
import sttp.client3.*
import sttp.client3.circe.*
import io.circe.generic.auto.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait EmailService[F[_]]:
  def sendVerificationEmail(email: String, token: String): F[Unit]
  def sendPasswordResetEmail(email: String, token: String): F[Unit]

case class TwilioEmailRequest(
                               personalizations: List[TwilioPersonalization],
                               from: TwilioEmailAddress,
                               subject: String,
                               content: List[TwilioContent]
                             )

case class TwilioPersonalization(
                                  to: List[TwilioEmailAddress]
                                )

case class TwilioEmailAddress(
                               email: String,
                               name: Option[String] = None
                             )

case class TwilioContent(
                          `type`: String,
                          value: String
                        )

class TwilioEmailService(
                          config: TwilioConfig,
                          baseUrl: String,
                          backend: SttpBackend[IO, Any]
                        ) extends EmailService[IO]:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def sendVerificationEmail(email: String, token: String): IO[Unit] =
    val verificationUrl = s"$baseUrl/api/auth/verify-email?token=$token"
    val emailContent = s"""
                          |Welcome to NoteApp!
                          |
                          |Please verify your email address by clicking the link below:
                          |$verificationUrl
                          |
                          |If you didn't create an account, please ignore this email.
                          |
                          |Best regards,
                          |NoteApp Team
    """.stripMargin

    sendEmail(email, "Verify Your Email Address", emailContent)

  override def sendPasswordResetEmail(email: String, token: String): IO[Unit] =
    val resetUrl = s"$baseUrl/reset-password?token=$token"
    val emailContent = s"""
                          |Password Reset Request
                          |
                          |You requested a password reset for your NoteApp account.
                          |Click the link below to reset your password:
                          |$resetUrl
                          |
                          |If you didn't request this, please ignore this email.
                          |
                          |Best regards,
                          |NoteApp Team
    """.stripMargin

    sendEmail(email, "Reset Your Password", emailContent)

  private def sendEmail(to: String, subject: String, content: String): IO[Unit] =
    val request = TwilioEmailRequest(
      personalizations = List(TwilioPersonalization(
        to = List(TwilioEmailAddress(to))
      )),
      from = TwilioEmailAddress(config.fromEmail),
      subject = subject,
      content = List(TwilioContent("text/plain", content))
    )

    val apiRequest = basicRequest
      .post(uri"https://api.sendgrid.com/v3/mail/send")
      .auth.bearer(config.authToken)
      .body(request)
      .response(asString)

    backend.send(apiRequest).flatMap { response =>
      response.code.code match
        case code if code >= 200 && code < 300 =>
          Logger[IO].info(s"Email sent successfully to $to")
        case _ =>
          Logger[IO].error(s"Failed to send email to $to: ${response.body}")
    }.handleErrorWith { error =>
      Logger[IO].error(error)(s"Error sending email to $to")
    }