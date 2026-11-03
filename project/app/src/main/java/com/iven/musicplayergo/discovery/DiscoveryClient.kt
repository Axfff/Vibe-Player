package com.iven.musicplayergo.discovery

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface DiscoveryGateway {
    fun status(): DiscoveryStatus
    fun discover(token: String, query: DiscoveryQuery): DiscoveryResults
    fun analyze(token: String, jpeg: ByteArray): PhotoIntent
    fun cancel()
}

/** One instance per operation; no redirects, key, automatic retries or background uploads. */
class DiscoveryClient(private val endpoint: String, private val deadlineMillis: Long = 45_000) : DiscoveryGateway {
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<HttpURLConnection?>()

    override fun status() = DiscoveryJson.status(request("/v1/providers/status"))
    override fun discover(token: String, query: DiscoveryQuery) = DiscoveryJson.results(
        request("/v1/discover", token, DiscoveryJson.query(query).toByteArray(Charsets.UTF_8)))
    override fun analyze(token: String, jpeg: ByteArray) = DiscoveryJson.photo(
        request("/v1/photo-intent", token, jpeg, photo = true))

    override fun cancel() {
        cancelled.set(true)
        active.getAndSet(null)?.disconnect()
    }

    private fun request(path: String, token: String? = null, data: ByteArray? = null, photo: Boolean = false): String {
        if (cancelled.get()) throw DiscoveryFailure("cancelled")
        val connection = URL(endpoint + path).openConnection() as HttpURLConnection
        active.set(connection)
        val timedOut = AtomicBoolean(false)
        val deadline = deadlines.schedule({ timedOut.set(true); connection.disconnect() }, deadlineMillis, TimeUnit.MILLISECONDS)
        try {
            if (timedOut.get()) throw DiscoveryFailure("timeout")
            if (cancelled.get()) throw DiscoveryFailure("cancelled")
            connection.connectTimeout = 8_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("X-Discovery-Token", token)
            if (data != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(data.size)
                connection.setRequestProperty("Content-Type", if (photo) "image/jpeg" else "application/json")
                if (photo) connection.setRequestProperty("X-Cloud-Consent", "true")
                connection.outputStream.use { it.write(data) }
            }
            val code = connection.responseCode
            if (code != 200) throw DiscoveryFailure("http_$code")
            if (connection.contentType?.substringBefore(';') != "application/json") throw DiscoveryFailure("invalid_response")
            val raw = connection.inputStream.use { it.readBytesBounded(1_000_000).toString(Charsets.UTF_8) }
            if (timedOut.get()) throw DiscoveryFailure("timeout")
            if (cancelled.get()) throw DiscoveryFailure("cancelled")
            return raw
        } catch (error: DiscoveryFailure) { throw error
        } catch (_: SocketTimeoutException) { throw DiscoveryFailure("timeout")
        } catch (_: Exception) { throw DiscoveryFailure(if (timedOut.get()) "timeout" else if (cancelled.get()) "cancelled" else "network_error")
        } finally {
            deadline.cancel(false)
            active.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    companion object {
        private val deadlines = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Discovery deadline").apply { isDaemon = true }
        }
    }
}
