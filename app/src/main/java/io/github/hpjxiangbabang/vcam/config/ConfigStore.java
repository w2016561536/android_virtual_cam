package io.github.hpjxiangbabang.vcam.config;

import android.content.Context;
import android.os.Environment;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * config.json 的读写与路径解析。
 *
 * 管理端（本 App，targetSdk 26 + 旧版存储）直接写 /storage/emulated/0/DCIM/Camera1/config.json；
 * hook 端跑在目标 App 进程里，可能没有存储权限，此时回落到目标 App 自己的
 * external files dir/Camera1/config.json。两端各取自己能读到的第一份。
 */
public final class ConfigStore {

    public static final String CONFIG_NAME = "config.json";
    public static final String DIR_PUBLIC = "DCIM/Camera1";

    private ConfigStore() {
    }

    public static File publicDir() {
        return new File(Environment.getExternalStorageDirectory(), DIR_PUBLIC);
    }

    public static File publicConfigFile() {
        return new File(publicDir(), CONFIG_NAME);
    }

    public static File appConfigFile(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        return new File(new File(base, "Camera1"), CONFIG_NAME);
    }

    /** hook 端调用：第一个能读到的配置生效。 */
    public static VcamConfig loadForHook(Context context) {
        File[] candidates = new File[] {
                publicConfigFile(),
                context != null ? appConfigFile(context) : null,
        };
        for (File f : candidates) {
            VcamConfig c = readFile(f);
            if (c != null) {
                return c;
            }
        }
        return migrateLegacy(publicDir());
    }

    /** 管理端调用：只认公共目录那份。 */
    public static VcamConfig loadForManager() {
        VcamConfig c = readFile(publicConfigFile());
        if (c != null) {
            return c;
        }
        return migrateLegacy(publicDir());
    }

    public static boolean saveForManager(VcamConfig config) {
        return writeFile(publicConfigFile(), config);
    }

    /** 供无存储权限的目标 App 使用：把配置落到它自己的目录。 */
    public static boolean saveForApp(Context context, VcamConfig config) {
        if (context == null) {
            return false;
        }
        File f = appConfigFile(context);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        return writeFile(f, config);
    }

    /**
     * 旧版 jpg 标记迁移：config.json 不存在时，把旧开关转成配置，
     * 并把 legacy 视频路径作为默认源。写回一份 config.json 后旧文件就不再被读取。
     */
    public static VcamConfig migrateLegacy(File dir) {
        VcamConfig c = new VcamConfig();
        File legacyVideo = new File(dir, "virtual.mp4");
        c.defaultSource = legacyVideo.exists() ? legacyVideo.getAbsolutePath() : legacyVideo.getAbsolutePath();
        if (flagExists(dir, "disable.jpg")) {
            c.disabled = true;
        }
        if (flagExists(dir, "no_toast.jpg")) {
            c.showToast = false;
        }
        if (flagExists(dir, "no-silent.jpg")) {
            c.playSound = true;
        }
        if (flagExists(dir, "private_dir.jpg")) {
            c.forcePrivateDir = true;
        }
        c.fitMode = flagExists(dir, "fit_inside.jpg") ? VcamConfig.FIT_INSIDE : VcamConfig.FIT_INSIDE;
        return c;
    }

    private static boolean flagExists(File dir, String name) {
        File f = new File(dir, name);
        return f.exists() && f.isFile();
    }

    private static VcamConfig readFile(File f) {
        if (f == null || !f.exists() || !f.isFile()) {
            return null;
        }
        try {
            Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
            StringBuilder b = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) != -1) {
                b.append(buf, 0, n);
            }
            r.close();
            JSONObject root = new JSONObject(b.toString());
            return VcamConfig.fromJson(root);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean writeFile(File f, VcamConfig config) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp, false), StandardCharsets.UTF_8);
            w.write(config.toJson().toString(2));
            w.flush();
            w.close();
            // 原子替换：hook 端读一半的配置比旧配置更糟
            File old = new File(f.getParentFile(), f.getName() + ".bak");
            if (f.exists()) {
                old.delete();
                f.renameTo(old);
            }
            if (!tmp.renameTo(f)) {
                // 极端情况回滚
                if (old.exists()) {
                    old.renameTo(f);
                }
                return false;
            }
            old.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}