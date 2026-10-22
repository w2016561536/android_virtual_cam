package io.github.hpjxiangbabang.vcam.hook;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hpjxiangbabang.vcam.HookMain;
import io.github.hpjxiangbabang.vcam.engine.FrameBuffer;
import io.github.hpjxiangbabang.vcam.engine.FrameFitter;
import io.github.hpjxiangbabang.vcam.engine.VideoFrameSource;

/**
 * Camera1 (android.hardware.Camera) hook，P1 重构版。
 *
 * 与旧实现的差别：
 * - 状态从全局 static 收进 Cam1Session（每个 Camera 实例一份，WeakReference 跟随）；
 * - onPreviewFrame 不再忙等死循环：FrameBuffer.awaitFresh 带超时，
 *   首帧最多等 1.5s，之后等不到就复用上一帧；
 * - 拍照替换用缓冲里"当前这一帧"适配目标尺寸后重编码，不再写死 1000.bmp。
 */
public final class Camera1Hook {

    private static final int FIRST_FRAME_WAIT_MS = 1500;
    private static final int FRAME_WAIT_MS = 150;
    private static final long FRAME_MAX_AGE_MS = 600;

    private static final Map<Camera, Cam1Session> sSessions =
            java.util.Collections.synchronizedMap(new WeakHashMap<Camera, Cam1Session>());

    private Camera1Hook() {
    }

