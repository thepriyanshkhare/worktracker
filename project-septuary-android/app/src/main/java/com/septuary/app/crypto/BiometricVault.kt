package com.septuary.app.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Fingerprint / face unlock without weakening the PIN design.
 *
 * The database key derived from the PIN is wrapped (AES-256-GCM) by a Keystore key that can
 * only be used right after a successful STRONG biometric check, and that Android destroys
 * automatically if a new fingerprint is enrolled. The PIN always remains the fallback; nothing
 * here stores or can recover the PIN itself.
 */
object BiometricVault {
    private const val ALIAS = "septuary_biometric_v1"
    private const val PREFS = "septuary_biometric"
    private const val KEY_CT = "ct"
    private const val KEY_IV = "iv"
    private const val KEY_DECLINED = "declined"

    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(context: Context): Boolean =
        prefs(context).contains(KEY_CT) && prefs(context).contains(KEY_IV)

    fun wasDeclined(context: Context): Boolean = prefs(context).getBoolean(KEY_DECLINED, false)

    fun setDeclined(context: Context) {
        prefs(context).edit().putBoolean(KEY_DECLINED, true).apply()
    }

    /** A fresh key + cipher ready to encrypt; hand it to BiometricPrompt as a CryptoObject. */
    fun cipherForEnrol(): Cipher? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        gen.init(spec.build())
        val key = gen.generateKey()
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    } catch (_: Exception) {
        null
    }

    /** Cipher ready to decrypt the stored key, or null if biometrics were reset (falls back to PIN). */
    fun cipherForUnlock(context: Context): Cipher? {
        val iv = prefs(context).getString(KEY_IV, null) ?: return null
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = ks.getKey(ALIAS, null) as? SecretKey ?: run { disable(context); return null }
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable(context)
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Called with the cipher BiometricPrompt has just authorised. */
    fun store(context: Context, authorisedCipher: Cipher, passphrase: CharArray): Boolean = try {
        val ct = authorisedCipher.doFinal(String(passphrase).toByteArray(Charsets.UTF_8))
        prefs(context).edit()
            .putString(KEY_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(authorisedCipher.iv, Base64.NO_WRAP))
            .remove(KEY_DECLINED)
            .apply()
        true
    } catch (_: Exception) {
        false
    }

    fun reveal(context: Context, authorisedCipher: Cipher): CharArray? = try {
        val ct = prefs(context).getString(KEY_CT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        ct?.let { String(authorisedCipher.doFinal(it), Charsets.UTF_8).toCharArray() }
    } catch (_: Exception) {
        null
    }

    fun disable(context: Context) {
        prefs(context).edit().remove(KEY_CT).remove(KEY_IV).apply()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
