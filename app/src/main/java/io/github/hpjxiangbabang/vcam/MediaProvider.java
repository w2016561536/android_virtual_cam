package io.github.hpjxiangbabang.vcam;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;

import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.server.SourceResolver;

/**
 * 配置与媒体文件的跨进程通道（P2）。
 *
 * 作用域存储下目标 App 读不了 /sdcard/DCIM/config.json，且多数 App 的
 * 网络安全配置拦截明文 http（连 127.0.0.1 也拦）。Provider 走 binder，
 * 两边都不受限——这是 MyCam（MochiMediaProvider）同款方案。
 *
 * - call("getConfig") → 配置 JSON（模块进程常驻时可用；前台服务保证存活）
 * - openFile(content://…/v/<源名>) → 该源媒体的只读 FD，喂 MediaPlayer/MediaExtractor
 */
public class MediaProvider extends ContentProvider {

    public static final String AUTHORITY = "io.github.hpjxiangbabang.vcam.media";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("getConfig".equals(method)) {
            try {
                File f = ConfigStore.publicConfigFile();
                String json = "{}";
                if (f.isFile()) {
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    in.close();
                    json = bos.toString("UTF-8");
                }
                Bundle b = new Bundle();
                b.putString("json", json);
                return b;
            } catch (Throwable t) {
                Log.w("VCAM-MEDIA", "getConfig: " + t);
                return null;
            }
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        java.util.List<String> segs = uri.getPathSegments();
        String name = segs != null && !segs.isEmpty() ? segs.get(segs.size() - 1) : "";
        ParcelFileDescriptor pfd = SourceResolver.openFd(getContext(), name);
        if (pfd == null) {
            throw new java.io.FileNotFoundException("no source: " + name);
        }
        return pfd;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "video/mp4";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}