package com.iven.musicplayergo.covers

import java.io.File
import java.io.IOException

/** Best-effort reclamation after interruption. Caller holds the cover transaction lock. */
internal object CoverFileRecovery {
    private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val albumName = Regex("[1-9][0-9]*(?:-$UUID_PATTERN)?\\.webp(?:\\.tmp|\\.bak|\\.delete)?")
    private val playlistName = Regex("$UUID_PATTERN(?:-$UUID_PATTERN)?\\.webp(?:\\.tmp|\\.bak|\\.delete)?")

    fun recover(root: File, covers: List<CustomCover>) {
        for (type in CoverTargetType.values()) {
            try {
                val directory = File(root, type.name.lowercase())
                // Do not traverse a redirected directory or recurse into unrelated contents.
                if (directory.canonicalFile != File(root.canonicalFile, directory.name)) continue
                val referenced = covers.filter { it.targetType == type }.map { it.localFileName }.toSet()
                val protected = referenced.flatMap { listOf(it, "$it.bak", "$it.delete") }.toSet()
                for (name in referenced) {
                    val file = File(directory, name)
                    if (!file.exists()) {
                        for (suffix in listOf(".bak", ".delete")) {
                            val backup = File(directory, name + suffix)
                            if (isDirectFile(directory, backup) && backup.renameTo(file)) break
                        }
                    }
                }
                val ownedName = if (type == CoverTargetType.ALBUM) albumName else playlistName
                for (file in directory.listFiles().orEmpty()) {
                    if (file.name !in protected && ownedName.matches(file.name) && isDirectFile(directory, file)) {
                        file.delete() // A failed delete is retried on the next recovery; metadata is untouched.
                    }
                }
            } catch (_: IOException) {
                // Unavailable files are not grounds to discard their records or fail application startup.
            } catch (_: SecurityException) { }
        }
    }

    private fun isDirectFile(directory: File, file: File): Boolean =
        file.isFile && file.canonicalFile == File(directory.canonicalFile, file.name)
}
