package io.github.hpjxiangbabang.vcam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;
import io.github.hpjxiangbabang.vcam.engine.FrameFitter;

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
}