    public static void install(final XC_LoadPackage.LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;

        // ---- 预览纹理：换成我们的假纹理（核心入口）----
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam1Session s = sessionFor((Camera) param.thisObject, pkg);
                        if (s.suppressNextTextureHook) {
                            // setPreviewDisplay 内部调用 setPreviewTexture，防递归（旧版 is_hooked 旗标）
                            s.suppressNextTextureHook = false;
                            return;
                        }
                        if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                            s.releasePlayers();
                            return;
                        }
                        SurfaceTexture orig = (SurfaceTexture) param.args[0];
                        if (orig == null || orig == s.fakeTexture) {
                            return;
                        }
                        if (s.camera != null && s.camera.equals(param.thisObject) && s.fakeTexture != null) {
                            param.args[0] = s.fakeTexture;
                            return;
                        }
                        s.camera = (Camera) param.thisObject;
                        s.origTexture = orig;
                        s.fakeTexture = newFakeTexture();
                        s.fakeSurface = new Surface(s.fakeTexture);
                        s.origSurface = new Surface(orig);
                        param.args[0] = s.fakeTexture;
                        log(pkg, "已接管预览纹理");
                    }
                });

        // ---- SurfaceView 预览：同样换假纹理，并接管 holder 供本地预览回显 ----
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewDisplay", SurfaceHolder.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Cam1Session s = sessionFor((Camera) param.thisObject, pkg);
                        if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                            return;
                        }
                        s.camera = (Camera) param.thisObject;
                        s.holder = (SurfaceHolder) param.args[0];
                        s.holderFakeTexture = newFakeTexture();
                        s.holderFakeSurface = new Surface(s.holderFakeTexture);
                        // 相机画面写进丢弃纹理；原 holder 面留给视频播放（旧版语义）
                        s.suppressNextTextureHook = true;
                        s.camera.setPreviewTexture(s.holderFakeTexture);
                        param.setResult(null);
                        log(pkg, "已接管 SurfaceView 预览");
                    }
                });

        // ---- startPreview：启动渲染与缓冲源 ----
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "startPreview", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam1Session s = sessionFor((Camera) param.thisObject, pkg);
                        if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                            return;
                        }
                        startSession(s, pkg);
                    }
                });

        // ---- 预览回调：喂虚拟帧 ----
        XC_MethodHook callbackHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] == null) {
                    return;
                }
                hookPreviewCallback(lpparam.classLoader, (Camera) param.thisObject, pkg, param.args[0]);
            }
        };
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, callbackHook);
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewCallback", Camera.PreviewCallback.class, callbackHook);
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setOneShotPreviewCallback", Camera.PreviewCallback.class, callbackHook);

        // ---- 回调缓冲区：换新数组，避免直接写目标 App 的原始缓冲 ----
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "addCallbackBuffer", byte[].class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args[0] != null && HookMain.enabled(pkg)) {
                            param.args[0] = new byte[((byte[]) param.args[0]).length];
                        }
                    }
                });

        // ---- 拍照：用当前虚拟帧替换 ----
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "takePicture", Camera.ShutterCallback.class, Camera.PictureCallback.class,
                Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                            return;
                        }
                        Cam1Session s = sessionFor((Camera) param.thisObject, pkg);
                        if (param.args[1] != null) {
                            hookJpegOrYuv(lpparam.classLoader, param.args[1].getClass(), s, pkg, false);
                        }
                        if (param.args[3] != null) {
                            hookJpegOrYuv(lpparam.classLoader, param.args[3].getClass(), s, pkg, true);
                        }
                    }
                });

        // ---- 录像：提示（P4 处理）----
        XposedHelpers.findAndHookMethod("android.media.MediaRecorder", lpparam.classLoader,
                "setCamera", Camera.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (HookMain.enabled(pkg)) {
                            log(pkg, "该应用触发了录像，暂不拦截（P4 处理）");
                        }
                    }
                });
    }

    private static Cam1Session sessionFor(Camera camera, String pkg) {
        synchronized (sSessions) {
            Cam1Session s = sSessions.get(camera);
            if (s == null) {
                s = new Cam1Session(pkg);
                sSessions.put(camera, s);
            }
            s.camera = camera;
            return s;
        }
    }

    private static SurfaceTexture newFakeTexture() {
        // 旧实现每次销毁重建，保持一致可避免 texture 已释放的崩溃
        return new SurfaceTexture(10);
    }

    private static void startSession(Cam1Session s, String pkg) {
        String path = HookMain.sourcePath(pkg);
        boolean mute = HookMain.muteFor(pkg);
        s.ensurePlayers(path, mute);
        // 缓冲源：等回调拿到真实目标宽高后再 setTargetSize，这里先以原尺寸起流
        if (s.bufferSource == null) {
            FrameBuffer buffer = new FrameBuffer();
            s.buffer = buffer;
            s.bufferSource = new VideoFrameSource(path, buffer);
            s.bufferSource.setErrorListener(new VideoFrameSource.ErrorListener() {
                @Override
                public void onError(Throwable t, String stage) {
                    log(s.pkg, "解码异常[" + stage + "]:" + t);
                }
            });
            s.bufferSource.start();
        }
    }

    private static void hookPreviewCallback(final ClassLoader cl, final Camera camera,
                                            final String pkg, Object callbackObj) {
        final Cam1Session s = sessionFor(camera, pkg);
        s.previewW = camera.getParameters().getPreviewSize().width;
        s.previewH = camera.getParameters().getPreviewSize().height;
        if (s.bufferSource != null) {
            s.bufferSource.setTargetSize(s.previewW, s.previewH, HookMain.fitMode(pkg));
        }
        // 微信每秒重注册回调：同类只挂一次 hook，尺寸变化静默更新，日志只在首次
        if (s.hookedCallbacks.add(callbackObj.getClass().getName())) {
            log(pkg, "预览回调接管: " + s.previewW + "×" + s.previewH);
            XposedHelpers.findAndHookMethod(callbackObj.getClass(), "onPreviewFrame",
                    byte[].class, Camera.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                                return;
                            }
                            Cam1Session st = sessionFor((Camera) param.args[1], pkg);
                            if (st.buffer == null) {
                                return;
                            }
                            FrameBuffer.Frame f = st.firstFrameDone
                                    ? st.buffer.awaitFresh(FRAME_WAIT_MS, FRAME_MAX_AGE_MS)
                                    : st.buffer.awaitFresh(FIRST_FRAME_WAIT_MS, -1);
                            st.firstFrameDone = true;
                            byte[] dst = (byte[]) param.args[0];
                            if (f == null) {
                                // 真的没有可用帧：保留上一份发过的内容，避免闪现实况
                                if (st.lastSent != null && st.lastSent.length == dst.length) {
                                    System.arraycopy(st.lastSent, 0, dst, 0, dst.length);
                                }
                                return;
                            }
                            int n = Math.min(f.data.length, dst.length);
                            System.arraycopy(f.data, 0, dst, 0, n);
                            st.lastSent = new byte[n];
                            System.arraycopy(f.data, 0, st.lastSent, 0, n);
                        }
                    });
        }
    }

    private static void hookJpegOrYuv(final ClassLoader cl, Class callbackClass,
                                      final Cam1Session s, final String pkg, final boolean jpeg) {
        XposedHelpers.findAndHookMethod(callbackClass, "onPictureTaken",
                byte[].class, Camera.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                            return;
                        }
                        try {
                            Camera cam = (Camera) param.args[1];
                            int w = cam.getParameters().getPreviewSize().width;
                            int h = cam.getParameters().getPreviewSize().height;
                            FrameBuffer.Frame f = s.buffer != null
                                    ? s.buffer.awaitFresh(1200, -1) : null;
                            if (f == null) {
                                log(pkg, "拍照：暂无虚拟帧，放行原图");
                                return;
                            }
                            byte[] frame = FrameFitter.adapt(f.data, f.width, f.height, w, h,
                                    HookMain.fitMode(pkg));
                            if (frame == null) {
                                frame = f.data;
                            }
                            if (jpeg) {
                                android.graphics.Bitmap bmp = yuvToBitmap(frame, w, h);
                                if (bmp == null) {
                                    return;
                                }
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, bos);
                                bmp.recycle();
                                param.args[0] = bos.toByteArray();
                            } else {
                                param.args[0] = frame;
                            }
                            log(pkg, "拍照已替换(" + (jpeg ? "JPEG" : "YUV") + " " + w + "×" + h + ")");
                        } catch (Throwable t) {
                            log(pkg, "拍照替换失败:" + t);
                        }
                    }
                });
    }

    private static android.graphics.Bitmap yuvToBitmap(byte[] nv21, int w, int h) {
        try {
            android.graphics.YuvImage yuv = new android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!yuv.compressToJpeg(new android.graphics.Rect(0, 0, w, h), 100, bos)) {
                return null;
            }
            byte[] j = bos.toByteArray();
            return android.graphics.BitmapFactory.decodeByteArray(j, 0, j.length);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void log(String pkg, String msg) {
        HookMain.log(pkg, msg);
    }

    /** 每个 Camera 实例一份状态。 */
    private static final class Cam1Session {
        final String pkg;
        Camera camera;
        SurfaceTexture origTexture;
        Surface origSurface;         // 视频→目标 App 原纹理
        SurfaceTexture fakeTexture;  // 相机→丢弃
        Surface fakeSurface;
        SurfaceHolder holder;
        SurfaceTexture holderFakeTexture;
        Surface holderFakeSurface;
        boolean suppressNextTextureHook;
        MediaPlayer player;          // 渲染到 fakeSurface（目标 App 的纹理预览）
        MediaPlayer holderPlayer;    // 渲染到 holderFakeSurface（SurfaceView 预览）
        VideoFrameSource bufferSource;
        FrameBuffer buffer;
        int previewW;
        int previewH;
        boolean firstFrameDone;
        byte[] lastSent;
        int lastPlayerPathHash;
        final java.util.Set<String> hookedCallbacks = new java.util.HashSet<>();

        Cam1Session(String pkg) {
            this.pkg = pkg;
        }

        void ensurePlayers(String path, boolean mute) {
            int hash = path.hashCode();
            if (player == null || lastPlayerPathHash != hash) {
                releasePlayers();
                // 关键：视频渲染到目标 App 原来的纹理面（旧版语义），不是假纹理
                origSurface = origTexture != null ? new Surface(origTexture) : null;
                player = newPlayer(path, origSurface, mute);
                holderPlayer = newPlayer(path,
                        holder != null ? holder.getSurface() : null, mute);
                lastPlayerPathHash = hash;
            }
        }

        private MediaPlayer newPlayer(String path, Surface surface, boolean mute) {
            if (surface == null || !surface.isValid()) {
                return null;
            }
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setSurface(surface);
                if (mute) {
                    mp.setVolume(0f, 0f);
                }
                mp.setLooping(true);
                mp.setDataSource(path);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        try {
                            mediaPlayer.start();
                        } catch (Throwable ignored) {
                        }
                    }
                });
                mp.prepare();
            } catch (Throwable t) {
                log(pkg, "播放器创建失败:" + t);
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
                return null;
            }
            return mp;
        }

        boolean bufferSourceAlive() {
            return bufferSource != null && buffer.latest() != null;
        }

        void releasePlayers() {
            for (MediaPlayer mp : new MediaPlayer[]{player, holderPlayer}) {
                if (mp != null) {
                    try {
                        mp.stop();
                    } catch (Throwable ignored) {
                    }
                    try {
                        mp.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
            player = null;
            holderPlayer = null;
        }
    }
}