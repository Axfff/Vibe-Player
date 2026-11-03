package com.iven.musicplayergo.discovery

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** App-private, excluded from Android backup. Never writes photo bytes or stream URLs. */
class DiscoveryFileStorage(file: File) : DiscoveryStorage {
    private val atomic = AtomicFile(file)
    override fun load(): DiscoveryCollection = try {
        atomic.openRead().use { input ->
            val bytes = input.readBytesBounded(1_000_000)
            DiscoveryJson.decode(bytes.toString(Charsets.UTF_8))
        }
    } catch (_: FileNotFoundException) {
        if (atomic.baseFile.exists()) throw DiscoveryFailure("collection_read_failed")
        DiscoveryCollection()
    } catch (_: Exception) { throw DiscoveryFailure("collection_read_failed") }

    override fun save(value: DiscoveryCollection) {
        if (atomic.baseFile.exists() && !atomic.baseFile.isFile) throw DiscoveryFailure("collection_write_failed")
        val data = DiscoveryJson.encode(value).toByteArray(Charsets.UTF_8)
        if (data.size > 1_000_000) throw DiscoveryFailure("collection_limit")
        val output = try { atomic.startWrite() } catch (_: Exception) { throw DiscoveryFailure("collection_write_failed") }
        try {
            output.write(data)
            atomic.finishWrite(output)
            // AtomicFile reports some sync/rename failures only in logcat. Verify the
            // committed bytes before publishing success to the repository/UI.
            val committed = atomic.openRead().use { it.readBytesBounded(1_000_000) }
            if (!committed.contentEquals(data)) throw DiscoveryFailure("collection_write_failed")
        } catch (_: Exception) {
            atomic.failWrite(output)
            throw DiscoveryFailure("collection_write_failed")
        }
    }
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer)
        if (size == -1) break
        if (output.size() + size > limit) throw DiscoveryFailure("response_too_large")
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
