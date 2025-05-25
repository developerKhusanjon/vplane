package com.vplane.services

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordUtils:

  extension (password: String)
    def bcryptBounded: String =
      BCrypt.withDefaults().hashToString(12, password.toCharArray)

    def isBcryptedBounded(hash: String): Boolean =
      val result = BCrypt.verifyer().verify(password.toCharArray, hash)
      result.verified
