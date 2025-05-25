package com.vplane.domain

import java.util.UUID

case class JwtClaims(
                      userId: UUID,
                      email: String,
                      exp: Long
                    )

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

object JsonCodecs:
  given Decoder[JwtClaims] = deriveDecoder
  given Encoder[JwtClaims] = deriveEncoder
