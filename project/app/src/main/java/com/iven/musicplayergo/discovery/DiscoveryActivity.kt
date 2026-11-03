package com.iven.musicplayergo.discovery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iven.musicplayergo.BaseActivity
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.ActivityDiscoveryBinding
import com.iven.musicplayergo.databinding.DiscoveryTrackBinding
import com.iven.musicplayergo.utils.Theming

class DiscoveryActivity : BaseActivity() {
    private lateinit var binding: ActivityDiscoveryBinding
    internal val model: DiscoveryViewModel by viewModels()
    private var showCollection = false
    private var bindingInputs = false
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        model.selectPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(Theming.resolveTheme(this))
        binding = ActivityDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showCollection = savedInstanceState?.getBoolean("collection") ?: false
        binding.discoveryToolbar.apply {
            title = getString(R.string.discovery_title)
            setNavigationIcon(android.R.drawable.ic_media_previous)
            setNavigationContentDescription(android.R.string.cancel)
            setNavigationOnClickListener { finish() }
        }
        choices(binding.discoveryScene, listOf(getString(R.string.discovery_none)) + DiscoveryChoices.scenes)
        choices(binding.discoveryMood, DiscoveryChoices.moods)
        choices(binding.discoveryTag, listOf(getString(R.string.discovery_none)) + DiscoveryChoices.tags)
        choices(binding.discoveryMarket, DiscoveryChoices.markets)
        bindDraft()
        listOf(binding.discoveryScene, binding.discoveryMood, binding.discoveryTag, binding.discoveryMarket).forEach { spinner ->
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { inputChanged() }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        binding.discoveryQuery.doAfterTextChanged { inputChanged() }
        binding.discoveryEndpoint.doAfterTextChanged { if (!bindingInputs) model.endpoint = it.toString() }
        binding.discoveryConfirm.setOnCheckedChangeListener { _, _ -> renderActions() }
        binding.discoveryConnect.setOnClickListener { readDraft(); model.connect() }
        binding.discoverySearch.setOnClickListener {
            if (binding.discoveryConfirm.isChecked) { readDraft(); model.discover() }
        }
        binding.discoveryPickPhoto.setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.discoveryAnalyzePhoto.setOnClickListener { showPhotoConsent() }
        binding.discoveryManual.setOnClickListener { model.useManual() }
        binding.discoveryApplyPhoto.setOnClickListener {
            model.applySuggestions()
            bindDraft()
            binding.discoveryConfirm.isChecked = false
        }
        binding.discoveryCancel.setOnClickListener { model.cancel() }
        binding.discoveryClearExclusions.setOnClickListener { model.clearExclusions() }
        binding.discoveryCollection.setOnClickListener {
            showCollection = !showCollection
            render()
            binding.discoveryScroll.smoothScrollTo(0, 0)
        }
        model.updates.observe(this) {
            render()
            if (model.message == "results" && !showCollection) {
                binding.discoveryScroll.post { binding.discoveryScroll.smoothScrollTo(0, binding.discoveryMessage.top) }
            }
        }
        if (model.status == null && model.endpoint.isNotBlank() && !model.busy) model.connect()
    }

    private fun choices(view: Spinner, options: List<String>) {
        view.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
    }

    private fun bindDraft() {
        bindingInputs = true
        binding.discoveryEndpoint.setText(model.endpoint)
        binding.discoveryQuery.setText(model.intent.text)
        binding.discoveryMood.setSelection(DiscoveryChoices.moods.indexOf(model.intent.mood))
        binding.discoveryTag.setSelection(DiscoveryChoices.tags.indexOf(model.intent.tags.firstOrNull()) + 1)
        binding.discoveryScene.setSelection(DiscoveryChoices.scenes.indexOf(model.scene) + 1)
        binding.discoveryMarket.setSelection(DiscoveryChoices.markets.indexOf(model.market))
        bindingInputs = false
    }

