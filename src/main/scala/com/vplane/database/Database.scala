package com.vplane.database

import cats.effect.*
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import com.vplane.config.DatabaseConfig
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Database:
  def createTransactor(config: DatabaseConfig): Resource[IO, HikariTransactor[IO]] =
    val connectEC = ExecutionContext.fromExecutor(java.util.concurrent.Executors.newFixedThreadPool(32))

    HikariTransactor.newHikariTransactor[IO](
      config.driver,
      config.url,
      config.username,
      config.password,
      connectEC
    )

  def initializeDb(config: DatabaseConfig): IO[Unit] =
    IO {
      val flyway = Flyway.configure()
        .dataSource(config.url, config.username, config.password)
        .load()
      flyway.migrate()
    }.void