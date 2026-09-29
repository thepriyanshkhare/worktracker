package com.septuary.app.crypto

import android.content.Context
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a user PIN into the SQLCipher database passphrase, using PBKDF2 with a random salt.
 * The salt is not secret — it's stored in plain SharedPreferences alongside a small marker.
 * The PIN itself is never stored anywhere, in any form.
 */
object PinCrypto {
    private const val PREFS = "septuary_pin_prefs"
    private const val KEY_SALT = "salt"
    private const val ITERATIONS = 150_000
    private const val KEY_LEN_BITS = 256

    fun hasPinSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(KEY_SALT)
    }

    /** Call once, when the user sets their PIN for the first time. Returns the derived passphrase. */
    fun setupPin(context: Context, pin: String): CharArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)).apply()
        return derive(pin, salt)
    }

    /** Call on every unlock attempt. The resulting passphrase either opens the DB, or doesn't. */
    fun deriveForUnlock(context: Context, pin: String): CharArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(KEY_SALT, null) ?: error("PIN not set up yet")
        val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        return derive(pin, salt)
    }

    private fun derive(pin: String, salt: ByteArray): CharArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        // SQLCipher passphrase API takes chars; hex-encode the derived key bytes.
        return keyBytes.joinToString("") { "%02x".format(it) }.toCharArray()
    }

    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.deleteDatabase("septuary_encrypted.db")
    }
}
