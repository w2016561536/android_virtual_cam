package io.github.hpjxiangbabang.vcam.server;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import de.robv.android.xposed.XposedBridge;
import io.github.hpjxiangbabang.vcam.AppLog;
import io.github.hpjxiangbabang.vcam.ControlState;
import io.github.hpjxiangbabang.vcam.MediaProvider;
import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;

/**
 * 悬浮控制窗（P2.1c）：hook 端相机被接管时自动显示，只有 声音/上一个/暂停/下一个
 * 四键，可拖动。显隐由 hook 端经 MediaProvider"cameraState"上报。
 * 切换逻辑与主界面控制条一致：全局"当前源"（默认源+所有绑定一起切），
 * ConfigStore 保存即广播 notifyChange → hook 端 ContentObserver 即时生效。
 */
public class FloatingControlService extends Service {

    private static volatile FloatingControlService sInstance;
    private WindowManager mWm;
    private View mBar;
    private WindowManager.LayoutParams mParams;
    private Handler mMain;

    public static void onCameraState(boolean active) {
        FloatingControlService s = sInstance;
        if (s == null && active) {
            // 进程被杀后静态状态丢失：显式拉起服务，onCreate 会按 cameraActive 恢复悬浮条
            try {
                startService(new Intent(this, FloatingControlService.class));
            } catch (Throwable ignored) {
            }
            return;
        }
        if (s != null) {
            Handler h = new Handler(Looper.getMainLooper());
            h.post(() -> {
                FloatingControlService inst = sInstance;
                if (inst != null) {
                    if (active) {
                        inst.showBar();
                    } else {
                        inst.hideBar();
                    }
                }
            });
        }
    }

    public static boolean isRunning() {
        return sInstance != null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mWm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mMain = new Handler(Looper.getMainLooper());
        log("service created, cameraActive=" + ControlState.cameraActive);
        if (ControlState.cameraActive) {
            showBar();
        }
    }

    @Override
    public void onDestroy() {
        hideBar();
        sInstance = null;
        super.onDestroy();
    }

    private synchronized void showBar() {
        log("showBar enter, mBar=" + (mBar != null));
        if (mBar != null) {
            return;
        }
        try {
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(0xAA202020);
            int pad = (int) (4 * getResources().getDisplayMetrics().density);
            bar.setPadding(pad, pad, pad, pad);
            int iconPad = (int) (4 * getResources().getDisplayMetrics().density);

            ImageButton sound = new ImageButton(this);
            sound.setImageResource(android.R.drawable.ic_lock_silent_mode);
            sound.setPadding(iconPad, iconPad, iconPad, iconPad);
            sound.setBackgroundColor(0x00000000);
            sound.setOnClickListener(v -> {
                ControlState.videoSound = !ControlState.videoSound;
                sound.setImageResource(ControlState.videoSound
                        ? android.R.drawable.ic_btn_speak_now
                        : android.R.drawable.ic_lock_silent_mode);
                MediaProvider.notifyChanged(this);
            });
            bar.addView(sound, rowParams());

            ImageButton prev = new ImageButton(this);
            prev.setImageResource(android.R.drawable.ic_media_previous);
            prev.setPadding(iconPad, iconPad, iconPad, iconPad);
            prev.setBackgroundColor(0x00000000);
            prev.setOnClickListener(v -> cycleSource(-1));
            bar.addView(prev, rowParams());

            final ImageButton pause = new ImageButton(this);
            pause.setImageResource(android.R.drawable.ic_media_pause);
            pause.setPadding(iconPad, iconPad, iconPad, iconPad);
            pause.setBackgroundColor(0x00000000);
            pause.setOnClickListener(v -> {
                ControlState.paused = !ControlState.paused;
                pause.setImageResource(ControlState.paused
                        ? android.R.drawable.ic_media_play
                        : android.R.drawable.ic_media_pause);
                MediaProvider.notifyChanged(this);
            });
            bar.addView(pause, rowParams());

            ImageButton next = new ImageButton(this);
            next.setImageResource(android.R.drawable.ic_media_next);
            next.setPadding(iconPad, iconPad, iconPad, iconPad);
            next.setBackgroundColor(0x00000000);
            next.setOnClickListener(v -> cycleSource(1));
            bar.addView(next, rowParams());

            // 按住 bar 空白处拖动
            bar.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY;
                int startX, startY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mParams == null) {
                        return false;
                    }
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();
                            startX = mParams.x;
                            startY = mParams.y;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            mParams.x = startX + (int) (event.getRawX() - downX);
                            mParams.y = startY + (int) (event.getRawY() - downY);
                            try {
                                mWm.updateViewLayout(mBar, mParams);
                            } catch (Throwable ignored) {
                            }
                            return true;
                        default:
                            return false;
                    }
                }
            });

            mParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            mParams.gravity = Gravity.TOP | Gravity.START;
            mParams.x = 40;
            mParams.y = 120;

            mWm.addView(bar, mParams);
            mBar = bar;
        } catch (Throwable t) {
            log("悬浮窗创建失败: " + t);
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        int s = (int) (44 * getResources().getDisplayMetrics().density);
        return new LinearLayout.LayoutParams(s, s);
    }

    private synchronized void hideBar() {
        if (mBar != null) {
            try {
                mWm.removeView(mBar);
            } catch (Throwable ignored) {
            }
            mBar = null;
        }
    }

    /** 与主界面控制条一致的"当前源"循环切换（全局：默认源+所有绑定）。 */
    private void cycleSource(int dir) {
        try {
            VcamConfig c = ConfigStore.loadForManager();
            java.util.List<String> order = new java.util.ArrayList<>();
            order.add("virtual.mp4");
            order.addAll(c.sources.keySet());
            if (order.size() <= 1) {
                return;
            }
            String cur = c.defaultSourceName != null && c.defaultSourceName.length() > 0
                    ? c.defaultSourceName : "virtual.mp4";
            int idx = order.indexOf(cur);
            if (idx < 0) {
                idx = 0;
            }
            int ni = ((idx + dir) % order.size() + order.size()) % order.size();
            String name = order.get(ni);
            c.defaultSourceName = name.equals("virtual.mp4") ? "" : name;
            for (VcamConfig.AppEntry e : c.apps.values()) {
                e.source = c.defaultSourceName;
            }
            ConfigStore.saveForManager(c);   // 保存即广播 notifyChange → hook 即时切换
        } catch (Throwable t) {
            log("切换失败: " + t);
        }
    }

    private static void log(String msg) {
        try {
            android.util.Log.i("VCAM-FLOAT", msg);
        } catch (Throwable ignored) {
        }
        try {
            XposedBridge.log("【VCAM】【float】" + msg);
        } catch (Throwable ignored) {
        }
        AppLog.append(msg);
    }
}