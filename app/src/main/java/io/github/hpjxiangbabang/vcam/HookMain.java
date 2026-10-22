package io.github.hpjxiangbabang.vcam;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;
import io.github.hpjxiangbabang.vcam.hook.Camera1Hook;
import io.github.hpjxiangbabang.vcam.hook.Camera2Hook;

/**
 * 模块入口（P1 瘦身）。
 *
 * 旧版把全部 hook 与状态塞在这一个类里（~1200 行、40+ static 字段）。
 * 现在只做四件事：装配 hook、维护配置缓存（2s 热刷新）、
 * 在目标 App onCreate 后拿到 Context、统一日志/提示出口。
 */
public class HookMain implements IXposedHookLoadPackage {

    private static final long CONFIG_TTL_MS = 2000;

    private static volatile VcamConfig sConfig;
    private static volatile long sConfigAt;
    private static volatile SourceHandle sSourceHandle;  // 当前源句柄（path 或 provider FD）
    private static volatile String sSourceKey;     // pkg|name → 缓存键
    private static volatile long sSourceCheckAt;
    private static volatile Context sAppContext;
    private static volatile String sPkg;
    private static volatile boolean sForcePrivateDir;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            return;
        }
        sPkg = lpparam.packageName;
        sConfig = providerConfig();
        if (sConfig == null) {
            sConfig = fetchHttpConfig();
        }
        if (sConfig == null) {
            sConfig = ConfigStore.loadForHook(null);
        }

        // 目标 App 起来后拿 Context：配置回落目录 + 权限判定
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader,
                "callApplicationOnCreate", Application.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!(param.args[0] instanceof Application)) {
                            return;
                        }
                        try {
                            sAppContext = ((Application) param.args[0]).getApplicationContext();
                            sForcePrivateDir = resolveForcePrivateDir(lpparam.packageName);
                            refreshConfig(true);
                        } catch (Throwable t) {
                            XposedBridge.log("【VCAM】onCreate:" + t);
                        }
                    }
                });

        Camera1Hook.install(lpparam);
        Camera2Hook.install(lpparam);
    }

    // ---------------- 配置访问（hook 层统一走这里） ----------------

    private static void refreshConfig(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - sConfigAt < CONFIG_TTL_MS) {
            return;
        }
        sConfigAt = now;
        // 通道优先级：Provider(binder，不受存储权限/明文策略限制) → 本地 http → 文件
        VcamConfig c = providerConfig();
        if (c == null) {
            c = fetchHttpConfig();
        }
        if (c == null) {
            c = ConfigStore.loadForHook(sAppContext);
        }
        if (c != null) {
            sConfig = c;
        }
        sSourceCheckAt = 0; // 源可用性重新判定
    }

    /** content://io.github.hpjxiangbabang.vcam.media → getConfig */
    private static VcamConfig providerConfig() {
        Context ctx = sAppContext;
        if (ctx == null) {
            return null;
        }
        try {
            Bundle b = ctx.getContentResolver().call(MediaProvider.BASE_URI, "getConfig", null, null);
            String json = b != null ? b.getString("json") : null;
            if (json != null && json.length() > 2) {
                return VcamConfig.fromJson(new org.json.JSONObject(json));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** http://127.0.0.1:37199/config —— 300ms 超时，失败返回 null（回环连接拒绝几乎是即时的）。 */
    private static VcamConfig fetchHttpConfig() {
        try {
            java.net.URL u = new java.net.URL(ConfigStore.SERVER_URL + "/config");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(300);
            conn.setReadTimeout(500);
            java.io.InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            in.close();
            conn.disconnect();
            return VcamConfig.fromJson(new org.json.JSONObject(bos.toString("UTF-8")));
        } catch (Throwable t) {
            return null;
        }
    }

    static VcamConfig config() {
        refreshConfig(false);
        VcamConfig c = sConfig;
        return c != null ? c : new VcamConfig();
    }

    public static boolean enabled(String pkg) {
        return config().enabledFor(pkg);
    }

    public static boolean sourceReady(String pkg) {
        String name = sourceName(pkg);
        String key = pkg + "|" + name;
        long now = System.currentTimeMillis();
        if (!key.equals(sSourceKey) || now - sSourceCheckAt > CONFIG_TTL_MS) {
            sSourceCheckAt = now;
            sSourceKey = key;
            sSourceHandle = openSource(pkg, name);
        }
        return sSourceHandle != null;
    }

    /** sourceReady 后取缓存的源句柄。 */
    public static SourceHandle sourceHandle(String pkg) {
        return sSourceHandle;
    }

    /** 源名解析：应用绑定 > 全局默认源选择 > 传统 virtual.mp4。 */
    public static String sourceName(String pkg) {
        VcamConfig c = config();
        String n = c.app(pkg).source;
        if (n == null || n.length() == 0) {
            n = c.defaultSourceName;
        }
        if (n == null || n.length() == 0) {
            n = "virtual.mp4";
        }
        return n;
    }

    /**
     * 打开源（按通道优先级）：
     * 1. MediaProvider FD（binder，不受存储权限/明文策略限制）—— 首选
     * 2. 可直读的文件路径（模块自身 / 旧版存储视图的进程）
     * 3. 明文 http 允许的 App → 本地源服务 URL
     */
    private static SourceHandle openSource(String pkg, String name) {
        Context ctx = sAppContext;
        if (ctx != null) {
            try {
                Uri uri = Uri.parse(MediaProvider.BASE_URI + "/v/" + android.net.Uri.encode(name));
                ParcelFileDescriptor pfd = ctx.getContentResolver().openFile(uri, "r", null);
                if (pfd != null) {
                    return new SourceHandle(null, pfd, "provider:" + name);
                }
                log(pkg, "provider 返回空 FD: " + name);
            } catch (Throwable t) {
                log(pkg, "provider 打开源失败[" + name + "]: " + t);
            }
        }
        String path = effectiveSource(pkg);
        if (path != null && path.startsWith("http")) {
            return new SourceHandle(path, null, path);
        }
        if (path != null) {
            File f = resolveSourceFile(pkg, path);
            if (f != null) {
                return new SourceHandle(f.getAbsolutePath(), null, f.getAbsolutePath());
            }
            log(pkg, "文件源不可读: " + path);
        }
        log(pkg, "所有源通道均失败: " + name);
        return null;
    }

    /** 源句柄：path（http/文件）或 pfd（provider FD），二选一。 */
    public static final class SourceHandle {
        public final String path;
        public final ParcelFileDescriptor pfd;
        public final String display;

        SourceHandle(String path, ParcelFileDescriptor pfd, String display) {
            this.path = path;
            this.pfd = pfd;
            this.display = display;
        }
    }

    /** 优先级：应用显式绑定 > 全局默认源选择 > 传统 virtual.mp4。
     * 命名源走 /v/<源名>，传统默认走 /v。 */
    private static String effectiveSource(String pkg) {
        VcamConfig c = config();
        String url = c.serverUrl != null && c.serverUrl.startsWith("http")
                ? c.serverUrl : ConfigStore.SERVER_URL + "/v";

        // 1) 应用显式绑定
        String named = c.app(pkg).source;
        String via = pick(named, c, url);
        if (via != null) {
            return via;
        }
        // 2) 全局默认源选择
        via = pick(c.defaultSourceName, c, url);
        if (via != null) {
            return via;
        }
        // 3) 传统默认（服务可用则 /v，否则文件路径）
        if (url.startsWith("http")) {
            return c.serverUrl != null && c.serverUrl.startsWith("http") ? c.serverUrl : url;
        }
        return c.sourceFor(pkg);
    }

    private static String pick(String name, VcamConfig c, String url) {
        if (name == null || name.length() == 0) {
            return null;
        }
        VcamConfig.SourceEntry se = c.resolveSource(name);
        if (se == null) {
            return null;
        }
        if ("rtmp".equals(se.type)) {
            return se.path;   // P2.2 由流源处理
        }
        try {
            return url + "/" + java.net.URLEncoder.encode(name, "UTF-8");
        } catch (Throwable t) {
            return url;
        }
    }

    /** 兼容旧调用：源句柄的文件/http 路径（FD 通道时返回 null）。 */
    public static String sourcePath(String pkg) {
        SourceHandle h = sourceHandle(pkg);
        return h != null ? h.path : null;
    }

    public static boolean muteFor(String pkg) {
        return config().muteFor(pkg);
    }

    public static int fitMode(String pkg) {
        return config().fitModeFor(pkg);
    }

    public static boolean showToast() {
        return config().showToast;
    }

    /**
     * 源文件解析：
     * 1. 配置里的路径直接可用 → 用；
     * 2. 不可用（目标 App 无存储权限）→ 回落目标 App 自己 external files/Camera1/ 下同名文件；
     * 3. 还不行 → 返回原路径（让引擎报"视频不存在"的明确错误）。
     */
    private static File resolveSourceFile(String pkg, String path) {
        if (path == null || path.length() == 0) {
            return null;
        }
        File f = new File(path);
        if (isReallyReadable(f) && f.length() > 1024) {
            return f;
        }
        // 作用域存储下 exists() 可能为真但读不了（EACCES），必须真读探测
        Context ctx = sAppContext;
        if (ctx != null) {
            File base = ctx.getExternalFilesDir(null);
            if (base != null) {
                File alt = new File(new File(base, "Camera1"), f.getName());
                if (isReallyReadable(alt) && alt.length() > 1024) {
                    return alt;
                }
            }
        }
        return isReallyReadable(f) ? f : null;
    }

    /** 真读 1 字节确认可读（exists/canRead 在 FUSE 下会说谎）。 */
    private static boolean isReallyReadable(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                in.read();
                return true;
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** 旧版"强制私有目录"：目标 App 没有存储权限或开关打开时提示重定向。 */
    private static boolean resolveForcePrivateDir(String pkg) {
        VcamConfig c = config();
        if (c.forcePrivateDir) {
            return true;
        }
        Context ctx = sAppContext;
        if (ctx == null) {
            return false;
        }
        try {
            int granted = ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (ctx.checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // 目标 App 在 Android 11+ 没有"所有文件"权限，公共目录多半读不到
                    File pub = new File(Environment.getExternalStorageDirectory(), "DCIM/Camera1/virtual.mp4");
                    if (!pub.canRead()) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ---------------- 日志与提示 ----------------

    public static void log(String pkg, String msg) {
        try {
            XposedBridge.log("【VCAM】[" + pkg + "] " + msg);
        } catch (Throwable ignored) {
        }
        Context ctx = sAppContext;
        if (ctx != null) {
            AppLog.appendFromHook(ctx, pkg + "\n" + msg);
            if (showToast()) {
                try {
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}