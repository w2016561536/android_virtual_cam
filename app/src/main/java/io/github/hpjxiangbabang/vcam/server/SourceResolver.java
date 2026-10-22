package io.github.hpjxiangbabang.vcam.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;

import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;

/**
 * 源名 → 实际文件 的统一解析（服务端与 MediaProvider 共用）。
 * image 源在首次访问时懒生成循环 mp4 缓存。
 */
public final class SourceResolver {

    private SourceResolver() {
    }

    public static File resolve(Context context, String name) {
        if (name == null || name.length() == 0 || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            return null;
        }
        VcamConfig c = ConfigStore.loadForManager();
        VcamConfig.SourceEntry se = c.resolveSource(name);
        File file = null;
        if (se != null) {
            if ("image".equals(se.type)) {
                if (se.cache == null || !(new File(se.cache).isFile())) {
                    se.cache = generateImageCache(context, se.path);
                    if (se.cache != null) {
                        c.sources.put(name, se);
                        ConfigStore.saveForManager(c);
                    }
                }
                if (se.cache != null) {
                    file = new File(se.cache);
                }
            } else if (!"rtmp".equals(se.type)) {
                file = new File(se.path);
            }
        }
        if (file == null) {
            File pub = new File(ConfigStore.publicDir(), name);
            if (pub.isFile()) {
                file = pub;
            }
        }
        if ((file == null || !file.isFile()) && c.defaultSource != null && c.defaultSource.length() > 0) {
            File ds = new File(c.defaultSource);
            if (ds.isFile()) {
                file = ds;
            }
        }
        return file != null && file.isFile() ? file : null;
    }

    private static String generateImageCache(Context context, String imagePath) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, o);
            if (o.outWidth <= 0) {
                return null;
            }
            BitmapFactory.Options b = new BitmapFactory.Options();
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1280) {
                s *= 2;
            }
            b.inSampleSize = s;
            Bitmap bmp = BitmapFactory.decodeFile(imagePath, b);
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
        } catch (Throwable t) {
            return null;
        }
    }
}