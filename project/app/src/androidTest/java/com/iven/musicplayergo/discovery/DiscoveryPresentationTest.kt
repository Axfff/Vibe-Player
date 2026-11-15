package com.iven.musicplayergo.discovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoveryPresentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun provider(mode: String = "live", state: String = "ok", error: String? = null, name: String = "itunes") = ProviderState(name, mode, state, error)
    private fun status(vararg catalogs: ProviderState, vision: ProviderState = provider("unconfigured", name = "vision")) = DiscoveryStatus("", vision, catalogs.toList(), "", "")
    private fun show(value: DiscoveryStatus?) = DiscoveryPresentation.service(value).resolve(context)

    @Test fun catalogAvailabilityIsIndependentOfCloudAndUncheckedNeverMeansLiveSuccess() {
        val ready = show(status(provider()))
        val failed = show(status(provider(state = "error", error = "timeout")))
        assertTrue(ready.contains("Apple music catalog: last request succeeded"))
        assertTrue(failed.contains("Apple music catalog: unavailable on the last request"))
        listOf(ready, failed).forEach { assertTrue(it.contains("Cloud photo analysis: not configured")) }
        assertTrue(show(status(provider(state = "not_run"))).contains("live availability not checked yet"))
        assertTrue(show(status(provider(state = "ready"))).contains("live availability not checked yet"))
        assertTrue(show(status(provider(state = "ok", error = "timeout"))).contains("unavailable on the last request"))
        assertTrue(show(status(provider("mock"))).contains("test mode; no live results"))
        assertTrue(show(status(provider("unconfigured"))).contains("Apple music catalog: not configured"))
        assertTrue(show(status(provider("future"))).contains("status unknown"))
        assertTrue(show(status(provider(state = "future"))).contains("status unknown"))
        assertTrue(show(status(provider(name = "future"))).contains("Music source:"))
        assertTrue(show(status()).contains("No music sources reported"))
        assertTrue(show(null).contains("Check the service"))
        val partial = show(status(provider(), provider(state = "error", name = "jamendo"), vision = provider(name = "vision")))
        assertTrue(partial.contains("Apple music catalog: last request succeeded"))
        assertTrue(partial.contains("Jamendo music catalog: unavailable"))
        assertTrue(partial.contains("Cloud photo analysis: last request succeeded"))
    }

    @Test fun optionalCatalogMetadataIsBoundedAndLegacyCollectionRemainsReadable() {
        val intent = MusicIntent(tags = listOf("rock"), text = "piano")
        val track = DiscoveryJson.results(DiscoveryFixtureServer.results(JSONArray().put(
            DiscoveryFixtureServer.row().put("tags", JSONArray().put("Rock")).put("recommendationReason", "服务中文说明")))).tracks.single()
        val reason = DiscoveryPresentation.reason(track, intent).resolve(context)
        assertTrue(reason.contains("Title/artist words: piano"))
        assertTrue(reason.contains("Matching catalog tags: Rock"))
        assertFalse(reason.contains("服务"))
        val collection = DiscoveryCollection(listOf(SavedDiscovery(track, intent, "JP")), listOf(DiscoveryRef("itunes", "456")))
        val encoded = DiscoveryJson.encode(collection)
        val restored = DiscoveryJson.decode(encoded)
        assertEquals(listOf("Rock"), restored.saved.single().track.catalogTags)
        assertEquals(reason, DiscoveryPresentation.reason(restored.saved.single().track, restored.saved.single().intent).resolve(context))
        assertEquals(collection.excluded, restored.excluded)
        val old = JSONObject(encoded).apply { getJSONArray("saved").getJSONObject(0).getJSONObject("track").remove("tags") }
        val legacy = DiscoveryJson.decode(old.toString())
        assertEquals(track.ref, legacy.saved.single().track.ref)
        assertEquals("JP", legacy.saved.single().market)
        assertNull(legacy.saved.single().track.catalogTags)
        assertTrue(DiscoveryPresentation.reason(legacy.saved.single().track, intent).resolve(context).contains("Catalog tags are unavailable"))
        val absent = DiscoveryPresentation.reason(track.copy(title = "Other", artist = "Someone", catalogTags = emptyList()), intent).resolve(context)
        assertTrue(absent.contains("No exact search-word match"))
        assertTrue(absent.contains("No exact catalog-tag match"))
        for (tags in listOf<Any>("not an array", JSONArray().put(3), JSONArray().put(""), JSONArray().put("x".repeat(41)), JSONArray(List(31) { "rock" }))) {
            val parsed = DiscoveryJson.results(DiscoveryFixtureServer.results(JSONArray().put(DiscoveryFixtureServer.row().put("tags", tags)))).tracks.single()
            assertNull(parsed.catalogTags) // bad optional evidence cannot invent a match or erase a valid identity
        }
    }
}