    private fun inputChanged() {
        if (bindingInputs) return
        readDraft()
        binding.discoveryConfirm.isChecked = false
    }

    private fun readDraft() {
        model.endpoint = binding.discoveryEndpoint.text.toString()
        model.intent = MusicIntent(
            DiscoveryChoices.moods[binding.discoveryMood.selectedItemPosition],
            listOfNotNull(DiscoveryChoices.tags.getOrNull(binding.discoveryTag.selectedItemPosition - 1)),
            binding.discoveryQuery.text.toString().trim())
        model.scene = DiscoveryChoices.scenes.getOrNull(binding.discoveryScene.selectedItemPosition - 1)
        model.market = DiscoveryChoices.markets[binding.discoveryMarket.selectedItemPosition]
    }

    private fun showPhotoConsent() {
        val status = model.status
        if (status?.canAnalyze != true) { model.showMessage("manual_model_blocked"); return }
        MaterialAlertDialogBuilder(this).setTitle(R.string.discovery_consent_title)
            .setMessage(getString(R.string.discovery_consent, status.recipient, status.model))
            .setPositiveButton(R.string.discovery_agree) { _, _ -> model.analyzeConsented() }
            .setNegativeButton(R.string.discovery_refuse) { _, _ -> model.useManual() }
            .show()
    }

    private fun renderActions() {
        binding.discoverySearch.isEnabled = !model.busy && binding.discoveryConfirm.isChecked
        binding.discoveryProgress.isVisible = model.busy
        binding.discoveryCancel.isVisible = model.busy
        listOf(binding.discoveryConnect, binding.discoveryPickPhoto, binding.discoveryAnalyzePhoto, binding.discoveryApplyPhoto,
            binding.discoveryScene, binding.discoveryMood, binding.discoveryTag, binding.discoveryMarket,
            binding.discoveryQuery, binding.discoveryEndpoint, binding.discoveryConfirm).forEach { it.isEnabled = !model.busy }
    }

    private fun render() {
        renderActions()
        binding.discoveryCollection.text = if (showCollection) getString(R.string.discovery_back_search)
            else getString(R.string.discovery_collection, model.collection.saved.size)
        binding.discoveryForm.isVisible = !showCollection
        val status = model.status
        binding.discoveryProviderStatus.text = if (status == null) getString(R.string.discovery_status_unknown)
            else (status.catalogs + status.vision).joinToString("\n") { "${it.name}: ${it.mode} / ${it.state}${it.error?.let { error -> " ($error)" }.orEmpty()}" }
        binding.discoveryPhotoStatus.text = when {
            model.photo != null -> "${model.photo!!.scenes.joinToString()}\n${model.photo!!.moods.joinToString()} · ${model.photo!!.tags.joinToString()}"
            model.photoUri != null -> getString(R.string.discovery_photo_selected)
            else -> getString(R.string.discovery_photo_none)
        }
        binding.discoveryAnalyzePhoto.isVisible = model.photoUri != null
        binding.discoveryApplyPhoto.isVisible = model.photo != null
        binding.discoveryClearExclusions.isVisible = model.collection.excluded.isNotEmpty()
        binding.discoveryMessage.text = if (model.collectionError != null) getString(R.string.discovery_storage_error) else message(model.message)
        binding.discoveryResults.removeAllViews()
        if (showCollection) {
            if (model.collection.saved.isEmpty() && model.collectionError == null) binding.discoveryMessage.setText(R.string.discovery_collection_empty)
            model.collection.saved.forEach { addTrack(it.track, savedList = true) }
        } else {
            model.results?.let { response ->
                if (model.message == "results" || model.message == "no_results") {
                    binding.discoveryMessage.append("\n${response.shortage}\n" + response.catalogs.joinToString("; ") { "${it.name}: ${it.error ?: it.state} (${it.mode})" })
                }
                response.tracks.forEach { addTrack(it, savedList = false) }
            }
        }
    }

