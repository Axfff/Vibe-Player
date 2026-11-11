package com.iven.musicplayergo.discovery

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** Synthetic transport fixture, only in androidTest; never a live catalog/model claim. */
class DiscoveryFixtureServer : Closeable {
    data class Request(val path: String, val body: String, val headers: Map<String, String>)
    data class Response(val body: String, val code: Int = 200, val delay: Long = 0, val type: String = "application/json")
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool()
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<Request>()
    val endpoint = "http://127.0.0.1:${server.localPort}"
    @Volatile var handler: (Request) -> Response = { request -> Response(if (request.path.endsWith("status")) status() else results()) }

    init {
        workers.submit {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                sockets.add(socket)
                workers.submit {
                    try {
                        socket.use {
                            socket.soTimeout = 5000
                            val input = socket.getInputStream().buffered()
                            fun line(): String {
                                val result = StringBuilder()
                                while (true) {
                                    val byte = input.read()
                                    if (byte == -1 || byte == 10) break
                                    if (byte != 13) result.append(byte.toChar())
                                }
                                return result.toString()
                            }
                            val first = line()
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val row = line()
                                if (row.isEmpty()) break
                                headers[row.substringBefore(':').lowercase()] = row.substringAfter(':').trim()
                            }
                            val body = ByteArray(headers["content-length"]?.toInt() ?: 0)
                            var offset = 0
                            while (offset < body.size) { val n = input.read(body, offset, body.size - offset); if (n < 0) break; offset += n }
                            val request = Request(first.split(' ')[1], body.toString(Charsets.UTF_8), headers)
                            requests.add(request)
                            val response = handler(request)
                            if (response.delay > 0) Thread.sleep(response.delay)
                            val bytes = response.body.toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 ${response.code} Fixture\r\nContent-Type: ${response.type}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(bytes); flush()
                            }
                        }
                    } catch (_: Exception) { /* Cancellation/disconnect is expected in tests. */ }
                    finally { sockets.remove(socket) }
                }
            }
        }
    }
    override fun close() { server.close(); sockets.forEach { it.close() }; workers.shutdownNow() }

    companion object {
        const val TOKEN = "fixture_token_only_1234567890"
        fun provider(name: String, mode: String = "live", state: String = "ok") = JSONObject().put("provider", name).put("mode", mode).put("state", state).put("errorCode", JSONObject.NULL)
        fun status(visionMode: String = "unconfigured") = JSONObject().put("demoToken", TOKEN)
            .put("vision", provider("vision", visionMode)).put("catalogs", JSONArray().put(provider("itunes")))
            .put("cloudRecipient", "Fixture only").put("visionModel", "fixture-model").toString()
        fun row(id: String = "123") = JSONObject().put("ref", JSONObject().put("provider", "itunes").put("providerTrackId", id))
            .put("title", "Fixture piano $id").put("artist", "Fixture artist $id")
            .put("officialUrl", "https://music.apple.com/us/album/fixture/99?i=$id")
            .put("recommendationReason", "Synthetic transport fixture; metadata match only")
            .put("linkNotice", "Region availability varies")
        fun results(rows: JSONArray = JSONArray().put(row()).put(row("456")), mode: String = "live") = JSONObject()
            .put("tracks", rows).put("shortageReason", "Fixture has fewer than eight results")
            .put("catalogs", JSONArray().put(provider("itunes", mode))).toString()
        fun photo(mode: String = "live") = JSONObject().put("vision", provider("vision", mode)).put("intent", JSONObject()
            .put("sceneTags", JSONArray().put("night")).put("visualMoodSuggestions", JSONArray().put("dreamy"))
            .put("musicDescriptors", JSONArray().put("ambient")).put("uncertainty", "high")).toString()
    }
}
