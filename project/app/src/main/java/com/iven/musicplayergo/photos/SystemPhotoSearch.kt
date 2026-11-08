package com.iven.musicplayergo.photos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

enum class SystemPhotoSearchCapability { MAY_REQUEST, UNSUPPORTED, UNKNOWN }
object PhotoSearchPolicy {
    const val REQUIRED_EXTENSION = 19
    fun capability(api: Int, tExtension: Int?): SystemPhotoSearchCapability = when {
        api < 33 -> SystemPhotoSearchCapability.UNSUPPORTED
        tExtension == null -> SystemPhotoSearchCapability.UNKNOWN
        tExtension >= REQUIRED_EXTENSION -> SystemPhotoSearchCapability.MAY_REQUEST
        else -> SystemPhotoSearchCapability.UNSUPPORTED
    }
    fun query(value: String) = value.trim().take(200)
    fun explanation(api: Int, tExtension: Int?): String = when (capability(api, tExtension)) {
        SystemPhotoSearchCapability.MAY_REQUEST -> "系统支持请求关键词高亮；选图器仍可能忽略关键词。"
        SystemPhotoSearchCapability.UNKNOWN -> "无法读取系统搜索能力，将使用普通选图。"
        SystemPhotoSearchCapability.UNSUPPORTED ->
            (if (api >= 33 && tExtension != null) "系统扩展版本为 $tExtension，关键词高亮需要 $REQUIRED_EXTENSION。" else "此 Android 版本不支持关键词高亮。") +
                "可在手机设置中检查系统更新或 Google Play 系统更新；更新 App 无法补齐此能力。当前可普通选图，找不到相册时可从文件选择。"
    }
}

/** Public constants copied verbatim from MediaStore docs (36.1 / T extension 19).
 * compileSdk 33 cannot reference these symbols; no alpha AndroidX dependency is needed.
 * Never adds album highlight, location access, or grants for unselected search results.
 */
class SystemPhotoSearchLauncher(private val context: Context,
    private val detect: () -> SystemPhotoSearchCapability = {
        PhotoSearchPolicy.capability(Build.VERSION.SDK_INT,
            if (Build.VERSION.SDK_INT >= 33) runCatching { SdkExtensions.getExtensionVersion(33) }.getOrNull() else null)
    }) {
    fun capability(): SystemPhotoSearchCapability = detect()
    fun explanation(): String = PhotoSearchPolicy.explanation(Build.VERSION.SDK_INT,
        if (Build.VERSION.SDK_INT >= 33) runCatching { SdkExtensions.getExtensionVersion(33) }.getOrNull() else null)
    fun intent(query: String): Intent {
        val text = PhotoSearchPolicy.query(query)
        require(text.isNotEmpty())
        check(capability() == SystemPhotoSearchCapability.MAY_REQUEST)
        return Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
            .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, minOf(100, MediaStore.getPickImagesMaxLimit()))
            .putExtra(EXTRA_SEARCH, Bundle().apply { putString(KEY_QUERY, text); putInt(KEY_TYPE, 1) })
    }
    /** Exceptions from unavailable/denying providers always reach the ordinary picker path. */
    fun launch(query: String, launch: (Intent) -> Unit, fallback: () -> Unit) {
        if (capability() != SystemPhotoSearchCapability.MAY_REQUEST) { fallback(); return }
        try { launch(intent(query)) } catch (_: Exception) { fallback() }
    }
    fun ordinaryIntent(): Intent = ActivityResultContracts.PickMultipleVisualMedia(100)
        .createIntent(context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    companion object {
        const val EXTRA_SEARCH = "android.provider.extra.PICK_IMAGES_HIGHLIGHT_SEARCH_RESULTS"
        const val KEY_QUERY = "android.provider.media.key.PICK_IMAGES_HIGHLIGHT_SEARCH_TEXT_QUERY"
        const val KEY_TYPE = "android.provider.media.key.PICK_IMAGES_HIGHLIGHT_TYPE"
    }
}
