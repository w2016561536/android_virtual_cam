package io.github.hpjxiangbabang.vcam.config;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * vcam 配置模型（P1）。
 * 以前开关散落在 DCIM/Camera1 下的若干 jpg 标记文件里，现在集中到一份 config.json。
 * 仍保持"文件即配置"的传输方式：管理端写、hook 端读，两边不共享内存。
 */
public final class VcamConfig {

    public static final int FIT_INSIDE = 0;   // 等比放入，黑边
    public static final int FIT_FILL = 1;     // 等比铺满，裁边
    public static final int FIT_STRETCH = 2;  // 拉伸铺满

    public static final int CONFIG_VERSION = 1;

    /** 全局开关 */
    public boolean disabled = false;
    public boolean showToast = true;
    public boolean playSound = false;
    public boolean forcePrivateDir = false;
    public int fitMode = FIT_INSIDE;

    /** 默认源：视频文件绝对路径（P1 只有文件视频源） */
    public String defaultSource = "";

    /** 每目标 App 配置，key = 包名；缺失字段回落全局值 */
    public final LinkedHashMap<String, AppEntry> apps = new LinkedHashMap<>();

    public static final class AppEntry {
        public boolean enabled = true;
        public String source = null;     // null => 用 defaultSource
        public Boolean mute = null;      // null => 用全局 playSound 取反
        public Integer fitMode = null;   // null => 用全局 fitMode
    }

    public AppEntry app(String packageName) {
        AppEntry e = apps.get(packageName);
        return e != null ? e : new AppEntry();
    }

    /** hook 端取有效值 */
    public boolean enabledFor(String packageName) {
        return !disabled && app(packageName).enabled;
    }

    public String sourceFor(String packageName) {
        AppEntry e = app(packageName);
        if (e.source != null && e.source.length() > 0) {
            return e.source;
        }
        return defaultSource != null ? defaultSource : "";
    }

    public boolean muteFor(String packageName) {
        Boolean m = app(packageName).mute;
        if (m != null) {
            return m;
        }
        return !playSound;
    }

    public int fitModeFor(String packageName) {
        Integer f = app(packageName).fitMode;
        return f != null ? f : fitMode;
    }

    // ---------- 序列化 ----------

    public JSONObject toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", CONFIG_VERSION);
            JSONObject g = new JSONObject();
            g.put("disabled", disabled);
            g.put("show_toast", showToast);
            g.put("play_sound", playSound);
            g.put("force_private_dir", forcePrivateDir);
            g.put("fit_mode", fitMode);
            root.put("global", g);
            root.put("default_source", defaultSource == null ? "" : defaultSource);
            JSONObject a = new JSONObject();
            for (Map.Entry<String, AppEntry> en : apps.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("enabled", en.getValue().enabled);
                if (en.getValue().source != null) {
                    o.put("source", en.getValue().source);
                }
                if (en.getValue().mute != null) {
                    o.put("mute", en.getValue().mute);
                }
                if (en.getValue().fitMode != null) {
                    o.put("fit_mode", en.getValue().fitMode);
                }
                a.put(en.getKey(), o);
            }
            root.put("apps", a);
            return root;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static VcamConfig fromJson(JSONObject root) {
        VcamConfig c = new VcamConfig();
        if (root == null) {
            return c;
        }
        try {
            JSONObject g = root.optJSONObject("global");
            if (g != null) {
                c.disabled = g.optBoolean("disabled", false);
                c.showToast = g.optBoolean("show_toast", true);
                c.playSound = g.optBoolean("play_sound", false);
                c.forcePrivateDir = g.optBoolean("force_private_dir", false);
                c.fitMode = g.optInt("fit_mode", FIT_INSIDE);
            }
            c.defaultSource = root.optString("default_source", "");
            JSONObject a = root.optJSONObject("apps");
            if (a != null) {
                Iterator<String> keys = a.keys();
                while (keys.hasNext()) {
                    String pkg = keys.next();
                    JSONObject o = a.optJSONObject(pkg);
                    if (o == null) {
                        continue;
                    }
                    AppEntry e = new AppEntry();
                    e.enabled = o.optBoolean("enabled", true);
                    e.source = o.has("source") ? o.optString("source", null) : null;
                    e.mute = o.has("mute") ? Boolean.valueOf(o.optBoolean("mute")) : null;
                    e.fitMode = o.has("fit_mode") ? Integer.valueOf(o.optInt("fit_mode")) : null;
                    c.apps.put(pkg, e);
                }
            }
        } catch (Exception ignored) {
        }
        return c;
    }
}