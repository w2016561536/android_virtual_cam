package io.github.hpjxiangbabang.vcam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.hpjxiangbabang.vcam.ControlState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;
import io.github.hpjxiangbabang.vcam.engine.FrameFitter;
import io.github.hpjxiangbabang.vcam.server.ImageVideoEncoder;

/**
 * 管理端（P1）：开关全部落到 config.json，不再创建 jpg 标记文件。
 * force_show 一个例外保留旧文件——它只影响"权限提示是否重新弹一次"的旧行为。
 */
public class MainActivity extends Activity {

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;
    private Switch fit_inside_switch;
    private TextView log_view;
    private TextView currentSourceView;
    private android.widget.ImageButton btnSound;
    private android.widget.ImageButton btnPause;
    private LinearLayout settingsBox;
    private VcamConfig config;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show();
        } else {
            File cameraDir = new File(Environment.getExternalStorageDirectory(), "DCIM/Camera1");
            if (!cameraDir.exists()) {
                cameraDir.mkdirs();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfig();
        show_log();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        force_show_switch = findViewById(R.id.switch1);
        disable_switch = findViewById(R.id.switch2);
        play_sound_switch = findViewById(R.id.switch3);
        force_private_dir = findViewById(R.id.switch4);
        disable_toast_switch = findViewById(R.id.switch5);
        fit_inside_switch = findViewById(R.id.switch6);
        log_view = findViewById(R.id.log_view);

        findViewById(R.id.log_refresh).setOnClickListener(v -> show_log());
        findViewById(R.id.log_clear).setOnClickListener(v -> {
            if (!has_permission()) {
                request_permission();
                return;
            }
            AppLog.clear();
            show_log();
        });

        // P2.1 源库（SAF 文件选择器，MIME 预设过滤）
        findViewById(R.id.btn_add_video).setOnClickListener(v -> pickSource("video"));
        findViewById(R.id.btn_add_image).setOnClickListener(v -> pickSource("image"));
        // 控制条（播放器形式：声音/上一个/暂停/下一个/列表，文字标签保证渲染）
        btnSound = findViewById(R.id.btn_sound);
        btnPause = findViewById(R.id.btn_pause);
        currentSourceView = findViewById(R.id.current_source);
        findViewById(R.id.btn_sound).setOnClickListener(v -> {
            ControlState.videoSound = !ControlState.videoSound;
            refreshControlIcons();
            Toast.makeText(this, ControlState.videoSound ? "视频声音" : "麦克风（静音）", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_pause).setOnClickListener(v -> {
            ControlState.paused = !ControlState.paused;
            refreshControlIcons();
        });
        findViewById(R.id.btn_prev).setOnClickListener(v -> cycleSource(-1));
        findViewById(R.id.btn_next).setOnClickListener(v -> cycleSource(1));
        findViewById(R.id.btn_list).setOnClickListener(v -> defaultSourceDialog());

        // 二级入口：设置与日志
        settingsBox = findViewById(R.id.settings_box);
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            boolean show = settingsBox.getVisibility() != View.VISIBLE;
            settingsBox.setVisibility(show ? View.VISIBLE : View.GONE);
            ((Button) v).setText(show ? "设置与日志 ▲" : "设置与日志 ▼");
        });

        findViewById(R.id.btn_del_source).setOnClickListener(v -> deleteSourceDialog());
        findViewById(R.id.btn_bind_app).setOnClickListener(v -> bindAppDialog());

        loadConfig();

        // 总开关
        disable_switch.setOnCheckedChangeListener((cb, b) -> {
            if (cb.isPressed()) {
                if (requirePerm()) {
                    return;
                }
                config.disabled = b;
                save();
            }
        });

        // 重新弹出重定向提示（旧机制保留）
        force_show_switch.setOnCheckedChangeListener((cb, b) -> {
            if (cb.isPressed()) {
                if (requirePerm()) {
                    return;
                }
                File f = new File(Environment.getExternalStorageDirectory(), "DCIM/Camera1/force_show.jpg");
                try {
                    if (b) {
                        if (!f.exists()) {
                            f.createNewFile();
                        }
                    } else {
                        f.delete();
                    }
                } catch (IOException e) {
                    Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 声音（true = 播放源视频声音）
        play_sound_switch.setOnCheckedChangeListener((cb, b) -> {
            if (cb.isPressed()) {
                if (requirePerm()) {
                    return;
                }
                config.playSound = b;
                save();
            }
        });

        // 强制私有目录
        force_private_dir.setOnCheckedChangeListener((cb, b) -> {
            if (cb.isPressed()) {
                if (requirePerm()) {
                    return;
                }
                config.forcePrivateDir = b;
                save();
            }
        });

        // Toast 提示（开关文字"关闭提示"）
        disable_toast_switch.setOnCheckedChangeListener((cb, b) -> {
            if (cb.isPressed()) {
                if (requirePerm()) {
                    return;
                }
                config.showToast = !b;
                save();
            }
        });

        // 宽高适配（开 = 等比放入留黑边；关 = 不适配，按视频原样输出）
        fit_inside_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean b) {
                if (cb.isPressed()) {
                    if (requirePerm()) {
                        return;
                    }
                    config.fitMode = b ? VcamConfig.FIT_INSIDE : VcamConfig.FIT_NONE;
                    save();
                }
            }
        });
    }

    private boolean requirePerm() {
        if (!has_permission()) {
            request_permission();
            return true;
        }
        return false;
    }

    private void loadConfig() {
        config = ConfigStore.loadForManager();
        if (disable_switch != null) {
            syncUi();
        }
        // 起源服务：让目标 App 经 127.0.0.1 读视频，绕开作用域存储的 EACCES
        try {
            startForegroundService(new android.content.Intent(this,
                    io.github.hpjxiangbabang.vcam.server.SourceServerService.class));
        } catch (Throwable t) {
            Toast.makeText(this, "源服务启动失败: " + t, Toast.LENGTH_SHORT).show();
        }
    }

    private void syncUi() {
        disable_switch.setChecked(config.disabled);
        play_sound_switch.setChecked(config.playSound);
        force_private_dir.setChecked(config.forcePrivateDir);
        disable_toast_switch.setChecked(!config.showToast);
        fit_inside_switch.setChecked(config.fitMode == VcamConfig.FIT_INSIDE);
        force_show_switch.setChecked(
                new File(Environment.getExternalStorageDirectory(), "DCIM/Camera1/force_show.jpg").exists());
        refreshSourcesSummary();
        refreshControlIcons();
    }

    /** 控制条图标与"当前源"标签（系统内置图标，保证渲染）。 */
    private void refreshControlIcons() {
        if (btnSound == null) {
            return;
        }
        btnSound.setImageResource(ControlState.videoSound
                ? android.R.drawable.ic_btn_speak_now
                : android.R.drawable.ic_lock_silent_mode);
        btnPause.setImageResource(ControlState.paused
                ? android.R.drawable.ic_media_play
                : android.R.drawable.ic_media_pause);
        if (currentSourceView != null) {
            String cur = config.defaultSourceName != null && config.defaultSourceName.length() > 0
                    ? config.defaultSourceName : "virtual.mp4(传统)";
            String state = ControlState.paused ? "（已暂停）" : "";
            currentSourceView.setText("当前源: " + cur + state);
        }
    }

    /** 控制条切换 = 全局切换：改默认源，并让所有已绑定应用跟着切（"当前视频"语义）。 */
    private void applyGlobalSource(String name) {
        config.defaultSourceName = name.equals("virtual.mp4") ? "" : name;
        for (VcamConfig.AppEntry e : config.apps.values()) {
            e.source = config.defaultSourceName;  // null = 跟随默认（即传统 virtual.mp4）
        }
        save();
    }

    /** 上一个/下一个源：在 [传统 virtual.mp4] + 源库 顺序上循环。 */
    private void cycleSource(int dir) {
        java.util.List<String> order = new java.util.ArrayList<>();
        order.add("virtual.mp4");
        order.addAll(config.sources.keySet());
        if (order.size() <= 1) {
            Toast.makeText(this, "源库为空，先添加源", Toast.LENGTH_SHORT).show();
            return;
        }
        String cur = config.defaultSourceName != null && config.defaultSourceName.length() > 0
                ? config.defaultSourceName : "virtual.mp4";
        int idx = order.indexOf(cur);
        if (idx < 0) {
            idx = 0;
        }
        int ni = ((idx + dir) % order.size() + order.size()) % order.size();
        String name = order.get(ni);
        applyGlobalSource(name);
        Toast.makeText(this, "已切换: " + name, Toast.LENGTH_SHORT).show();
    }

    private void save() {
        if (ConfigStore.saveForManager(config)) {
            Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.config_save_failed, Toast.LENGTH_SHORT).show();
        }
        syncUi();
    }

    private void request_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.permission_lack_warn);
                builder.setMessage(R.string.permission_description);
                builder.setNegativeButton(R.string.negative, (d, i) ->
                        Toast.makeText(this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show());
                builder.setPositiveButton(R.string.positive, (d, i) ->
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1));
                builder.show();
            }
        }
    }

    private boolean has_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED;
        }
        return true;
    }

    private void show_log() {
        if (log_view == null) {
            return;
        }
        if (!has_permission()) {
            log_view.setText(R.string.log_need_permission);
            return;
        }
        String text = AppLog.read();
        log_view.setText(text.length() == 0 ? getString(R.string.log_empty) : text);
    }

    // ==================== P2.1 源库与应用绑定 ====================

    private static final int REQ_PICK_VIDEO = 101;
    private static final int REQ_PICK_IMAGE = 102;
    private String pendingSourceType;

    /** SAF 文件选择器：MIME 预设过滤，杜绝选错格式。 */
    private void pickSource(String type) {
        pendingSourceType = type;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video".equals(type) ? "video/*" : "image/*");
        try {
            startActivityForResult(i, "video".equals(type) ? REQ_PICK_VIDEO : REQ_PICK_IMAGE);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器: " + t, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final String type = requestCode == REQ_PICK_VIDEO ? "video" : "image";
        android.net.Uri uri = data.getData();
        String name = displayName(uri);
        if (name == null || name.length() == 0) {
            name = (type.equals("video") ? "视频_" : "图片_") + System.currentTimeMillis();
        }
        String base = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        String key = base;
        int k = 2;
        while (config.sources.containsKey(key)) {
            key = base + "_" + k++;
        }
        VcamConfig.SourceEntry e = new VcamConfig.SourceEntry();
        e.type = type;

        // 持久化授权成功 → 存 content URI；失败 → 当场复制进模块私有目录（一次成本，
        // 之后完全脱离 SAF 授权体系，hook 端经 Provider FD 稳定读取）
        boolean persisted = false;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            persisted = true;
        } catch (Throwable ignored) {
        }
        if (persisted) {
            e.path = uri.toString();
        } else {
            try {
                File baseDir = new File(getExternalFilesDir(null), "Camera1");
                if (!baseDir.exists()) {
                    baseDir.mkdirs();
                }
                File dst = new File(baseDir, "src_" + System.currentTimeMillis() + "_" + base);
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
                byte[] buf = new byte[65536];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    total += n;
                }
                in.close();
                out.close();
                if (total < 1024) {
                    Toast.makeText(this, "复制源文件失败", Toast.LENGTH_LONG).show();
                    return;
                }
                e.path = dst.getAbsolutePath();
                Toast.makeText(this, "已复制到模块目录(" + (total / 1024) + "KB)", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "复制源文件异常: " + t, Toast.LENGTH_LONG).show();
                return;
            }
        }
        config.sources.put(key, e);
        save();
        if ("image".equals(type)) {
            Toast.makeText(this, "已添加，首次使用时自动生成缓存", Toast.LENGTH_SHORT).show();
        }
    }

    private String displayName(android.net.Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) {
                            return c.getString(idx);
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void refreshSourcesSummary() {
        TextView tv = findViewById(R.id.sources_summary);
        if (tv == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("默认源: ");
        if (config.defaultSourceName != null && config.defaultSourceName.length() > 0) {
            sb.append(config.defaultSourceName);
        } else {
            sb.append(config.defaultSource);
        }
        sb.append('\n');
        if (config.sources.isEmpty()) {
            sb.append("源库为空\n");
        } else {
            sb.append("源库:\n");
            for (java.util.Map.Entry<String, VcamConfig.SourceEntry> e : config.sources.entrySet()) {
                sb.append("  • ").append(e.getKey())
                        .append(" [").append(e.getValue().type).append("] ")
                        .append(e.getValue().path).append('\n');
            }
        }
        if (config.apps.isEmpty()) {
            sb.append("应用绑定: 无（全部走默认源）");
        } else {
            sb.append("应用绑定:");
            for (java.util.Map.Entry<String, VcamConfig.AppEntry> e : config.apps.entrySet()) {
                sb.append("\n  • ").append(e.getKey()).append(" → ")
                        .append(e.getValue().source == null || e.getValue().source.length() == 0
                                ? "默认源" : e.getValue().source);
            }
        }
        tv.setText(sb.toString());
    }

    private void deleteSourceDialog() {
        if (config.sources.isEmpty()) {
            Toast.makeText(this, "源库为空", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = config.sources.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("删除源")
                .setItems(names, (d, which) -> {
                    String n = names[which];
                    config.sources.remove(n);
                    for (VcamConfig.AppEntry e : config.apps.values()) {
                        if (n.equals(e.source)) {
                            e.source = null;
                        }
                    }
                    save();
                })
                .setNegativeButton(R.string.negative, null)
                .show();
    }

    /** 全局默认源（列表选择）：应用绑定 > 默认源选择 > 传统 virtual.mp4。
     * 控制条语义下，列表选择同样全局生效（所有绑定一起切）。 */
    private void defaultSourceDialog() {
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("传统 virtual.mp4");
        options.addAll(config.sources.keySet());
        final String[] items = options.toArray(new String[0]);
        String cur = config.defaultSourceName != null && config.defaultSourceName.length() > 0
                ? config.defaultSourceName : "传统 virtual.mp4";
        int checked = Math.max(0, options.indexOf(cur));
        new AlertDialog.Builder(this)
                .setTitle("选择当前源（全局切换）")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    String name = which == 0 ? "virtual.mp4" : items[which];
                    applyGlobalSource(name);
                    d.dismiss();
                })
                .setNegativeButton(R.string.negative, null)
                .show();
    }

    private void bindAppDialog() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> apps =
                getPackageManager().queryIntentActivities(intent, 0);
        java.util.TreeMap<String, String> map = new java.util.TreeMap<>();
        for (android.content.pm.ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) {
                continue;
            }
            map.put(pkg, ri.loadLabel(getPackageManager()).toString());
        }
        if (map.isEmpty()) {
            Toast.makeText(this, "没有可绑定的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] pkgs = map.keySet().toArray(new String[0]);
        final String[] display = new String[pkgs.length];
        for (int i = 0; i < pkgs.length; i++) {
            display[i] = map.get(pkgs[i]) + " (" + pkgs[i] + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setItems(display, (d, which) -> chooseSourceForApp(pkgs[which]))
                .setNegativeButton(R.string.negative, null)
                .show();
    }

    private void chooseSourceForApp(final String pkg) {
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("默认源");
        options.addAll(config.sources.keySet());
        final String[] items = options.toArray(new String[0]);
        VcamConfig.AppEntry cur = config.app(pkg);
        int checked = 0;
        if (cur.source != null) {
            int idx = options.indexOf(cur.source);
            checked = Math.max(0, idx);
        }
        new AlertDialog.Builder(this)
                .setTitle("绑定源 → " + pkg)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    VcamConfig.AppEntry e = config.app(pkg);
                    e.source = which == 0 ? null : items[which];
                    e.enabled = true;
                    config.apps.put(pkg, e);
                    save();
                    d.dismiss();
                })
                .setNegativeButton(R.string.negative, null)
                .show();
    }
}