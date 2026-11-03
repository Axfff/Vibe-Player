package com.iven.musicplayergo.library

import java.util.Locale

/** Pure candidate filtering. This does NOT grant access to files or declare them playable. */
internal object MediaScanPolicy {
    val audioExtensions = setOf("mp3", "ogg", "opus", "flac", "wav", "m4a", "aac", "amr", "3gp", "mid", "midi")

    fun isAudioCandidate(path: String?, displayName: String?, mimeType: String?): Boolean {
        if (path.isNullOrBlank() || !path.startsWith("/") || '\u0000' in path) return false
        val mime = mimeType.orEmpty().lowercase(Locale.ROOT)
        // Do not reinterpret a known non-audio type merely because its name ends in .mp3.
        if (mime.startsWith("audio/")) return true
        if (mime.isNotBlank() && mime != "application/octet-stream" && mime != "application/ogg") return false
        val name = displayName?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/')
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExtensions
    }
}

internal enum class MediaScanOutcome { COMPLETE, NO_CANDIDATES, PARTIAL, TIMED_OUT }

internal data class MediaScanReport(
    val outcome: MediaScanOutcome,
    val attempted: Int = 0,
    val indexed: Int = 0,
    val skipped: Int = 0,
    val limited: Boolean = false
)
