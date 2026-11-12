package com.checkin.app.platform

/** What the update offer shows for one [UpdateStatus]. */
sealed interface UpdateOffer {
    data object None : UpdateOffer

    /** Play's flexible dialog for [versionCode], which is then recorded as offered. */
    data class Prompt(val versionCode: Int) : UpdateOffer

    /** A flexible download has finished and waits for a restart to install. */
    data object RestartReady : UpdateOffer
}

/**
 * The update offer's one decision, pure so `UpdatePromptTest` can pin it.
 *
 * **One dialog per version code.** Play's flexible dialog is dismissible, and a dismissal is an
 * answer: re-asking on every launch turns an offer into a nag, which is the register the app
 * refuses everywhere else. A later build is a new question and gets asked once in turn.
 *
 * **A finished download is always surfaced**, whatever was offered before: it is the user's own
 * accepted update waiting on a restart, and Play installs nothing until the app asks it to.
 */
object UpdatePrompt {
    fun decide(status: UpdateStatus?, lastOfferedVersionCode: Int): UpdateOffer {
        if (status == null) return UpdateOffer.None
        if (status.downloaded) return UpdateOffer.RestartReady
        val available = status.availableVersionCode ?: return UpdateOffer.None
        return if (status.flexibleAllowed && available > lastOfferedVersionCode) {
            UpdateOffer.Prompt(available)
        } else {
            UpdateOffer.None
        }
    }
}
