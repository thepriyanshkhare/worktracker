package com.septuary.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** show(message, actionLabel, onAction) — one app-wide snackbar for confirmations and Undo. */
typealias ShowSnackbar = (String, String?, (() -> Unit)?) -> Unit

val LocalSnackbar = staticCompositionLocalOf<ShowSnackbar> { { _, _, _ -> } }
