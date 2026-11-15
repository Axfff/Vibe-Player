package com.iven.musicplayergo.discovery

interface DiscoveryStorage {
    fun load(): DiscoveryCollection
    fun save(value: DiscoveryCollection)
}

/** Commit before publishing state; a failed write cannot masquerade as a successful save. */
class DiscoveryRepository(private val storage: DiscoveryStorage) {
    var collection = DiscoveryCollection()
        private set
    var readable = false
        private set

    fun load(): DiscoveryCollection {
        readable = false
        val loaded = storage.load()
        requireValid(loaded)
        collection = loaded
        readable = true
        return collection
    }

    fun save(track: DiscoveryTrack, intent: MusicIntent, market: String) {
        if (!readable) throw DiscoveryFailure("collection_unreadable")
        if (!track.valid() || !intent.valid() || market !in DiscoveryChoices.markets) throw DiscoveryFailure("invalid_track")
        if (collection.saved.any { it.track.ref == track.ref }) return
        update(collection.copy(saved = collection.saved + SavedDiscovery(track.copy(reason = "", linkNotice = ""), intent, market)))
    }

    fun remove(ref: DiscoveryRef) = update(collection.copy(saved = collection.saved.filterNot { it.track.ref == ref }))

    fun exclude(ref: DiscoveryRef) {
        if (!ref.valid()) throw DiscoveryFailure("invalid_track")
        update(collection.copy(excluded = (collection.excluded + ref).distinct()))
    }

    fun clearExclusions() = update(collection.copy(excluded = emptyList()))
    fun exclusions() = (collection.excluded + collection.saved.map { it.track.ref }).distinct()

    private fun update(value: DiscoveryCollection) {
        if (!readable) throw DiscoveryFailure("collection_unreadable")
        requireValid(value)
        storage.save(value)
        collection = value
    }

    private fun requireValid(value: DiscoveryCollection) {
        if (value.saved.size > 200 || value.excluded.size > 300) throw DiscoveryFailure("collection_limit")
        if (value.saved.any { !it.track.valid() || !it.intent.valid() || it.market !in DiscoveryChoices.markets } ||
            value.excluded.any { !it.valid() } || value.saved.map { it.track.ref }.distinct().size != value.saved.size ||
            value.excluded.distinct().size != value.excluded.size) throw DiscoveryFailure("collection_invalid")
    }
}
