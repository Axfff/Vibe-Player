package com.iven.musicplayergo.discovery

import org.json.JSONArray
import org.json.JSONObject

/** Strict at identity/vision boundaries; unsupported catalog rows are rejected individually. */
object DiscoveryJson {
    private fun JSONObject.string(key: String): String = get(key) as? String ?: throw DiscoveryFailure("invalid_response")
    private fun strings(array: JSONArray) = (0 until array.length()).map { array.get(it) as? String ?: throw DiscoveryFailure("invalid_response") }
    private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
    private fun ref(value: JSONObject) = DiscoveryRef(value.string("provider"), value.string("providerTrackId"))
    private fun ref(value: DiscoveryRef) = JSONObject().put("provider", value.provider).put("providerTrackId", value.id)
    private fun intent(value: JSONObject) = MusicIntent(value.string("targetMood"), strings(value.getJSONArray("musicTags")), value.string("text"))
    private fun intent(value: MusicIntent) = JSONObject().put("targetMood", value.mood).put("musicTags", JSONArray(value.tags)).put("text", value.text)
    private fun provider(value: JSONObject) = ProviderState(value.string("provider"), value.string("mode"), value.string("state"), value.optString("errorCode").takeUnless { it == "null" || it.isBlank() })
    private fun providers(value: JSONObject) = objects(value.getJSONArray("catalogs")).map { provider(it) }
    private fun track(value: JSONObject): DiscoveryTrack {
        val track = DiscoveryTrack(ref(value.getJSONObject("ref")), value.string("title").trim(), value.string("artist").trim(), value.string("officialUrl"), value.optString("recommendationReason"), value.optString("linkNotice"))
        if (!track.valid()) throw DiscoveryFailure("invalid_track")
        return track
    }
    private fun track(value: DiscoveryTrack) = JSONObject().put("ref", ref(value.ref)).put("title", value.title).put("artist", value.artist).put("officialUrl", value.officialUrl)

    fun status(raw: String): DiscoveryStatus = checked {
        val value = JSONObject(raw)
        val token = value.string("demoToken")
        if (!token.matches(Regex("[A-Za-z0-9_-]{20,100}"))) throw DiscoveryFailure("invalid_response")
        DiscoveryStatus(token, provider(value.getJSONObject("vision")), providers(value), value.optString("cloudRecipient"), value.optString("visionModel").takeUnless { it == "null" }.orEmpty())
    }

    fun query(value: DiscoveryQuery): String {
        if (!value.valid()) throw DiscoveryFailure("invalid_input")
        return JSONObject().put("intent", intent(value.intent)).put("sceneTags", JSONArray(listOfNotNull(value.scene)))
            .put("confirmed", true).put("inputMode", if (value.photo == null) "manual" else "photo")
            .put("market", value.market).put("limit", 8).put("exclude", JSONArray(value.excluded.map { ref(it) }))
            .put("visualSuggestions", value.photo?.let { photo -> JSONObject().put("sceneTags", JSONArray(photo.scenes)).put("visualMoodSuggestions", JSONArray(photo.moods)).put("musicDescriptors", JSONArray(photo.tags)).put("uncertainty", photo.uncertainty) } ?: JSONObject.NULL).toString()
    }

    fun results(raw: String): DiscoveryResults = checked {
        val value = JSONObject(raw)
        val statuses = providers(value)
        val live = statuses.filter { it.mode == "live" && it.state == "ok" }.map { it.name }.toSet()
        val rows = value.getJSONArray("tracks")
        if (rows.length() > 8) throw DiscoveryFailure("invalid_response")
        val valid = (0 until rows.length()).mapNotNull { index ->
            try { track(rows.getJSONObject(index)).takeIf { it.ref.provider in live } } catch (_: Exception) { null }
        }.distinctBy { it.ref }
        val rejected = rows.length() - valid.size
        val shortage = value.optString("shortageReason").takeUnless { it == "null" }.orEmpty()
        DiscoveryResults(valid, if (rejected > 0) "$shortage\n$rejected invalid or non-live results rejected.".trim() else shortage, statuses)
    }

    fun photo(raw: String): PhotoIntent = checked {
        val root = JSONObject(raw)
        val status = provider(root.getJSONObject("vision"))
        if (status.mode != "live" || status.state != "ok") throw DiscoveryFailure("vision_not_live")
        val value = root.getJSONObject("intent")
        if (value.keys().asSequence().toSet() != setOf("sceneTags", "visualMoodSuggestions", "musicDescriptors", "uncertainty")) throw DiscoveryFailure("vision_invalid_output")
        PhotoIntent(strings(value.getJSONArray("sceneTags")), strings(value.getJSONArray("visualMoodSuggestions")), strings(value.getJSONArray("musicDescriptors")), value.string("uncertainty")).also {
            if (!it.valid()) throw DiscoveryFailure("vision_invalid_output")
        }
    }

    fun encode(value: DiscoveryCollection): String = JSONObject().put("version", 1)
        .put("saved", JSONArray(value.saved.map { JSONObject().put("track", track(it.track)).put("intent", intent(it.intent)).put("market", it.market) }))
        .put("excluded", JSONArray(value.excluded.map { ref(it) })).toString()

    fun decode(raw: String): DiscoveryCollection = checked {
        val value = JSONObject(raw)
        if (value.getInt("version") != 1) throw DiscoveryFailure("collection_version")
        DiscoveryCollection(objects(value.getJSONArray("saved")).map { SavedDiscovery(track(it.getJSONObject("track")), intent(it.getJSONObject("intent")), it.string("market")) }, objects(value.getJSONArray("excluded")).map { ref(it) })
    }

    private inline fun <T> checked(block: () -> T): T = try { block() } catch (error: DiscoveryFailure) { throw error } catch (_: Exception) { throw DiscoveryFailure("invalid_response") }
}
