package com.iven.musicplayergo.discovery

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.InputStream
import java.util.UUID

/** Test-owned API29+ media only. Never publish fixtures into the user's gallery. */
@RequiresApi(29)
internal class PendingDiscoveryPhotos(private val resolver: ContentResolver) {
    private val photos = mutableListOf<Uri>()
    val createdUris: List<Uri> get() = photos.toList()

    fun insert(name: String, input: () -> InputStream): Uri {
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "discovery-test-${UUID.randomUUID()}-$name")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VibeDiscoveryTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        // Track before opening/copying, so normal failure teardown can remove a partial row.
        photos += uri
        input().use { source ->
            checkNotNull(resolver.openOutputStream(uri)).use { source.copyTo(it) }
        }
        // Owner-side exact-URI reads work while pending. If the process dies, Android's
        // pending expiry handles the private row; @After is not a process-death guarantee.
        return uri
    }

    fun cleanup(): Cleanup {
        val created = photos.size
        var deleted = 0
        val failures = mutableListOf<String>()
        for (uri in photos.toList()) {
            try {
                check(resolver.delete(uri, null, null) == 1) { "Fixture row was not removed: $uri" }
                photos.remove(uri)
                deleted++
            } catch (error: Exception) {
                failures += error.toString()
            }
        }
        return Cleanup(created, deleted, failures)
    }

    data class Cleanup(val created: Int, val deleted: Int, val errors: List<String>)
}
