package com.checkin.app.ui.presence

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
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
 * The credentials the fallback accepts. One value for both the question ([deviceUnlockStatus]) and
 * the prompt, because a status asked of one set and a prompt raised for another would offer a prompt
 * that fails, or send someone to set up a screen lock they already have.
 */
private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/**
 * Whether the device has a credential to fall back to. Re-read on resume by the screen, never
 * cached at first composition: the recovery for "none enrolled" is a round trip through system
 * settings, which returns no result and recreates no activity.
 */
internal fun deviceUnlockStatus(context: Context): Int =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)

/**
 * Opens the system screen that sets up a screen lock — the fallback's fallback, when there is no
 * credential to unlock with. False when nothing on the device handles the intent, so the caller can
 * say so instead.
 */
internal fun openScreenLockSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: ActivityNotFoundException) {
    Log.e(TAG, "No screen-lock settings activity", e)
    false
}

/**
 * The device-unlock fallback the presence check offers when the camera cannot answer: builds the
 * [BiometricPrompt] and returns the function that raises it.
 *
 * Everything about the fallback is here: when it is offered
 * ([DEVICE_UNLOCK_OFFERED_AFTER_MS]), whether the device has one ([deviceUnlockStatus]), how it is
 * raised, what each outcome means, and the route to set one up ([openScreenLockSettings]).
 *
 * **The prompt is built here, in composition, and never on the button tap.** It registers its
 * callback against the host activity and outlives a recreation of that activity beneath it, so a
 * callback captured at tap time belongs to a composition a rotation has since discarded: the cancel
 * that follows is delivered to dead state, the caller's "prompt is up" flag is never cleared, and
 * the camera path stays shut for good. Keying the [remember] on [activity] re-registers a live
 * callback on every recreation, which is what makes that flag safe to save across one.
 *
 * The callbacks the prompt captures are held through [rememberUpdatedState] for the same reason: it
 * is remembered across recompositions and would otherwise hold the first lambdas it was given for
 * the life of the screen. The returned lambda is rebuilt each composition, so what it calls
 * directly needs no such wrapping.
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
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
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
