package com.example.vcam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @SuppressLint("WorldReadableFiles")
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.button);
        EditText viewt1 =(EditText) findViewById(R.id.editTextTextPersonName);
        SharedPreferences stored_id = getSharedPreferences("camera_id", Context.MODE_PRIVATE);
        String Texture_path = stored_id.getString("id","");

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View viewa) {
                String text_string = viewt1.getText().toString();
                SharedPreferences.Editor ideditor = stored_id.edit();
                ideditor.putString("id",text_string);
                ideditor.commit();
                Toast Toast1 =Toast.makeText(getApplicationContext(),"已保存",Toast.LENGTH_SHORT);
                Toast1.show();
            }
        });
    }
}
