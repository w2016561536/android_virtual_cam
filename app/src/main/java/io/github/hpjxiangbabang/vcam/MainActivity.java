package io.github.hpjxiangbabang.vcam;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;
    private Switch fit_inside_switch;
    private TextView log_view;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show();
            }else {
                File camera_dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/Camera1/");
                if (!camera_dir.exists()){
                    camera_dir.mkdir();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync_statue_with_files();
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

        sync_statue_with_files();

        disable_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File disable_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg");
                    if (disable_file.exists() != b){
                        if (b){
                            try {
                                disable_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            disable_file.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

        force_show_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File force_show_switch = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
                    if (force_show_switch.exists() != b){
                        if (b){
                            try {
                                force_show_switch.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            force_show_switch.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

        play_sound_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (compoundButton.isPressed()) {
                    if (!has_permission()) {
                        request_permission();
                    } else {
                        File play_sound_switch = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no-silent.jpg");
                        if (play_sound_switch.exists() != b){
                            if (b){
                                try {
                                    play_sound_switch.createNewFile();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }else {
                                play_sound_switch.delete();
                            }
                        }
                    }
                    sync_statue_with_files();
                }
            }
        });

        force_private_dir.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File force_private_dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg");
                    if (force_private_dir.exists() != b){
                        if (b){
                            try {
                                force_private_dir.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            force_private_dir.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });


        disable_toast_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File disable_toast_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no_toast.jpg");
                    if (disable_toast_file.exists() != b){
                        if (b){
                            try {
                                disable_toast_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            disable_toast_file.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

        fit_inside_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File fit_inside_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/fit_inside.jpg");
                    if (fit_inside_file.exists() != b) {
                        if (b) {
                            try {
                                fit_inside_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            fit_inside_file.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

    }

    private void request_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.permission_lack_warn);
                builder.setMessage(R.string.permission_description);

                builder.setNegativeButton(R.string.negative, (dialogInterface, i) -> Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show());

                builder.setPositiveButton(R.string.positive, (dialogInterface, i) -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1));
                builder.show();
            }
        }
    }

    private boolean has_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED
                    && this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED;
        }
        return true;
    }


    private void sync_statue_with_files() {
        Log.d(this.getApplication().getPackageName(), "【VCAM】[sync]同步开关状态");

        if (!has_permission()){
            request_permission();
        }else {
            File camera_dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/Camera1");
            if (!camera_dir.exists()){
                camera_dir.mkdir();
            }
        }

        File disable_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg");
        disable_switch.setChecked(disable_file.exists());

        File force_show_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
        force_show_switch.setChecked(force_show_file.exists());

        File play_sound_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no-silent.jpg");
        play_sound_switch.setChecked(play_sound_file.exists());

        File force_private_dir_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg");
        force_private_dir.setChecked(force_private_dir_file.exists());

        File disable_toast_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no_toast.jpg");
        disable_toast_switch.setChecked(disable_toast_file.exists());

        File fit_inside_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/fit_inside.jpg");
        fit_inside_switch.setChecked(fit_inside_file.exists());

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
        if (text.length() == 0) {
            log_view.setText(R.string.log_empty);
        } else {
            log_view.setText(text);
        }
    }


}



