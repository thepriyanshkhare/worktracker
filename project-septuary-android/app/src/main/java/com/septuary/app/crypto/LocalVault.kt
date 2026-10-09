package com.septuary.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key-value store for the few things that must be readable while the app is
 * LOCKED (no PIN in memory): the "Taken" taps from a notification and the set of items already
 * done today (so a reminder for something already taken is suppressed).
 *
 * Values are AES-256-GCM encrypted with a key that lives in the Android Keystore (hardware
 * backed on modern phones) and never leaves it. The key needs no user authentication because
 * the alarm/notification receivers must use it with nobody at the screen.
 */
object LocalVault {
    private const val ALIAS = "septuary_local_vault_v1"
    private const val PREFS = "septuary_vault"
    private const val GCM_TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Never throws: a Keystore hiccup must not break unlocking or crash a receiver. */
    @Synchronized
    fun put(context: Context, name: String, value: String?): Boolean = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value == null) {
            prefs.edit().remove(name).commit()
        } else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
            prefs.edit().putString(name, packed).commit()
        }
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun get(context: Context, name: String): String? {
        val packed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null) ?: return null
        return try {
            val (ivB64, ctB64) = packed.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null // Corrupt or key lost: treat as empty rather than crash a background receiver.
        }
    }
}
