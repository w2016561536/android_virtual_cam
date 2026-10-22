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
    public static final int FIT_NONE = 3;     // 不适配，按视频原样输出

    public static final int CONFIG_VERSION = 1;

    /** 全局开关 */
    public boolean disabled = false;
    public boolean showToast = true;
    public boolean playSound = false;
    public boolean forcePrivateDir = false;
    public int fitMode = FIT_INSIDE;

    /** 默认源：视频文件绝对路径（P1 只有文件视频源） */
    public String defaultSource = "";

    /** 本地源服务地址（模块进程 HTTP 服务），非空时 hook 端优先使用 */
    public String serverUrl = "";

    /** 全局默认源名（源库里的 key）；空 = 传统 virtual.mp4 */
    public String defaultSourceName = "";

    /** 每目标 App 配置，key = 包名；缺失字段回落全局值 */
    public final LinkedHashMap<String, AppEntry> apps = new LinkedHashMap<>();

    /** 源库，key = 源名；P2.1 支持 video/image（rtmp 预留 P2.2） */
    public final LinkedHashMap<String, SourceEntry> sources = new LinkedHashMap<>();

    public static final class SourceEntry {
        public String type = "video";   // video | image | rtmp
        public String path = "";        // video/rtmp: 路径或 URL；image: 原图路径
        public String cache = null;     // image: 由模块预生成的循环 mp4（目标 App 经源服务取）
    }

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

    /** 按源名解析出"实际媒体位置"（路径或缓存 mp4）。未知名返回 null。 */
    public SourceEntry resolveSource(String name) {
        if (name == null || name.length() == 0) {
            return null;
        }
        return sources.get(name);
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
            root.put("server_url", serverUrl == null ? "" : serverUrl);
            root.put("default_source_name", defaultSourceName == null ? "" : defaultSourceName);
            JSONObject s = new JSONObject();
            for (Map.Entry<String, SourceEntry> en : sources.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("type", en.getValue().type);
                o.put("path", en.getValue().path);
                if (en.getValue().cache != null) {
                    o.put("cache", en.getValue().cache);
                }
                s.put(en.getKey(), o);
            }
            root.put("sources", s);
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
            c.serverUrl = root.optString("server_url", "");
            c.defaultSourceName = root.optString("default_source_name", "");
            JSONObject s = root.optJSONObject("sources");
            if (s != null) {
                Iterator<String> sk = s.keys();
                while (sk.hasNext()) {
                    String name = sk.next();
                    JSONObject o = s.optJSONObject(name);
                    if (o == null) {
                        continue;
                    }
                    SourceEntry e = new SourceEntry();
                    e.type = o.optString("type", "video");
                    e.path = o.optString("path", "");
                    e.cache = o.has("cache") ? o.optString("cache", null) : null;
                    c.sources.put(name, e);
                }
            }
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