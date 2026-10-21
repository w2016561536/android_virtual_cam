package io.github.hpjxiangbabang.vcam;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 挂钩代码跑在目标应用进程里，本模块界面是另一个应用。
 * 两边不能直接共享变量，所以把要给人看的提示写到同一个文本文件。
 */
public class AppLog {
    public static final Uri LOG_URI = Uri.parse("content://io.github.hpjxiangbabang.vcam.log/line");
    private static final String SEPARATOR = "\n--------\n";
    private static final int MAX_ENTRIES = 60;

    public static File logFile() {
        return new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/vcam_log.txt");
    }

    public static boolean appendFromHook(Context context, String message) {
        if (context != null) {
            try {
                ContentValues values = new ContentValues();
                values.put("message", message);
                Uri result = context.getContentResolver().insert(LOG_URI, values);
                if (result != null) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return append(message);
    }

    public static boolean append(String message) {
        if (message == null) {
            return false;
        }
        String body = message.trim();
        if (body.length() == 0) {
            return false;
        }
        synchronized (AppLog.class) {
            try {
                File file = logFile();
                File dir = file.getParentFile();
                if (dir != null && !dir.exists() && !dir.mkdirs()) {
                    return false;
                }
                String old = read();
                String time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
                String next;
                if (body.equals(bodyOfNewest(old))) {
                    int newline = old.indexOf('\n');
                    String rest = newline >= 0 ? old.substring(newline) : "";
                    next = "[" + time + "]" + rest;
                } else if (old.length() == 0) {
                    next = "[" + time + "]\n" + body;
                } else {
                    next = trimEntries("[" + time + "]\n" + body + SEPARATOR + old);
                }
                Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8");
                writer.write(next);
                writer.flush();
                writer.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static String read() {
        File file = logFile();
        if (!file.exists()) {
            return "";
        }
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
            reader.close();
            return builder.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static void clear() {
        synchronized (AppLog.class) {
            File file = logFile();
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private static String bodyOfNewest(String all) {
        if (all == null || all.length() == 0) {
            return "";
        }
        String first = all;
        int separator = all.indexOf(SEPARATOR);
        if (separator >= 0) {
            first = all.substring(0, separator);
        }
        int newline = first.indexOf('\n');
        if (newline < 0 || newline == first.length() - 1) {
            return "";
        }
        return first.substring(newline + 1).trim();
    }

    private static String trimEntries(String all) {
        String[] parts = all.split(SEPARATOR, -1);
        if (parts.length <= MAX_ENTRIES) {
            return all;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_ENTRIES; i++) {
            if (i > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }
}
