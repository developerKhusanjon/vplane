package com.vplane

import cats.effect.*
import cats.implicits.*
import com.vplane.config.Config
import com.vplane.database.{Database, DoobieUserRepository, DoobieNoteRepository}
import com.vplane.services.*
import com.vplane.http.HttpServer
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp:
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    val program = for {
      config <- Config.load
      _ <- Logger[IO].info("Configuration loaded successfully")

      _ <- Database.initializeDb(config.database)
      _ <- Logger[IO].info("Database initialized successfully")

      transactor <- Database.createTransactor(config.database)
      httpBackend <- HttpClientCatsBackend.resource[IO]()

      userRepository = new DoobieUserRepository(transactor)
      noteRepository = new DoobieNoteRepository(transactor)

      // Choose between real services or mock services based on environment
      emailService = if (sys.env.get("USE_MOCK_SERVICES").contains("true"))
        new MockEmailService()
      else
        new TwilioEmailService(config.twilio, config.emailVerificationBaseUrl, httpBackend)

      notificationService = if (sys.env.get("USE_MOCK_SERVICES").contains("true"))
        new MockNotificationService()
      else
        new FirebaseNotificationService(config.firebase, httpBackend)

      authService = new AuthServiceImpl(userRepository, config.jwt, emailService)
      noteService = new NoteServiceImpl(noteRepository, userRepository, notificationService)
      userService = new UserServiceImpl(userRepository)

      server = new HttpServer(authService, noteService, userService, config)

      _ <- server.start
    } yield ()

    program.use(_ => IO.never).as(ExitCode.Success)
      .handleErrorWith { error =>
        Logger[IO].error(error)("Application failed to start") *>
          IO.pure(ExitCode.Error)
      }