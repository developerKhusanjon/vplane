package com.vplane.config

import pureconfig.*
import pureconfig.generic.derivation.default.*
import cats.effect.IO
import pureconfig.module.catseffect.syntax.*

case class DatabaseConfig(
                           driver: String,
                           url: String,
                           username: String,
                           password: String,
                           maxPoolSize: Int
                         ) derives ConfigReader

case class ServerConfig(
                         host: String,
                         port: Int
                       ) derives ConfigReader

case class JwtConfig(
                      secret: String,
                      expirationHours: Int
                    ) derives ConfigReader

case class TwilioConfig(
                         accountSid: String,
                         authToken: String,
                         fromEmail: String
                       ) derives ConfigReader

case class FirebaseConfig(
                           projectId: String,
                           serviceAccountKeyPath: String
                         ) derives ConfigReader

case class AppConfig(
                      server: ServerConfig,
                      database: DatabaseConfig,
                      jwt: JwtConfig,
                      twilio: TwilioConfig,
                      firebase: FirebaseConfig,
                      emailVerificationBaseUrl: String
                    ) derives ConfigReader

object Config:
  def load: IO[AppConfig] =
    ConfigSource.default.loadF[IO, AppConfig]()
