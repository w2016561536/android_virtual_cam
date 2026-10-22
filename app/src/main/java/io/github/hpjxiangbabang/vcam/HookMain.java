package io.github.hpjxiangbabang.vcam;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

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
    private static volatile String sSourceReady;   // 缓存"源文件可用"的判定结果
    private static volatile String sSourceKey;     // pkg|path → 缓存键
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
        sConfig = ConfigStore.loadForHook(null);

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
        VcamConfig c = ConfigStore.loadForHook(sAppContext);
        if (c != null) {
            sConfig = c;
        }
        sSourceCheckAt = 0; // 源可用性重新判定
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
        String path = effectiveSource(pkg);
        String key = pkg + "|" + path;
        long now = System.currentTimeMillis();
        if (!key.equals(sSourceKey) || now - sSourceCheckAt > CONFIG_TTL_MS) {
            sSourceCheckAt = now;
            sSourceKey = key;
            if (path.startsWith("http")) {
                sSourceReady = path;
            } else {
                File f = resolveSourceFile(pkg, path);
                sSourceReady = f != null ? f.getAbsolutePath() : null;
            }
        }
        return sSourceReady != null;
    }

    /** HTTP 源服务优先（目标 App 不需要文件权限），文件路径兜底。 */
    private static String effectiveSource(String pkg) {
        VcamConfig c = config();
        String url = c.serverUrl;
        if (url != null && url.startsWith("http")) {
            return url;
        }
        return c.sourceFor(pkg);
    }

    public static String sourcePath(String pkg) {
        return sSourceReady != null ? sSourceReady : config().sourceFor(pkg);
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