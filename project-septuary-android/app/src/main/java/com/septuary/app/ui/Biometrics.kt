package com.septuary.app.ui

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Shows the system fingerprint/face sheet bound to [cipher]. [onResult] receives the cipher
 * authorised for exactly one operation, or null if the user cancelled / chose PIN / it failed.
 */
fun promptBiometric(
    activity: FragmentActivity,
    cipher: Cipher,
    title: String,
    subtitle: String,
    onResult: (Cipher?) -> Unit
) {
    var delivered = false
    fun deliver(c: Cipher?) {
        if (!delivered) {
            delivered = true
            onResult(c)
        }
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                deliver(result.cryptoObject?.cipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                deliver(null)
            }
            // onAuthenticationFailed = one unrecognised touch; the sheet stays up for a retry.
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    try {
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    } catch (_: Exception) {
        deliver(null)
    }
}
