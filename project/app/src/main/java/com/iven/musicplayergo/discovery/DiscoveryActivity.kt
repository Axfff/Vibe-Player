package com.iven.musicplayergo.discovery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iven.musicplayergo.BaseActivity
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.ActivityDiscoveryBinding
import com.iven.musicplayergo.databinding.DiscoveryTrackBinding
import com.iven.musicplayergo.utils.Theming
import com.iven.musicplayergo.photos.PhotoModelsDialogFragment

class DiscoveryActivity : BaseActivity() {
    private lateinit var binding: ActivityDiscoveryBinding
    internal val model: DiscoveryViewModel by viewModels()
    private var showCollection = false
    private var editing = true
    private var optionsExpanded = false
    private var photoDetailsExpanded = false
    private val expandedTracks = mutableSetOf<String>()
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
        editing = savedInstanceState?.getBoolean("editing") ?: (model.results == null)
        optionsExpanded = savedInstanceState?.getBoolean("options") ?: false
        photoDetailsExpanded = savedInstanceState?.getBoolean("photo-details") ?: false
        expandedTracks.addAll(savedInstanceState?.getStringArrayList("track-details").orEmpty())
        binding.discoveryToolbar.apply {
            title = getString(R.string.discovery_title)
            setNavigationIcon(R.drawable.ic_discovery_back)
            setNavigationContentDescription(R.string.discovery_navigate_back)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        onBackPressedDispatcher.addCallback(this) {
            when {
                showCollection -> { showCollection = false; render() }
                !editing -> { editing = true; render() }
                else -> finish()
            }
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
        binding.discoveryCustomService.isChecked = model.customService
        binding.discoveryCustomService.setOnCheckedChangeListener { _, checked -> model.useCustomService(checked) }
        binding.discoveryConfirm.setOnCheckedChangeListener { _, _ -> renderActions() }
        binding.discoveryConnect.setOnClickListener { readDraft(); model.connect() }
        binding.discoverySearch.setOnClickListener {
            if (binding.discoveryConfirm.isChecked) {
                readDraft()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(binding.discoveryQuery.windowToken, 0)
                binding.discoveryQuery.clearFocus()
                model.discover()
            }
        }
        binding.discoveryPickPhoto.setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.discoveryAnalyzePhoto.setOnClickListener { showPhotoConsent() }
        binding.discoveryAnalyzeLocal.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.discovery_local_title)
                .setMessage(R.string.discovery_local_consent)
                .setPositiveButton(R.string.discovery_local_analyze) { _, _ -> model.analyzeLocal() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.discoveryPhotoModels.setOnClickListener { PhotoModelsDialogFragment.forDiscovery().show(supportFragmentManager, "discovery-models") }
        binding.discoveryManual.setOnClickListener { model.useManual() }
        binding.discoveryApplyPhoto.setOnClickListener {
            model.applySuggestions()
            bindDraft()
            binding.discoveryConfirm.isChecked = false
        }
        binding.discoveryCancel.setOnClickListener { model.cancel() }
        binding.discoveryClearExclusions.setOnClickListener { model.clearExclusions() }
        binding.discoveryOptionsToggle.setOnClickListener { optionsExpanded = !optionsExpanded; renderOptions() }
        binding.discoveryPhotoDetails.setOnClickListener { photoDetailsExpanded = !photoDetailsExpanded; render() }
        binding.discoveryEdit.setOnClickListener {
            editing = true
            render()
            binding.discoveryScroll.smoothScrollTo(0, 0)
        }
        binding.discoveryAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.discovery_about)
                .setMessage(getString(R.string.discovery_metadata_reason) + "\n\n" + getString(R.string.discovery_official_notice))
                .setPositiveButton(android.R.string.ok, null).show()
        }
        binding.discoveryCollection.setOnClickListener {
            showCollection = !showCollection
            render()
            binding.discoveryScroll.smoothScrollTo(0, 0)
        }
        var observedResults = model.results
        model.updates.observe(this) {
            val newResults = model.results !== observedResults && model.message in setOf("results", "no_results")
            if (newResults) editing = false
            observedResults = model.results
            render()
            if (newResults && !showCollection) {
                binding.discoveryScroll.post { binding.discoveryScroll.smoothScrollTo(0, 0) }
            }
        }
        if (model.status == null && (!model.customService || model.endpoint.isNotBlank()) && !model.busy) model.connect()
    }

    private fun choices(view: Spinner, options: List<String>) {
        view.adapter = ArrayAdapter(this, R.layout.discovery_spinner_item, options.map { DiscoveryLabels.text(it).resolve(this) }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
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
        renderOptions()
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
            binding.discoveryAnalyzeLocal, binding.discoveryPhotoModels,
            binding.discoveryCustomService,
            binding.discoveryScene, binding.discoveryMood, binding.discoveryTag, binding.discoveryMarket,
            binding.discoveryQuery, binding.discoveryEndpoint, binding.discoveryConfirm).forEach { it.isEnabled = !model.busy }
    }

    private fun renderOptions() {
        binding.discoveryOptions.isVisible = optionsExpanded
        binding.discoveryOptionsToggle.setText(if (optionsExpanded) R.string.discovery_options_close else R.string.discovery_options)
        ViewCompat.setStateDescription(binding.discoveryOptionsToggle, getString(if (optionsExpanded) R.string.discovery_expanded else R.string.discovery_collapsed))
        binding.discoveryOptionsSummary.text = (model.intent.tags + listOfNotNull(model.scene) + model.market)
            .joinToString(" · ") { DiscoveryLabels.text(it).resolve(this) }
        binding.discoveryClearExclusions.isVisible = model.collection.excluded.isNotEmpty() && (showCollection || !editing || optionsExpanded)
    }

    private fun render() {
        renderActions()
        renderOptions()
        val showForm = !showCollection && editing
        binding.discoveryCollection.text = if (showCollection) getString(R.string.discovery_back_search)
            else getString(R.string.discovery_collection, model.collection.saved.size)
        binding.discoveryHeading.setText(if (showCollection) R.string.discovery_collection_heading
            else if (showForm) R.string.discovery_heading else R.string.discovery_results_heading)
        binding.discoverySubtitle.text = if (showCollection) getString(R.string.discovery_collection_summary)
            else if (showForm) getString(R.string.discovery_intro)
            else getString(R.string.discovery_results_summary, model.results?.tracks?.size ?: 0)
        binding.discoveryForm.isVisible = showForm
        binding.discoverySearchActions.isVisible = showForm
        binding.discoveryEdit.isVisible = !showForm && !showCollection
        binding.discoveryResults.isVisible = !showForm
        val status = model.status
        binding.discoveryServiceFields.isVisible = model.customService
        binding.discoveryProviderStatus.text = if (!model.customService) getString(R.string.discovery_device_ready)
            else if (status == null) getString(R.string.discovery_status_unknown)
            else getString(if (status.canAnalyze) R.string.discovery_connected else R.string.discovery_blocked)
        binding.discoveryPhotoStatus.text = when {
            model.photo != null -> getString(R.string.discovery_photo_summary, model.photo!!.moods.joinToString { DiscoveryLabels.text(it).resolve(this) })
            model.photoUri != null -> getString(R.string.discovery_photo_selected)
            else -> getString(R.string.discovery_photo_none)
        }
        binding.discoveryAnalyzePhoto.isVisible = model.photoUri != null && model.customService && status?.canAnalyze == true
        binding.discoveryPhotoStatus.isVisible = model.photoUri != null || model.photo != null
        if (model.photo?.uncertainty == "high") binding.discoveryPhotoStatus.append("\n" + getString(R.string.discovery_photo_uncertain_suggestion))
        binding.discoveryManual.isVisible = model.photoUri != null || model.photo != null
        binding.discoveryPhotoDetails.isVisible = model.photo != null
        binding.discoveryPhotoDetails.setText(if (photoDetailsExpanded) R.string.discovery_hide_details else R.string.discovery_photo_details)
        ViewCompat.setStateDescription(binding.discoveryPhotoDetails, getString(if (photoDetailsExpanded) R.string.discovery_expanded else R.string.discovery_collapsed))
        binding.discoveryPhotoExplanation.isVisible = model.photo != null && photoDetailsExpanded
        binding.discoveryPhotoExplanation.text = model.localPhotoDescription?.resolve(this)
            ?: model.photo?.let { (it.scenes + it.moods + it.tags).joinToString(" · ") { value -> DiscoveryLabels.text(value).resolve(this) } }.orEmpty()
        binding.discoveryAnalyzeLocal.isVisible = model.photoUri != null
        binding.discoveryApplyPhoto.isVisible = model.photo != null
        binding.discoveryClearExclusions.isVisible = model.collection.excluded.isNotEmpty() && (!showForm || optionsExpanded)
        binding.discoveryMessage.text = if (model.collectionError != null) getString(R.string.discovery_storage_error) else message(model.message)
        binding.discoveryMessage.isVisible = model.collectionError != null || model.message !in
            setOf("ready", "manual", "photo_cancelled", "connected", "results", "photo_selected", "photo_suggested")
        binding.discoveryResults.removeAllViews()
        if (showCollection) {
            if (model.collection.saved.isEmpty() && model.collectionError == null) {
                binding.discoveryMessage.setText(R.string.discovery_collection_empty)
                binding.discoveryMessage.isVisible = true
            }
            model.collection.saved.forEach { addTrack(it.track, savedList = true) }
        } else if (!showForm) {
            model.results?.let { response ->
                if (model.message == "results" || model.message == "no_results") {
                    if (response.tracks.size < 8) {
                        binding.discoveryMessage.append("\n" + getString(R.string.discovery_shortage, response.tracks.size))
                        binding.discoveryMessage.isVisible = true
                    }
                    if (response.catalogs.any { it.error != null }) {
                        binding.discoveryMessage.append("\n" + getString(R.string.discovery_catalog_partial))
                        binding.discoveryMessage.isVisible = true
                    }
                }
                response.tracks.forEach { addTrack(it, savedList = false) }
            }
        }
    }

    private fun addTrack(track: DiscoveryTrack, savedList: Boolean) {
        val row = DiscoveryTrackBinding.inflate(layoutInflater, binding.discoveryResults, false)
        row.discoveryTrackTitle.text = track.title
        row.discoveryTrackArtist.text = track.artist
        val provider = getString(if (track.ref.provider == "itunes") R.string.discovery_provider_apple else R.string.discovery_provider_jamendo)
        row.discoveryTrackIdentity.text = "$provider · ${track.ref.id}"
        row.discoveryTrackSource.text = "${track.ref.provider} · ${track.ref.id}\n${track.officialUrl}"
        // Service prose may use another language; identity/content is retained while UI copy is local.
        row.discoveryTrackReason.text = getString(R.string.discovery_metadata_reason) + "\n" + getString(R.string.discovery_official_notice)
        row.discoveryTrackOpen.setOnClickListener { openOfficial(track) }
        val saved = model.collection.saved.any { it.track.ref == track.ref }
        row.discoveryTrackSave.setText(if (savedList) R.string.discovery_remove else if (saved) R.string.discovery_saved else R.string.discovery_save)
        row.discoveryTrackSave.setIconResource(if (saved) R.drawable.ic_favorite else R.drawable.ic_favorite_empty)
        row.discoveryTrackOpen.contentDescription = getString(R.string.discovery_track_action_description, getString(R.string.discovery_open), track.title)
        row.discoveryTrackSave.contentDescription = getString(R.string.discovery_track_action_description, row.discoveryTrackSave.text, track.title)
        row.discoveryTrackSave.isEnabled = model.collectionError == null && (savedList || !saved)
        row.discoveryTrackSave.setOnClickListener { if (savedList) model.remove(track.ref) else model.save(track) }
        row.discoveryTrackExclude.isVisible = !savedList
        row.discoveryTrackExclude.isEnabled = model.collectionError == null
        row.discoveryTrackExclude.setOnClickListener { model.exclude(track.ref) }
        fun renderDetails() {
            val expanded = track.ref.key in expandedTracks
            row.discoveryTrackDetailContent.isVisible = expanded
            row.discoveryTrackDetails.setText(if (expanded) R.string.discovery_hide_details else R.string.discovery_track_details)
            ViewCompat.setStateDescription(row.discoveryTrackDetails, getString(if (expanded) R.string.discovery_expanded else R.string.discovery_collapsed))
        }
        row.discoveryTrackDetails.setOnClickListener {
            if (!expandedTracks.add(track.ref.key)) expandedTracks.remove(track.ref.key)
            renderDetails()
        }
        renderDetails()
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
        "connected" -> getString(if (model.customService) R.string.discovery_connected else R.string.discovery_device_ready)
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
        "photo_applied" -> getString(R.string.discovery_photo_applied)
        "local_photo_model_missing" -> getString(R.string.discovery_local_missing)
        "local_photo_uncertain" -> getString(R.string.discovery_local_uncertain)
        "local_photo_failed" -> getString(R.string.discovery_local_failed)
        "invalid_endpoint" -> getString(R.string.discovery_invalid_endpoint)
        "invalid_input", "http_422" -> getString(R.string.discovery_invalid_input)
        "invalid_response", "response_too_large" -> getString(R.string.discovery_invalid_response)
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
        outState.putBoolean("editing", editing)
        outState.putBoolean("options", optionsExpanded)
        outState.putBoolean("photo-details", photoDetailsExpanded)
        outState.putStringArrayList("track-details", ArrayList(expandedTracks))
        super.onSaveInstanceState(outState)
    }
}
