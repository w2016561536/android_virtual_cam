package io.github.hpjxiangbabang.vcam.hook;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.view.Surface;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hpjxiangbabang.vcam.HookMain;
import io.github.hpjxiangbabang.vcam.engine.VideoFrameSource;

/**
 * Camera2 (android.hardware.camera2) hook，P1 重构版。
 *
 * 与旧实现的主要差别：
 * - 状态从十几个全局 static 收进 Cam2Session（onOpened 时刷新）；
 * - 对设备类/session 回调类的 hook 有去重防护（旧实现每次 onOpened 叠一层，
 *   多次开关相机会重复替换输出 Surface 列表）；
 * - reader 面的解码换 VideoFrameSource（codec 复用），preview 面保持 MediaPlayer。
 *
 * 限制（沿旧实现，P4 处理多摄并发）：同一进程同一时刻只跟踪一个活跃会话。
 */
public final class Camera2Hook {

    /** P1 限制：当前活跃会话（目标 App 一般单摄）。 */
    private static volatile Cam2Session sCurrent;

    private static final Set<String> sHookedDeviceClasses = new CopyOnWriteArraySet<>();
    private static final Set<String> sHookedCallbackClasses = new CopyOnWriteArraySet<>();
    private static final Set<String> sHookedSessionCallbacks = new CopyOnWriteArraySet<>();

    private Camera2Hook() {
    }

