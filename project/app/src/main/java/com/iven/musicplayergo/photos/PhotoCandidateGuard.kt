package com.iven.musicplayergo.photos

import android.content.Context
import android.net.Uri
import com.iven.musicplayergo.covers.CoverTarget
import java.security.MessageDigest

/** Re-enumerates only the issuing scope before preview/save; cached pixels never grant access. */
suspend fun verifyCandidate(context: Context, target: CoverTarget, request: PhotoRequest, uri: String, fingerprint: String): ByteArray {
    val repo = PhotoRepositories.get(context)
    check(targetExists(context, target)) { "目标已删除或不可用" }
    repo.validate(target, request)
    val (scope, sources) = repo.snapshot(target)
    var found = false
    for (source in sources) {
        if (PhotoCandidateProvider(context).enumerate(source, scope.excluded.toSet()).photos.any { it.uri == uri }) { found = true; break }
    }
    check(found) { "候选已移除、离线或请重新选择照片" }
    val bytes = SafePhotoDecoder(context).bytes(Uri.parse(uri))
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    check(actual == fingerprint) { "原图内容已变化，请重新推荐" }
    repo.validate(target, request)
    return bytes
}
