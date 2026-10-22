package io.github.hpjxiangbabang.vcam.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.InputStream;

import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;

/**
 * 源名 → 可读媒体 的统一解析（MediaProvider 与源服务共用）。
 *
 * path 支持四种形态：
 *   content://…（SAF 选择器 + 持久化 URI 权限，模块进程读取）
 *   /sdcard/…（普通文件）
 *   http(s)://（暂由 http 通道处理，这里不解析）
 *   rtmp://（P2.2 流源，这里不解析）
 *
 * image 源首次访问时懒生成循环 mp4 缓存。
 * 统一出口 openFd()：返回只读 ParcelFileDescriptor，喂 MediaPlayer/MediaExtractor。
 */
public final class SourceResolver {

    private SourceResolver() {
    }

    /** 名字合法性与源库解析。 */
    private static VcamConfig.SourceEntry lookup(Context context, String name) {
        if (name == null || name.length() == 0 || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            return null;
        }
        VcamConfig c = ConfigStore.loadForManager();
        return c.resolveSource(name);
    }

    /** 统一出口：打开源为只读 FD。找不到返回 null。 */
    public static ParcelFileDescriptor openFd(Context context, String name) {
        ParcelFileDescriptor pfd = null;
        VcamConfig.SourceEntry se = lookup(context, name);
        if (se != null) {
            if ("image".equals(se.type)) {
                String cache = ensureImageCache(context, name);
                if (cache != null) {
                    pfd = openFile(new File(cache));
                }
            } else if ("video".equals(se.type)) {
                pfd = openAny(context, se.path);
            }
            // rtmp：P2.2
        }
        if (pfd == null) {
            File pub = new File(ConfigStore.publicDir(), name);
            if (pub.isFile()) {
                pfd = openFile(pub);
            }
        }
        if (pfd == null) {
            VcamConfig c = ConfigStore.loadForManager();
            if (c.defaultSource != null && c.defaultSource.length() > 0) {
                pfd = openAny(context, c.defaultSource);
            }
        }
        return pfd;
    }

    /** 按路径形态打开：content URI → ContentResolver；普通文件 → 直接打开。 */
    public static ParcelFileDescriptor openAny(Context context, String path) {
        if (path == null || path.length() == 0) {
            return null;
        }
        try {
            if (path.startsWith("content:")) {
                return context.getContentResolver()
                        .openFileDescriptor(Uri.parse(path), "r");
            }
            if (path.startsWith("http") || path.startsWith("rtmp")) {
                return null; // 交给各自的通道
            }
            File f = new File(path);
            if (f.isFile()) {
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ParcelFileDescriptor openFile(File f) {
        try {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 确保 image 源的循环 mp4 缓存存在。
     * 锁内重新加载最新配置（防多请求竞态丢失 cache 字段导致反复重编码）。
     * 返回缓存路径或 null。 */
    public static String ensureImageCache(Context context, String name) {
        synchronized (SourceResolver.class) {
            try {
                VcamConfig c = ConfigStore.loadForManager();
                VcamConfig.SourceEntry se = c.resolveSource(name);
                if (se == null || !"image".equals(se.type)) {
                    return null;
                }
                if (se.cache != null && new File(se.cache).isFile()) {
                    return se.cache;
                }
                String cache = generateImageCache(context, se.path);
                if (cache != null) {
                    se.cache = cache;
                    ConfigStore.saveForManager(c);
                }
                return cache;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static String generateImageCache(Context context, String imagePath) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        decodeBounds(context, imagePath, o);
        if (o.outWidth <= 0) {
            return null;
        }
        BitmapFactory.Options b = new BitmapFactory.Options();
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1280) {
            s *= 2;
        }
        b.inSampleSize = s;
        Bitmap bmp = decode(context, imagePath, b);
        if (bmp == null) {
            return null;
        }
        File base = context.getExternalFilesDir(null);
        File dir = new File(new File(base, "Camera1"), "imgcache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File cache = new File(dir, "img_" + Integer.toHexString(imagePath.hashCode()) + ".mp4");
        File out = ImageVideoEncoder.encode(bmp, cache);
        bmp.recycle();
        return out != null ? out.getAbsolutePath() : null;
    }

    private static void decodeBounds(Context context, String path, BitmapFactory.Options o) {
        if (path != null && path.startsWith("content:")) {
            try {
                InputStream in = context.getContentResolver().openInputStream(Uri.parse(path));
                BitmapFactory.decodeStream(in, null, o);
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        } else {
            BitmapFactory.decodeFile(path, o);
        }
    }

    private static Bitmap decode(Context context, String path, BitmapFactory.Options o) {
        try {
            if (path != null && path.startsWith("content:")) {
                InputStream in = context.getContentResolver().openInputStream(Uri.parse(path));
                Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
                if (in != null) {
                    in.close();
                }
                return bmp;
            }
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }
}