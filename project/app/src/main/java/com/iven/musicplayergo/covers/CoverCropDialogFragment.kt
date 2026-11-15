package com.iven.musicplayergo.covers

import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.iven.musicplayergo.photos.*

class CoverCropDialogFragment : DialogFragment() {
    private var cropView: CropImageView? = null
    private var status: TextView? = null
    private var selected: Bitmap? = null
    private var selectedUri: Uri? = null
    private var launched = false

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) dismissAllowingStateLoss() else {
            selectedUri = uri
            load(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean(LAUNCHED) ?: false
        selectedUri = (savedInstanceState?.getString(URI) ?: arguments?.getString(URI))?.let(Uri::parse)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val crop = CropImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            contentDescription = getString(R.string.cover_crop_preview)
        }
        val message = TextView(requireContext()).apply {
            setPadding(padding)
            text = getString(R.string.cover_crop_loading)
        }
        cropView = crop
        status = message
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding)
            addView(crop)
            addView(message)
        }
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.cover_crop_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.cover_save, null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                        isEnabled = selected != null
                        setOnClickListener { save(dialog) }
                    }
                    if (selectedUri != null && selected == null) {
                        load(selectedUri!!)
                    } else if (!launched && selected == null) {
                        launched = true
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
    }

    private fun load(uri: Uri) {
        status?.setText(R.string.cover_crop_loading)
        lifecycleScope.launch {
            val bitmap = try { withContext(Dispatchers.IO) {
                val request = candidateRequest()
                if (request == null) {
                    (CoverImageDecoder(requireContext()).decode(uri) as? CoverResult.Success)?.value
                } else {
                    val bytes = verifyCandidate(requireContext(), target()!!, request, uri.toString(), requireArguments().getString("fingerprint")!!)
                    SafePhotoDecoder(requireContext()).decode(bytes, 2048)
                }
            } } catch(e: CancellationException) { throw e } catch (_: Exception) { null }
            if (bitmap == null) {
                status?.setText(R.string.cover_error_decode)
                return@launch
            }
            selected?.recycle()
            selected = bitmap
            cropView?.setCropBitmap(bitmap)
            status?.setText(R.string.cover_crop_help)
            (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
        }
    }

    private fun save(dialog: AlertDialog) {
        val target = target() ?: return
        val output = try { cropView?.export() } catch (_: Exception) { null } ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        status?.setText(R.string.cover_saving)
        lifecycleScope.launch {
            val result = try { withContext(Dispatchers.IO) {
                val request = candidateRequest()
                if (request == null) CoverRepositories.get(requireContext()).setCover(
                    target, CoverImageInput.CroppedBitmap(output), CoverSource.USER_SELECTED)
                else {
                    verifyCandidate(requireContext(), target, request, selectedUri.toString(), requireArguments().getString("fingerprint")!!)
                    PhotoRepositories.get(requireContext()).guarded(target, request) {
                        check(targetExists(requireContext(), target))
                        CoverRepositories.get(requireContext()).setCover(target, CoverImageInput.CroppedBitmap(output), CoverSource.RECOMMENDED)
                    }
                }
            } } catch(e: CancellationException) { throw e }
              catch (_: Exception) { CoverResult.Failure(CoverError.SOURCE_NOT_READABLE) }
              finally { output.recycle() }
            when (result) {
                is CoverResult.Success -> {
                    parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(CHANGED_KEY to target.storageKey))
                    dismiss()
                }
                is CoverResult.Failure -> {
                    status?.text = getString(R.string.cover_error_save, result.error.name)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }
        }
    }

    private fun target(): CoverTarget? = when (requireArguments().getString(TYPE)) {
        ALBUM -> CoverTarget.Album(
            requireArguments().getLong(ID),
            requireArguments().getString(ARTIST),
            requireArguments().getString(TITLE)
        )
        PLAYLIST -> requireArguments().getString(ID)?.let(CoverTarget::Playlist)
        else -> null
    }

    private fun candidateRequest(): PhotoRequest? = requireArguments().getString("request_id")?.let {
        PhotoRequest(target()!!.storageKey, requireArguments().getString("scope_identity")!!, requireArguments().getLong("scope_revision"), it)
    }

    override fun onDestroyView() {
        cropView = null
        status = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(LAUNCHED, launched)
        outState.putString(URI, selectedUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        selected?.recycle()
        selected = null
        super.onDestroy()
    }

    companion object {
        const val RESULT_KEY = "custom_cover_changed"
        const val CHANGED_KEY = "target_key"
        private const val TYPE = "type"
        private const val ID = "id"
        private const val ARTIST = "artist"
        private const val TITLE = "title"
        private const val ALBUM = "album"
        private const val PLAYLIST = "playlist"
        private const val LAUNCHED = "picker_launched"
        private const val URI = "selected_uri"

        fun forAlbum(albumId: Long, artist: String?, title: String?) = CoverCropDialogFragment().apply {
            arguments = bundleOf(TYPE to ALBUM, ID to albumId, ARTIST to artist, TITLE to title)
        }
        fun forPlaylist(id: String) = CoverCropDialogFragment().apply {
            arguments = bundleOf(TYPE to PLAYLIST, ID to id)
        }
        fun forCandidate(target: CoverTarget, uri: String, request: PhotoRequest, fingerprint: String): CoverCropDialogFragment {
            val dialog = when (target) {
                is CoverTarget.Playlist -> forPlaylist(target.playlistId)
                is CoverTarget.Album -> forAlbum(target.albumId, target.artistSnapshot, target.albumTitleSnapshot)
            }
            dialog.requireArguments().apply {
                putString(URI, uri); putString("request_id", request.requestId); putString("scope_identity", request.identity)
                putLong("scope_revision", request.revision); putString("fingerprint", fingerprint)
            }
            return dialog
        }
    }
}

