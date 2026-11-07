package com.iven.musicplayergo.photos

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.covers.CoverTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun safeDecodeHandlesRealContentAndRejectsBytesTypesAndCorruption() {
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"scoped-test.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        })!!
        val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        try {
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val decoder=SafePhotoDecoder(context);val bytes=decoder.bytes(uri);val decoded=decoder.decode(bytes,100)
            assertEquals(200,decoded.width);assertEquals(100,decoded.height);decoded.recycle()
            assertThrows(IllegalArgumentException::class.java) { decoder.bytes(Uri.parse("file:///private.png")) }
            assertThrows(IllegalArgumentException::class.java) { decoder.decode(byteArrayOf(1,2,3)) }
            assertEquals(PhotoReadState.AVAILABLE,PhotoAccessManager(context).check(uri))
            assertFalse(PhotoAccessManager(context).persist(uri))
        } finally { context.contentResolver.delete(uri,null,null);bitmap.recycle() }
    }
    @Test fun atomicStorePersistsTargetIdentityAndRemovesOnlyBinding() {
        val repo=PhotoRepositories.get(context);val target=CoverTarget.Playlist(UUID.randomUUID().toString())
        val request=repo.request(target);val source=PhotoSource(UUID.randomUUID().toString(),"test",PhotoSourceKind.SELECTED,listOf("content://test/image"))
        repo.attach(target,request,source);assertEquals(listOf(source),repo.snapshot(target).second)
        repo.deleteTarget(target);assertTrue(repo.snapshot(target).second.isEmpty())
        assertThrows(PhotoScopeException::class.java) { repo.validate(target,request) }
    }
    @Test fun ungrantedSourcesCannotReuseCachedCandidates() = runBlocking {
        val result=PhotoCandidateProvider(context).enumerate(PhotoSource("test","test",PhotoSourceKind.SELECTED,listOf("content://test/image")))
        assertEquals(PhotoReadState.REAUTHORIZE,result.state);assertTrue(result.photos.isEmpty())
        val directory=PhotoCandidateProvider(context).enumerate(PhotoSource("tree","tree",PhotoSourceKind.DIRECTORY,listOf("content://test/tree/a")))
        assertEquals(PhotoReadState.REAUTHORIZE,directory.state)
    }
    @Test fun searchFallbackKeepsOrdinaryPickerIndependentOfSearchExtras() {
        val launcher=SystemPhotoSearchLauncher(context);val plain=launcher.ordinaryIntent()
        assertFalse(plain.hasExtra(SystemPhotoSearchLauncher.EXTRA_SEARCH))
        assertTrue(plain.action in listOf(MediaStore.ACTION_PICK_IMAGES,Intent.ACTION_OPEN_DOCUMENT,"androidx.activity.result.contract.action.PICK_IMAGES"))
        if (launcher.capability()!=SystemPhotoSearchCapability.MAY_REQUEST) {
            var fallback=false;launcher.launch("lakeside",{fail("unsupported must not request highlights")},{fallback=true});assertTrue(fallback)
        }
    }
    @Test fun targetBundlePreservesAlbumSnapshotsAndPlaylistId() {
        val album=CoverTarget.Album(123,"artist","album");assertEquals(album,album.toPhotoBundle().photoTarget())
        val playlist=CoverTarget.Playlist(UUID.randomUUID().toString());assertEquals(playlist,playlist.toPhotoBundle().photoTarget())
        assertFalse(targetExists(context,playlist))
    }
    @Test fun supportedRequestHasExactPublicBundleAndLaunchFailureFallsBack() {
        val launcher=SystemPhotoSearchLauncher(context) { SystemPhotoSearchCapability.MAY_REQUEST }
        val intent=launcher.intent("  lakeside  ")
        assertEquals(MediaStore.ACTION_PICK_IMAGES,intent.action)
        val bundle=intent.getBundleExtra(SystemPhotoSearchLauncher.EXTRA_SEARCH)!!
        assertEquals("lakeside",bundle.getString(SystemPhotoSearchLauncher.KEY_QUERY));assertEquals(1,bundle.getInt(SystemPhotoSearchLauncher.KEY_TYPE))
        assertFalse(intent.hasExtra("android.provider.extra.PICK_IMAGES_HIGHLIGHT_ALBUM"))
        var fallback=false;launcher.launch("lake",{throw android.content.ActivityNotFoundException()},{fallback=true});assertTrue(fallback)
        assertThrows(IllegalArgumentException::class.java){launcher.intent(" ")}
    }
    @Test fun decodeHonorsExifRotationAndMirroringInsteadOfReadingGps() {
        val file=java.io.File(context.cacheDir,"orientation-test.jpg")
        val original=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for(y in 0 until 40) for(x in 40 until 80) setPixel(x,y,Color.BLUE)
        }
        try {
            for (orientation in 1..8) {
                file.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG,100,it) }
                androidx.exifinterface.media.ExifInterface(file).apply {
                    setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,orientation.toString());saveAttributes()
                }
                val decoded=SafePhotoDecoder(context).decode(file.readBytes())
                try {
                    assertEquals(if(orientation>=5) 40 else 80,decoded.width)
                    assertEquals(if(orientation>=5) 80 else 40,decoded.height)
                    val topLeft=decoded.getPixel(4,4)
                    val expectedBlue=orientation in setOf(2,3,7,8)
                    assertEquals("Orientation $orientation",expectedBlue,Color.blue(topLeft)>Color.red(topLeft))
                } finally { decoded.recycle() }
            }
        } finally { original.recycle();file.delete() }
    }
    @Test fun candidateGuardRejectsReadableButUnboundPhotoAndRevokedRequest() = runBlocking {
        val playlists=com.iven.musicplayergo.playlists.PlaylistRepositories.get(context)
        val created=playlists.create("Guard ${UUID.randomUUID()}") as com.iven.musicplayergo.playlists.PlaylistResult.Success
        val target=CoverTarget.Playlist(created.value.id);val repo=PhotoRepositories.get(context);val request=repo.request(target)
        try {
            var rejected=false
            try { verifyCandidate(context,target,request,"content://unbound/photo","unused") } catch (_: IllegalStateException) { rejected=true }
            assertTrue(rejected)
            repo.refresh(target)
            rejected=false
            try { verifyCandidate(context,target,request,"content://unbound/photo","unused") } catch (_: PhotoScopeException) { rejected=true }
            assertTrue(rejected)
        } finally { playlists.delete(target.playlistId);repo.deleteTarget(target) }
    }
}
