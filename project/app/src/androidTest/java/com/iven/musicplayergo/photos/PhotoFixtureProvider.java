package com.iven.musicplayergo.photos;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Public read-only test JPEGs, never packaged in the app. Java keeps the standalone
 * provider process independent of the target app's Kotlin runtime on old Android. */
public final class PhotoFixtureProvider extends ContentProvider {
    private String asset(Uri uri) {
        String path = uri.getPath();
        if (path == null || !path.matches("/photo-[a-z-]+/media/images/[a-z0-9_]+\\.jpg")) {
            throw new IllegalArgumentException("Not a public photo fixture");
        }
        return path.substring(1);
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { asset(uri); return "image/jpeg"; }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only fixtures");
        try { return getContext().getAssets().openFd(asset(uri)); }
        catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only fixtures"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read-only fixtures"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read-only fixtures"); }
}