    private fun addTrack(track: DiscoveryTrack, savedList: Boolean) {
        val row = DiscoveryTrackBinding.inflate(layoutInflater, binding.discoveryResults, false)
        row.discoveryTrackTitle.text = track.title
        row.discoveryTrackArtist.text = track.artist
        row.discoveryTrackSource.text = "${track.ref.provider} · ${track.ref.id}\n${track.officialUrl}"
        row.discoveryTrackReason.text = listOf(track.reason, track.linkNotice, getString(R.string.discovery_official_notice)).filter { it.isNotBlank() }.joinToString("\n")
        row.discoveryTrackOpen.setOnClickListener { openOfficial(track) }
        val saved = model.collection.saved.any { it.track.ref == track.ref }
        row.discoveryTrackSave.setText(if (savedList) R.string.discovery_remove else if (saved) R.string.discovery_saved else R.string.discovery_save)
        row.discoveryTrackSave.isEnabled = model.collectionError == null && (savedList || !saved)
        row.discoveryTrackSave.setOnClickListener { if (savedList) model.remove(track.ref) else model.save(track) }
        row.discoveryTrackExclude.isVisible = !savedList
        row.discoveryTrackExclude.isEnabled = model.collectionError == null
        row.discoveryTrackExclude.setOnClickListener { model.exclude(track.ref) }
        binding.discoveryResults.addView(row.root)
    }

    internal fun openOfficial(track: DiscoveryTrack) {
        if (!track.valid()) { model.showMessage("unsafe_link"); return }
        try {
            // Unscoped HTTPS ACTION_VIEW lets Android use the official app or a browser.
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.officialUrl)).addCategory(Intent.CATEGORY_BROWSABLE))
            model.showMessage("opened")
        } catch (_: ActivityNotFoundException) { model.showMessage("no_browser")
        } catch (_: SecurityException) { model.showMessage("no_browser") }
    }

    private fun message(code: String): String = when (code) {
        "ready", "manual", "photo_cancelled" -> getString(R.string.discovery_ready)
        "manual_model_blocked", "vision_not_live" -> getString(R.string.discovery_blocked)
        "connected" -> getString(R.string.discovery_connected)
        "loading" -> getString(R.string.discovery_loading)
        "cancelled" -> getString(R.string.discovery_cancelled)
        "no_results" -> getString(R.string.discovery_no_results)
        "results" -> getString(R.string.discovery_result_count, model.results?.tracks?.size ?: 0)
        "saved" -> getString(R.string.discovery_saved_message)
        "removed" -> getString(R.string.discovery_removed_message)
        "excluded" -> getString(R.string.discovery_excluded_message)
        "exclusions_cleared" -> getString(R.string.discovery_exclusions_cleared)
        "photo_selected" -> getString(R.string.discovery_photo_selected)
        "photo_suggested" -> getString(R.string.discovery_photo_suggested)
        "invalid_endpoint" -> getString(R.string.discovery_invalid_endpoint)
        "invalid_input", "http_422" -> getString(R.string.discovery_invalid_input)
        "invalid_response" -> getString(R.string.discovery_invalid_response)
        "collection_limit" -> getString(R.string.discovery_limit)
        "collection_unreadable", "collection_read_failed", "collection_write_failed" -> getString(R.string.discovery_storage_error)
        "no_browser" -> getString(R.string.discovery_no_browser)
        "unsafe_link" -> getString(R.string.discovery_unsafe_link)
        "opened" -> getString(R.string.discovery_opened)
        "invalid_image", "image_byte_limit", "image_pixel_limit", "vision_invalid_output", "vision_consent_changed", "http_413", "http_415" -> getString(R.string.discovery_photo_failed)
        "http_429", "http_500", "http_502", "http_503", "http_403" -> getString(R.string.discovery_retry_later)
        else -> getString(R.string.discovery_network_error)
    }

    override fun onStop() {
        model.cancel()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("collection", showCollection)
        super.onSaveInstanceState(outState)
    }
}
