package com.checkin.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.adevinta.spark.components.snackbars.SnackbarHostState

/** The app-level snackbar host, provided by the top-level scaffold so any screen can post messages. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalSnackbarHostState not provided")
}
