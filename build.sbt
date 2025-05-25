// build.sbt
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    name := "vplane",
    libraryDependencies ++= Seq(
      // Cats Effect & Core
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "org.typelevel" %% "cats-core" % "2.10.0",

      // HTTP4S for REST API
      "org.http4s" %% "http4s-ember-server" % "0.23.23",
      "org.http4s" %% "http4s-ember-client" % "0.23.23",
      "org.http4s" %% "http4s-dsl" % "0.23.23",
      "org.http4s" %% "http4s-circe" % "0.23.23",

      // JSON handling
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",

      // Database
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC4",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC4",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC4",
      "org.flywaydb" % "flyway-core" % "9.22.3",
      "org.postgresql" % "postgresql" % "42.6.0",

      // JWT
      "com.github.jwt-scala" %% "jwt-circe" % "9.4.4",

      // Password hashing
      "at.favre.lib" % "bcrypt" % "0.9.0",

      // HTTP Client for external services
      "com.softwaremill.sttp.client3" %% "core" % "3.9.1",
      "com.softwaremill.sttp.client3" %% "circe" % "3.9.1",
      "com.softwaremill.sttp.client3" %% "cats" % "3.9.1",

      // Configuration
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.4",
      "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.4",

      // Logging
      "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
      "ch.qos.logback" % "logback-classic" % "1.4.11",

      // UUID
      "io.chrisdavenport" %% "fuuid" % "0.8.0-M2",

      // Testing
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
    )
  )