    public static void install(final XC_LoadPackage.LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;

        // ---- openCamera 两种签名 ----
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader,
                "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        onOpenCamera(pkg, param.args[1]);
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader,
                    "openCamera", String.class, Executor.class, CameraDevice.StateCallback.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            onOpenCamera(pkg, param.args[2]);
                        }
                    });
        }

        // ---- 输出目标替换 ----
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder",
                lpparam.classLoader, "addTarget", Surface.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.args[0] instanceof Surface) || !HookMain.enabled(pkg)
                                || !HookMain.sourceReady(pkg)) {
                            return;
                        }
                        Cam2Session s = currentOrCreate(pkg);
                        Surface orig = (Surface) param.args[0];
                        if (orig.equals(s.virtualSurface)) {
                            return;
                        }
                        String info = orig.toString();
                        if (info.contains("Surface(name=null)")) {
                            if (s.readerSurface == null) {
                                s.readerSurface = orig;
                            } else if (!s.readerSurface.equals(orig) && s.readerSurface1 == null) {
                                s.readerSurface1 = orig;
                            }
                        } else {
                            if (s.previewSurface == null) {
                                s.previewSurface = orig;
                            } else if (!s.previewSurface.equals(orig) && s.previewSurface1 == null) {
                                s.previewSurface1 = orig;
                            }
                        }
                        param.args[0] = s.virtualSurface;
                        log(pkg, "addTarget 已替换: " + info);
                    }
                });

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder",
                lpparam.classLoader, "removeTarget", Surface.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam2Session s = sCurrent;
                        if (s == null || !(param.args[0] instanceof Surface)) {
                            return;
                        }
                        Surface rm = (Surface) param.args[0];
                        if (rm.equals(s.previewSurface)) {
                            s.previewSurface = null;
                        }
                        if (rm.equals(s.previewSurface1)) {
                            s.previewSurface1 = null;
                        }
                        if (rm.equals(s.readerSurface)) {
                            s.readerSurface = null;
                        }
                        if (rm.equals(s.readerSurface1)) {
                            s.readerSurface1 = null;
                        }
                    }
                });

        // ---- build：启动解码/播放 ----
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder",
                lpparam.classLoader, "build", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!HookMain.enabled(pkg) || !HookMain.sourceReady(pkg)) {
                            return;
                        }
                        Cam2Session s = sCurrent;
                        if (s != null) {
                            s.startOutputs(pkg);
                        }
                    }
                });

        // ---- ImageReader：记录目标尺寸/格式 ----
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader,
                "newInstance", int.class, int.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam2Session s = currentOrCreate(pkg);
                        s.readerW = (int) param.args[0];
                        s.readerH = (int) param.args[1];
                        s.imageFormat = (int) param.args[2];
                        if (HookMain.enabled(pkg)) {
                            log(pkg, "ImageReader: " + s.readerW + "×" + s.readerH
                                    + " fmt=" + s.imageFormat);
                        }
                    }
                });

        // ---- 运行时控制：源热切换 / 暂停 / 声音 ----
        HookMain.addRuntimeListener(new HookMain.RuntimeListener() {
            @Override
            public void onSourceChanged(HookMain.SourceHandle h) {
                Cam2Session s = sCurrent;
                if (s != null && !HookMain.isPaused()) {
                    try {
                        s.stopOutputs();
                        s.lastPathHash = -1;
                        s.startOutputs(s.pkg);
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void onControlChanged(boolean paused, boolean videoSound) {
                Cam2Session s = sCurrent;
                if (s == null) {
                    return;
                }
                try {
                    if (paused) {
                        s.stopOutputs();
                    } else {
                        s.startOutputs(s.pkg);
                        s.applyVolume(videoSound);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static Cam2Session currentOrCreate(String pkg) {
        Cam2Session s = sCurrent;
        if (s == null) {
            s = new Cam2Session(pkg);
            sCurrent = s;
        }
        return s;
    }

    private static void onOpenCamera(final String pkg, Object stateCallback) {
        if (!(stateCallback instanceof CameraDevice.StateCallback) || !HookMain.enabled(pkg)
                || !HookMain.sourceReady(pkg)) {
            return;
        }
        final Class<?> cbClass = stateCallback.getClass();
        if (!sHookedCallbackClasses.add(cbClass.getName())) {
            return;
        }
        log(pkg, "openCamera，回调类: " + cbClass.getName());

        XposedHelpers.findAndHookMethod(cbClass, "onOpened", CameraDevice.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam2Session s = new Cam2Session(pkg);
                        s.device = (CameraDevice) param.args[0];
                        s.recreateVirtualSurface();
                        sCurrent = s;
                        HookMain.notifyCameraState(true);
                        log(pkg, "onOpened: " + s.device.getId());
                        if (sHookedDeviceClasses.add(param.args[0].getClass().getName())) {
                            installCreateSessionHooks(pkg, param.args[0].getClass());
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(cbClass, "onError", CameraDevice.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log(pkg, "camera onError: " + param.args[1]);
                    }
                });

        XposedHelpers.findAndHookMethod(cbClass, "onDisconnected", CameraDevice.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam2Session s = sCurrent;
                        if (s != null && param.args.length > 0 && param.args[0].equals(s.device)) {
                            s.stopOutputs();
                        }
                        HookMain.notifyCameraState(false);
                        log(pkg, "camera onDisconnected");
                    }
                });
    }

    private static void installCreateSessionHooks(final String pkg, final Class<?> deviceClass) {

        XC_MethodHook listHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Cam2Session s = sCurrent;
                if (s == null || !param.thisObject.equals(s.device) || param.args == null || param.args[0] == null) {
                    return;
                }
                param.args[0] = Arrays.asList(s.virtualSurface);
                log(pkg, "createCaptureSession 输出已替换");
            }
        };

        XposedHelpers.findAndHookMethod(deviceClass, "createCaptureSession", List.class,
                CameraCaptureSession.StateCallback.class, Handler.class, listHook);

        XposedHelpers.findAndHookMethod(deviceClass, "createCaptureSessionByOutputConfigurations",
                List.class, CameraCaptureSession.StateCallback.class, Handler.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam2Session s = sCurrent;
                        if (s == null || !param.thisObject.equals(s.device) || param.args[0] == null) {
                            return;
                        }
                        param.args[0] = Arrays.asList(new OutputConfiguration(s.virtualSurface));
                        log(pkg, "createCaptureSessionByOutputConfigurations 已替换");
                    }
                });

        XposedHelpers.findAndHookMethod(deviceClass, "createConstrainedHighSpeedCaptureSession",
                List.class, CameraCaptureSession.StateCallback.class, Handler.class, listHook);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            XposedHelpers.findAndHookMethod(deviceClass, "createReprocessableCaptureSession",
                    InputConfiguration.class, List.class, CameraCaptureSession.StateCallback.class,
                    Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Cam2Session s = sCurrent;
                            if (s == null || !param.thisObject.equals(s.device) || param.args[1] == null) {
                                return;
                            }
                            param.args[1] = Arrays.asList(s.virtualSurface);
                            log(pkg, "createReprocessableCaptureSession 已替换");
                        }
                    });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            XposedHelpers.findAndHookMethod(deviceClass, "createReprocessableCaptureSessionByConfigurations",
                    InputConfiguration.class, List.class, CameraCaptureSession.StateCallback.class,
                    Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Cam2Session s = sCurrent;
                            if (s == null || !param.thisObject.equals(s.device) || param.args[1] == null) {
                                return;
                            }
                            param.args[1] = Arrays.asList(new OutputConfiguration(s.virtualSurface));
                            log(pkg, "createReprocessableCaptureSessionByConfigurations 已替换");
                        }
                    });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod(deviceClass, "createCaptureSession",
                    SessionConfiguration.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Cam2Session s = sCurrent;
                            if (s == null || !param.thisObject.equals(s.device) || param.args[0] == null) {
                                return;
                            }
                            SessionConfiguration sc = (SessionConfiguration) param.args[0];
                            SessionConfiguration fake = new SessionConfiguration(
                                    sc.getSessionType(),
                                    Arrays.asList(new OutputConfiguration(s.virtualSurface)),
                                    sc.getExecutor(),
                                    sc.getStateCallback());
                            param.args[0] = fake;
                            log(pkg, "createCaptureSession(SessionConfiguration) 已替换");
                            hookSessionCallback(pkg, sc.getStateCallback());
                        }
                    });
        }
    }

    private static void hookSessionCallback(final String pkg, CameraCaptureSession.StateCallback cb) {
        if (cb == null) {
            return;
        }
        String cls = cb.getClass().getName();
        if (!sHookedSessionCallbacks.add(cls)) {
            return;
        }
        XposedHelpers.findAndHookMethod(cb.getClass(), "onConfigureFailed",
                CameraCaptureSession.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log(pkg, "onConfigureFailed: " + param.args[0]);
                    }
                });
        XposedHelpers.findAndHookMethod(cb.getClass(), "onConfigured",
                CameraCaptureSession.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log(pkg, "onConfigured");
                    }
                });
        XposedHelpers.findAndHookMethod(cb.getClass(), "onClosed",
                CameraCaptureSession.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Cam2Session s = sCurrent;
                        if (s != null) {
                            s.stopOutputs();
                        }
                        log(pkg, "onClosed");
                    }
                });
    }

    private static void log(String pkg, String msg) {
        HookMain.log(pkg, msg);
    }

    /** 一次相机打开周期内的全部状态。 */
    private static final class Cam2Session {
        final String pkg;
        CameraDevice device;
        SurfaceTexture virtualTexture;
        Surface virtualSurface;
        Surface readerSurface;
        Surface readerSurface1;
        Surface previewSurface;
        Surface previewSurface1;
        VideoFrameSource readerSrc;
        VideoFrameSource readerSrc1;
        MediaPlayer previewPlayer;
        MediaPlayer previewPlayer1;
        int readerW;
        int readerH;
        int imageFormat;
        int lastPathHash;

        Cam2Session(String pkg) {
            this.pkg = pkg;
        }

        void recreateVirtualSurface() {
            if (virtualTexture != null) {
                try {
                    virtualTexture.release();
                } catch (Throwable ignored) {
                }
                virtualTexture = null;
            }
            if (virtualSurface != null) {
                try {
                    virtualSurface.release();
                } catch (Throwable ignored) {
                }
                virtualSurface = null;
            }
            virtualTexture = new SurfaceTexture(15);
            virtualSurface = new Surface(virtualTexture);
            stopOutputs();
        }

        void startOutputs(String pkg) {
            HookMain.SourceHandle h = HookMain.sourceHandle(pkg);
            if (h == null) {
                return;
            }
            boolean videoSound = HookMain.isVideoSound();
            boolean restart = h.display.hashCode() != lastPathHash;
            if (restart) {
                stopOutputs();
                lastPathHash = h.display.hashCode();
            }
            // reader 面（ImageReader/JPEG/YUV）：codec 渲染
            if (readerSurface != null && readerSurface.isValid()) {
                if (readerSrc == null || restart) {
                    stopReader(readerSrc);
                    readerSrc = h.pfd != null
                            ? new VideoFrameSource(h.pfd, readerSurface)
                            : new VideoFrameSource(h.path, readerSurface);
                    readerSrc.setErrorListener(new VideoFrameSource.ErrorListener() {
                        @Override
                        public void onError(Throwable t, String stage) {
                            log(pkg, "reader 解码异常[" + stage + "]:" + t);
                        }
                    });
                    readerSrc.start();
                }
            }
            if (readerSurface1 != null && readerSurface1.isValid()) {
                if (readerSrc1 == null || restart) {
                    stopReader(readerSrc1);
                    readerSrc1 = h.pfd != null
                            ? new VideoFrameSource(h.pfd, readerSurface1)
                            : new VideoFrameSource(h.path, readerSurface1);
                    readerSrc1.start();
                }
            }
            // preview 面：MediaPlayer（对齐旧行为，硬件合成路径稳）
            if (previewSurface != null && previewSurface.isValid()) {
                if (previewPlayer == null || restart) {
                    stopPlayer(previewPlayer);
                    previewPlayer = h.pfd != null
                            ? newPlayerFd(h.pfd, previewSurface, videoSound)
                            : newPlayer(h.path, previewSurface, videoSound);
                }
            }
            if (previewSurface1 != null && previewSurface1.isValid()) {
                if (previewPlayer1 == null || restart) {
                    stopPlayer(previewPlayer1);
                    previewPlayer1 = h.pfd != null
                            ? newPlayerFd(h.pfd, previewSurface1, videoSound)
                            : newPlayer(h.path, previewSurface1, videoSound);
                }
            }
        }

        void applyVolume(boolean videoSound) {
            float vol = videoSound ? 1f : 0f;
            try {
                if (previewPlayer != null) {
                    previewPlayer.setVolume(vol, vol);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (previewPlayer1 != null) {
                    previewPlayer1.setVolume(vol, vol);
                }
            } catch (Throwable ignored) {
            }
        }

        private MediaPlayer newPlayerFd(ParcelFileDescriptor pfd, Surface surface, boolean videoSound) {
            if (surface == null || !surface.isValid()) {
                return null;
            }
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setSurface(surface);
                if (!videoSound) {
                    mp.setVolume(0f, 0f);
                }
                mp.setLooping(true);
                mp.setDataSource(pfd.getFileDescriptor());
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        try {
                            mediaPlayer.start();
                        } catch (Throwable ignored) {
                        }
                    }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                        log(pkg, "\u64ad\u653e\u5668\u9519\u8bef what=" + what + " extra=" + extra);
                        return true;
                    }
                });
                mp.prepareAsync();   // 非阻塞：绝不在目标 App 线程上 prepare

            } catch (Throwable t) {
                log(pkg, "preview 播放器失败(FD):" + t);
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
                return null;
            }
            return mp;
        }

        private MediaPlayer newPlayer(String path, Surface surface, boolean videoSound) {
            if (surface == null || !surface.isValid()) {
                return null;
            }
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setSurface(surface);
                if (!videoSound) {
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
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                        log(pkg, "\u64ad\u653e\u5668\u9519\u8bef what=" + what + " extra=" + extra);
                        return true;
                    }
                });
                mp.prepareAsync();   // 非阻塞：绝不在目标 App 线程上 prepare

            } catch (Throwable t) {
                log(pkg, "preview 播放器失败:" + t);
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
                return null;
            }
            return mp;
        }

        void stopOutputs() {
            stopReader(readerSrc);
            readerSrc = null;
            stopReader(readerSrc1);
            readerSrc1 = null;
            stopPlayer(previewPlayer);
            previewPlayer = null;
            stopPlayer(previewPlayer1);
            previewPlayer1 = null;
        }

        void stopReader(VideoFrameSource src) {
            if (src != null) {
                try {
                    src.stop();
                } catch (Throwable ignored) {
                }
            }
        }

        void stopPlayer(MediaPlayer mp) {
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
    }
}