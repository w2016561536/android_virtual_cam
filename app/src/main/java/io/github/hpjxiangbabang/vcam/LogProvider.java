package io.github.hpjxiangbabang.vcam;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * 目标应用把一条提示交过来，真正写文件发生在本模块自己的进程里。
 * 这样不依赖目标应用有没有写入存储的权限。
 */
public class LogProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (values == null) {
            return null;
        }
        String message = values.getAsString("message");
        if (!AppLog.append(message)) {
            return null;
        }
        return uri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
