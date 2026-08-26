package com.checkin.app.ui.about

/** The subject and body handed to the user's mail app. */
data class FeedbackDraft(val subject: String, val body: String)

/** The build the report came from, as the user would read it in the store listing. */
data class AppBuild(val versionName: String, val versionCode: Int)

/** The handset the report came from — enough to reproduce a problem, and nothing more. */
data class DeviceBuild(val manufacturer: String, val model: String, val androidRelease: String, val sdkInt: Int)

/**
 * Builds the feedback email.
 *
 * This app makes no network calls of its own, so feedback leaves through the user's own mail app and
 * nothing is transmitted until they press send — the merged manifest holds no INTERNET permission,
 * so it could not transmit anything if it tried (see [ExternalLinks]). It is also
 * why the diagnostics sit in the body as plain text rather than an attachment or a hidden header:
 * the user reads exactly what they are about to send, and the footer invites them to delete it.
 */
object Feedback {

    /** The contact address on the Play listing, so users see one address in both places. */
    const val ADDRESS = "saksham@nexusai.world"

    fun draft(app: AppBuild, device: DeviceBuild): FeedbackDraft = FeedbackDraft(
        subject = "CheckIn feedback (${app.versionName})",
        // Leading blank lines put the cursor above the footer in every mail app worth the name.
        body = buildString {
            append("\n\n")
            append("---\n")
            append("These lines help me reproduce problems. Delete them if you'd rather not share.\n")
            append("App: CheckIn ${app.versionName} (${app.versionCode})\n")
            append("Device: ${deviceName(device.manufacturer, device.model)}\n")
            append("Android: ${device.androidRelease} (API ${device.sdkInt})\n")
        },
    )

    /**
     * Phone `model` values often already lead with the manufacturer ("Pixel 8" does not, "moto g84"
     * does), so repeating it would read as "Motorola moto g84".
     */
    private fun deviceName(manufacturer: String, model: String): String {
        val make = manufacturer.trim()
        val name = model.trim()
        if (make.isEmpty()) return name
        if (name.isEmpty()) return make
        return if (name.startsWith(make, ignoreCase = true)) name else "$make $name"
    }
}
