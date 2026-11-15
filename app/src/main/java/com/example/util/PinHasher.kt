package com.example.util

import java.security.MessageDigest

object PinHasher {
    private const val APP_SALT = "um_tazkiyah_pos_salt_v1"

    fun hashPin(rawPin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$APP_SALT:${rawPin.trim()}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(rawPin: String, storedHash: String): Boolean {
        return hashPin(rawPin) == storedHash
    }
}
