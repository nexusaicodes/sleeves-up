package com.checkin.app.ui.presence

import android.util.Log
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val TAG = "DeviceUnlock"

/**
 * The device-unlock fallback the presence check offers when the camera cannot answer: builds the
 * [BiometricPrompt] and returns the function that raises it.
 *
 * Its own file because everything about the fallback used to live inside a file named for the camera
 * screen, while [AuthGate] — the name a reader tries first — held only the timeout that decides when
 * to offer it.
 *
 * **The prompt is built here, in composition, and never on the button tap.** It registers its
 * callback against the host activity and outlives a recreation of that activity beneath it, so a
 * callback captured at tap time belongs to a composition a rotation has since discarded: the cancel
 * that follows is delivered to dead state, the caller's "prompt is up" flag is never cleared, and
 * the camera path stays shut for good. Keying the [remember] on [activity] re-registers a live
 * callback on every recreation, which is what makes that flag safe to save across one.
 *
 * Every callback is held through [rememberUpdatedState] for the same reason: the prompt is
 * remembered across recompositions and would otherwise capture the first lambda it was given and
 * hold it for the life of the screen.
 *
 * The caller owns the flag rather than this function, because the same flag also gates the camera's
 * success effect. [onRaised] and [onEnded] bracket the prompt being up; [onEnded] fires on **any**
 * error including a cancel, since a prompt the user backed out of must reopen the camera path.
 */
@Composable
internal fun rememberDeviceUnlock(
    activity: FragmentActivity?,
    promptTitle: String,
    promptSubtitle: String,
    unavailableMessage: String,
    onSucceeded: () -> Unit,
    onRaised: () -> Unit,
    onEnded: () -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val currentOnSucceeded by rememberUpdatedState(onSucceeded)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnError by rememberUpdatedState(onError)

    val prompt = remember(activity) {
        activity?.let { host ->
            BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        currentOnSucceeded()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        currentOnEnded()
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            currentOnError(errString.toString())
                        }
                    }
                },
            )
        }
    }

    val promptInfo = remember(promptTitle, promptSubtitle) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    return {
        if (prompt == null) {
            onError(unavailableMessage)
        } else {
            onRaised()
            // The platform can refuse the prompt outright — an authenticator combination it rejects,
            // a fragment transaction past onSaveInstanceState. Only an error callback clears the
            // caller's flag, and a refusal delivers none, so a throw left unhandled would shut the
            // camera path too.
            runCatching { prompt.authenticate(promptInfo) }.onFailure { e ->
                Log.e(TAG, "Device unlock could not be raised", e)
                onEnded()
                onError(unavailableMessage)
            }
        }
    }
}
