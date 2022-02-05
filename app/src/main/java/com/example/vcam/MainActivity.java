package com.example.vcam;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.io.File;

import de.robv.android.xposed.XposedBridge;

public class MainActivity extends Activity {
    @SuppressLint("CommitPrefEdits")
    public void onCreate(Bundle savedInstanceState) {
        makeWorldReadable();

        SharedPreferences preference;
        preference = this.getSharedPreferences("module_set", MODE_PRIVATE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button repo_button = findViewById(R.id.button);
        Switch force_display = findViewById(R.id.switch2);
        Switch disable_module_switch = findViewById(R.id.switch1);

        disable_module_switch.setChecked(preference.getBoolean("disable", false));
        force_display.setChecked(preference.getBoolean("force_display",false));

        repo_button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Uri uri = Uri.parse("https://github.com/w2016561536/android_virtual_cam");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });

        Button repo_button_chinamainland = findViewById(R.id.button2);
        repo_button_chinamainland.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri uri = Uri.parse("https://gitee.com/w2016561536/android_virtual_cam");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });


        SharedPreferences finalPreference = preference;
        disable_module_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor editor = finalPreference.edit();
                editor.putBoolean("disable",b);
                editor.apply();
                editor.commit();
                makeWorldReadable();
            }
        });

        force_display.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor editor = finalPreference.edit();
                editor.putBoolean("force_display",b);
                editor.apply();
                editor.commit();
                makeWorldReadable();
            }
        });


    }

    @TargetApi(Build.VERSION_CODES.N)
    @SuppressLint({"SetWorldReadable", "SetWorldWritable"})
    private void makeWorldReadable() {
        try {
            File f = new File(this.getDataDir().getAbsolutePath()+"/shared_prefs/module_set.xml");
            f.setReadable(true, false);
            f.setExecutable(true, false);
            f.setWritable(true, false);
        } catch (Exception e) {
            XposedBridge.log("【VCAM】权限设置失败"+ e.toString());
        }
    }
}


