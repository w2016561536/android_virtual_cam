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
 * 悬浮控制窗（P2.1c，双态）：
 * - 平时只有一个悬浮小图标（相机），可拖动；
 * - 点小图标 → 展开为控制条：[收起] [声音] [上一个] [暂停] [下一个]；
 * - 点收起 → 缩回小图标；
 * - 相机被 hook 接管时自动出现，相机关闭自动消失。
 * 切换逻辑与主界面控制条一致：全局"当前源"（默认源+所有绑定一起切），
 * ConfigStore 保存即广播 notifyChange → hook 端 ContentObserver 即时生效。
 */
public class FloatingControlService extends Service {

    private static volatile FloatingControlService sInstance;
    private WindowManager mWm;
    private View mCurrent;                        // 当前挂在窗口上的视图（小图标或展开条）
    private WindowManager.LayoutParams mParams;
    private boolean mExpanded = false;
    private Handler mMain;

    public static boolean isRunning() {
        return sInstance != null;
    }

    public static void onCameraState(android.content.Context ctx, boolean active) {
        FloatingControlService s = sInstance;
        if (s == null && active) {
            // 进程被杀后静态状态丢失：显式拉起服务，onCreate 会按 cameraActive 恢复悬浮条
            try {
                if (ctx != null) {
                    ctx.startService(new Intent(ctx, FloatingControlService.class));
                }
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
                        inst.show();
                    } else {
                        inst.hide();
                    }
                }
            });
        }
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
        if (ControlState.cameraActive) {
            show();
        }
    }

    @Override
    public void onDestroy() {
        hide();
        sInstance = null;
        super.onDestroy();
    }

    // ---------------- 显隐 ----------------

    private synchronized void show() {
        if (mCurrent != null) {
            return;
        }
        if (mExpanded) {
            mCurrent = buildExpanded();
        } else {
            mCurrent = buildCollapsed();
        }
        try {
            mParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            mParams.gravity = Gravity.TOP | Gravity.START;
            mParams.x = 40;
            mParams.y = 120;
            mWm.addView(mCurrent, mParams);
        } catch (Throwable t) {
            log("悬浮窗创建失败: " + t);
            mCurrent = null;
        }
    }

    private synchronized void hide() {
        if (mCurrent != null) {
            try {
                mWm.removeView(mCurrent);
            } catch (Throwable ignored) {
            }
            mCurrent = null;
        }
    }

    /** 在小图标与展开条之间切换（保持位置）。 */
    private synchronized void swap(boolean expand) {
        if (mCurrent == null) {
            return;
        }
        int x = mParams != null ? mParams.x : 40;
        int y = mParams != null ? mParams.y : 120;
        try {
            mWm.removeView(mCurrent);
        } catch (Throwable ignored) {
        }
        mExpanded = expand;
        mCurrent = expand ? buildExpanded() : buildCollapsed();
        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = x;
        mParams.y = y;
        try {
            mWm.addView(mCurrent, mParams);
        } catch (Throwable t) {
            log("悬浮窗切换失败: " + t);
            mCurrent = null;
        }
    }

    // ---------------- 视图构建 ----------------

    private int iconPad() {
        return (int) (4 * getResources().getDisplayMetrics().density);
    }

    private int iconSize() {
        return (int) (44 * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams iconParams() {
        return new LinearLayout.LayoutParams(iconSize(), iconSize());
    }

    /** 收起态：单个相机小图标，点击展开，按住可拖动。 */
    private View buildCollapsed() {
        ImageButton icon = new ImageButton(this);
        icon.setImageResource(android.R.drawable.ic_menu_camera);
        icon.setPadding(iconPad(), iconPad(), iconPad(), iconPad());
        icon.setBackgroundColor(0xAA202020);
        // 点击与拖动统一在 touch 里处理（onTouch 返回 true 会吞掉 click）
        attachDrag(icon, () -> swap(true));
        return icon;
    }

    /** 展开态：[收起] [声音] [上一个] [暂停] [下一个]。 */
    private View buildExpanded() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xAA202020);
        bar.setPadding(iconPad(), iconPad(), iconPad(), iconPad());

        ImageButton collapse = new ImageButton(this);
        collapse.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        collapse.setPadding(iconPad(), iconPad(), iconPad(), iconPad());
        collapse.setBackgroundColor(0x00000000);
        collapse.setOnClickListener(v -> swap(false));
        bar.addView(collapse, iconParams());

        ImageButton sound = new ImageButton(this);
        sound.setImageResource(ControlState.videoSound
                ? android.R.drawable.ic_btn_speak_now
                : android.R.drawable.ic_lock_silent_mode);
        sound.setPadding(iconPad(), iconPad(), iconPad(), iconPad());
        sound.setBackgroundColor(0x00000000);
        sound.setOnClickListener(v -> {
            ControlState.videoSound = !ControlState.videoSound;
            sound.setImageResource(ControlState.videoSound
                    ? android.R.drawable.ic_btn_speak_now
                    : android.R.drawable.ic_lock_silent_mode);
            MediaProvider.notifyChanged(this);
        });
        bar.addView(sound, iconParams());

        ImageButton prev = new ImageButton(this);
        prev.setImageResource(android.R.drawable.ic_media_previous);
        prev.setPadding(iconPad(), iconPad(), iconPad(), iconPad());
        prev.setBackgroundColor(0x00000000);
        prev.setOnClickListener(v -> cycleSource(-1));
        bar.addView(prev, iconParams());

        final ImageButton pause = new ImageButton(this);
        pause.setImageResource(ControlState.paused
                ? android.R.drawable.ic_media_play
                : android.R.drawable.ic_media_pause);
        pause.setPadding(iconPad(), iconPad(), iconPad(), iconPad());
        pause.setBackgroundColor(0x00000000);
        pause.setOnClickListener(v -> {
            ControlState.paused = !ControlState.paused;
            pause.setImageResource(ControlState.paused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
            MediaProvider.notifyChanged(this);
        });
        bar.addView(pause, iconParams());

        ImageButton next = new ImageButton(this);
        next.setImageResource(android.R.drawable.ic_media_next);
        next.setPadding(iconPad(), iconPad(), iconPad(), iconPad());
        next.setBackgroundColor(0x00000000);
        next.setOnClickListener(v -> cycleSource(1));
        bar.addView(next, iconParams());

        attachDrag(bar, null);
        return bar;
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