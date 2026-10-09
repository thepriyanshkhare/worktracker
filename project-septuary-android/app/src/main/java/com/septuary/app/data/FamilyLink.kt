package com.septuary.app.data

import android.content.Context
import java.security.SecureRandom

/**
 * The private family code that links this app to the parents' Supervisor app.
 *
 * Nothing secret is compiled into either app or stored in the (public) source repository: the
 * code is generated on this phone (100 bits of randomness), shared once with the parents, and
 * used as the Firestore path. Without the code, the data cannot be located or read.
 */
object FamilyLink {
    private const val PREFS = "septuary_family"
    private const val KEY_CODE = "code"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    const val CODE_LENGTH = 20

    fun code(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_CODE, null)?.let { return it }
        val rnd = SecureRandom()
        val code = (1..CODE_LENGTH).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")
        prefs.edit().putString(KEY_CODE, code).commit()
        return code
    }

    /** Re-use an existing code (e.g. after reinstalling) so the parents don't need to re-pair. */
    fun setCode(context: Context, raw: String): Boolean {
        val code = normalize(raw)
        if (code.length != CODE_LENGTH) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CODE, code).commit()
        return true
    }

    fun normalize(raw: String): String = raw.uppercase().filter { it in ALPHABET }

    fun pretty(code: String): String = code.chunked(4).joinToString("-")

    /** Root Firestore collection for this family; must match firestore.rules' pattern. */
    fun root(context: Context): String = "sp_" + code(context)
}
