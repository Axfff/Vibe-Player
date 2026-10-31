package com.iven.musicplayergo.library

import java.io.File

/** A missing pre-Android 10 DATA value must not abort the entire library query. */
internal fun legacyMediaFolderName(path: String?, rootFolderName: String): String =
    path?.let { File(it).parentFile?.name }?.takeUnless { it == "0" } ?: rootFolderName
