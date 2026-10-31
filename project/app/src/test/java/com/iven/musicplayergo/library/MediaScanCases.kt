package com.iven.musicplayergo.library

internal data class MediaScanCase(
    val name: String,
    val path: String?,
    val filename: String?,
    val mime: String?,
    val expected: Boolean
)

internal fun mediaScanCases() = listOf(
    MediaScanCase("audio mime", "/storage/Music/a", "a", "audio/mpeg", true),
    MediaScanCase("null metadata mp3", "/storage/Download/a.mp3", "a.mp3", null, true),
    MediaScanCase("null metadata ogg", "/storage/Download/a.ogg", "a.ogg", null, true),
    MediaScanCase("upper extension", "/storage/Music/A.MP3", "A.MP3", null, true),
    MediaScanCase("generic MIME", "/storage/Music/a.flac", "a.flac", "application/octet-stream", true),
    MediaScanCase("ogg MIME", "/storage/Music/a.ogg", "a.ogg", "application/ogg", true),
    MediaScanCase("fallback filename", "/storage/Music/a.wav", null, null, true),
    MediaScanCase("upper MIME", "/storage/Music/a", "a", "AUDIO/OPUS", true),
    MediaScanCase("missing path", null, "a.mp3", "audio/mpeg", false),
    MediaScanCase("blank path", "", "a.mp3", "audio/mpeg", false),
    MediaScanCase("URI is not path", "content://media/external/audio/1", "a.mp3", "audio/mpeg", false),
    MediaScanCase("relative path", "Music/a.mp3", "a.mp3", "audio/mpeg", false),
    MediaScanCase("NUL path", "/storage/a\u0000.mp3", "a.mp3", "audio/mpeg", false),
    MediaScanCase("image disguised as audio", "/storage/a.mp3", "a.mp3", "image/png", false),
    MediaScanCase("video 3gp", "/storage/a.3gp", "a.3gp", "video/3gpp", false),
    MediaScanCase("unknown file", "/storage/readme.txt", "readme.txt", null, false)
)
