package io.github.hpjxiangbabang.vcam.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import de.robv.android.xposed.XposedBridge;
import io.github.hpjxiangbabang.vcam.AppLog;
import io.github.hpjxiangbabang.vcam.R;
import io.github.hpjxiangbabang.vcam.config.ConfigStore;
import io.github.hpjxiangbabang.vcam.config.VcamConfig;

/**
 * 本地视频源 HTTP 服务（P1 存储权限解法）。
 *
 * Android 11+ 作用域存储下，目标 App 往往读不了 /sdcard/DCIM/Camera1/virtual.mp4
 * （EACCES）。本服务跑在模块进程里（模块 targetSdk 26 保留旧版存储视图，能读），
 * hook 端直接播 http://127.0.0.1:<port>/v —— 目标 App 只需要 INTERNET 权限。
 *
 * 只绑定 loopback；支持 Range 请求（MediaExtractor 循环 seekTo(0) 会发）。
 * 服务启动后把 http://127.0.0.1:<port>/v 写进 config.json 的 server_url，
 * hook 端优先用 URL，URL 不可用时回落文件路径。
 */
public class SourceServerService extends Service {

    private static final String CHANNEL_ID = "vcam_source";
    private static final int NOTIFY_ID = 42;

    private static volatile int sPort = -1;
    private ServerSocket mServerSocket;
    private Thread mAcceptThread;
    private volatile boolean mRunning = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.i("VCAM-SERVER", "service onCreate");
        startForeground(NOTIFY_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.i("VCAM-SERVER", "onStartCommand running=" + mRunning);
        if (!mRunning) {
            mRunning = true;
            mAcceptThread = new Thread(this::serve, "vcam-source-server");
            mAcceptThread.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        try {
            if (mServerSocket != null) {
                mServerSocket.close();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.server_channel), NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.server_title))
                .setContentText(getString(R.string.server_text))
                .setOngoing(true)
                .build();
    }

    private void serve() {
        try {
            // 固定端口优先：hook 端无须读配置即可发现服务（作用域存储下读不到 config.json）
            try {
                mServerSocket = new ServerSocket(ConfigStore.SERVER_PORT, 8, InetAddress.getLoopbackAddress());
            } catch (Throwable t) {
                mServerSocket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            }
            sPort = mServerSocket.getLocalPort();
            publishUrl();
            while (mRunning) {
                final Socket socket = mServerSocket.accept();
                Thread t = new Thread(() -> handle(socket), "vcam-src-conn");
                t.setDaemon(true);
                t.start();
            }
        } catch (Throwable t) {
            if (mRunning) {
                log("源服务器异常:" + t);
            }
        }
    }

    private void publishUrl() {
        try {
            VcamConfig c = ConfigStore.loadForManager();
            c.serverUrl = "http://127.0.0.1:" + sPort + "/v";
            ConfigStore.saveForManager(c);
            log("源服务就绪 " + c.serverUrl);
        } catch (Throwable t) {
            log("配置写入失败:" + t);
        }
    }

    private void handle(Socket socket) {
        String peer = "unknown";
        try {
            peer = socket.getRemoteSocketAddress() != null ? socket.getRemoteSocketAddress().toString() : "?";
        } catch (Throwable ignored) {
        }
        try {
            log("conn from " + peer);
            socket.setSoTimeout(8000);
            InputStream in = socket.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null || !requestLine.startsWith("GET ")) {
                log("bad request line: " + requestLine);
                socket.close();
                return;
            }
            String path = requestLine.split(" ")[1];
            path = URLDecoder.decode(path, "UTF-8");
            // 配置端点：目标 App 拿配置的唯一可靠通道（绕开作用域存储）
            if (path.equals("/config")) {
                File cfg = ConfigStore.publicConfigFile();
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                if (cfg.isFile()) {
                    body = java.nio.file.Files.readAllBytes(cfg.toPath());
                }
                StringBuilder sb = new StringBuilder();
                sb.append("HTTP/1.1 200 OK\r\n");
                sb.append("Content-Type: application/json\r\n");
                sb.append("Content-Length: ").append(body.length).append("\r\n");
                sb.append("Connection: close\r\n\r\n");
                OutputStream out = socket.getOutputStream();
                out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
                socket.close();
                log("config served (" + body.length + "B)");
                return;
            }
            // /v 或 /v/<文件名>；文件名用于以后多源切换
            String name = "virtual.mp4";
            if (path.startsWith("/v/")) {
                name = path.substring(3);
            } else if (!path.startsWith("/v")) {
                respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8), null);
                return;
            }

            // 只允许安全文件名
            if (name.contains("..") || name.contains("/") || name.contains("\\")) {
                respond(socket, 403, "text/plain", "forbidden".getBytes(StandardCharsets.UTF_8), null);
                return;
            }

            // 按源名解析（源库/懒生成/默认源兜底），统一 FD 出口（支持 content:// URI）
            ParcelFileDescriptor srcPfd = SourceResolver.openFd(this, name);
            if (srcPfd == null) {
                log("serve 404 name=" + name);
                respond(socket, 404, "text/plain", "no source".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            long total = srcPfd.getStatSize();
            long from = 0;
            long to = total - 1;
            String range = readHeader(in, "Range:");
            boolean partial = false;
            if (range != null) {
                try {
                    String spec = range.substring(range.indexOf("bytes=") + 6).trim();
                    int dash = spec.indexOf('-');
                    from = Long.parseLong(spec.substring(0, dash).trim());
                    String end = spec.substring(dash + 1).trim();
                    if (end.length() > 0) {
                        to = Long.parseLong(end);
                    }
                    if (from > to || from >= total) {
                        respond(socket, 416, "text/plain", "bad range".getBytes(StandardCharsets.UTF_8), null);
                        return;
                    }
                    to = Math.min(to, total - 1);
                    partial = true;
                } catch (Throwable ignored) {
                }
            }

            OutputStream out = socket.getOutputStream();
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(partial ? "206 Partial Content" : "200 OK").append("\r\n");
            sb.append("Content-Type: video/mp4\r\n");
            sb.append("Accept-Ranges: bytes\r\n");
            sb.append("Content-Length: ").append(to - from + 1).append("\r\n");
            if (partial) {
                sb.append("Content-Range: bytes ").append(from).append("-").append(to)
                        .append("/").append(total).append("\r\n");
            }
            sb.append("Connection: close\r\n\r\n");
            out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            InputStream fis = new BufferedInputStream(new ParcelFileDescriptor.AutoCloseInputStream(srcPfd));
            if (from > 0) {
                long skipped = fis.skip(from);
                if (skipped < from) {
                    fis.close();
                    respond(socket, 416, "text/plain", "bad range".getBytes(StandardCharsets.UTF_8), null);
                    return;
                }
            }
            byte[] buf = new byte[65536];
            long remain = to - from + 1;
            try {
                while (remain > 0 && mRunning) {
                    int n = fis.read(buf, 0, (int) Math.min(buf.length, remain));
                    if (n < 0) {
                        break;
                    }
                    out.write(buf, 0, n);
                    remain -= n;
                }
            } catch (Throwable ignored) {
                // 客户端断开（seek）属正常
            }
            try {
                fis.close();
            } catch (Throwable ignored) {
            }
            out.flush();
            socket.close();
        } catch (Throwable t) {
            log("handle 异常[" + peer + "]:" + t);
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private String generateImageCache(String imagePath) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, o);
            if (o.outWidth <= 0) {
                log("图片解码失败: " + imagePath);
                return null;
            }
            BitmapFactory.Options b = new BitmapFactory.Options();
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1280) {
                s *= 2;
            }
            b.inSampleSize = s;
            Bitmap bmp = BitmapFactory.decodeFile(imagePath, b);
            if (bmp == null) {
                return null;
            }
            File dir = new File(getExternalFilesDir(null), "Camera1/imgcache");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File cache = new File(dir, "img_" + Integer.toHexString(imagePath.hashCode()) + ".mp4");
            File out = ImageVideoEncoder.encode(bmp, cache);
            bmp.recycle();
            return out != null ? out.getAbsolutePath() : null;
        } catch (Throwable t) {
            log("generateImageCache: " + t);
            return null;
        }
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String readHeader(InputStream in, String prefix) throws Exception {
        String line;
        while ((line = readLine(in)) != null) {
            if (line.length() == 0) {
                return null;
            }
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return null;
    }

    private void respond(Socket socket, int code, String type, byte[] body, String extra) throws Exception {
        OutputStream out = socket.getOutputStream();
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(" \r\n");
        sb.append("Content-Type: ").append(type).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
        socket.close();
    }

    private static void log(String msg) {
        try {
            android.util.Log.i("VCAM-SERVER", msg);
        } catch (Throwable ignored) {
        }
        try {
            de.robv.android.xposed.XposedBridge.log("【VCAM】【server】" + msg);
        } catch (Throwable ignored) {
        }
        AppLog.append(msg);
    }

    /** hook 端读取当前服务地址（可能还没起）。 */
    public static String currentUrl() {
        int p = sPort;
        return p > 0 ? "http://127.0.0.1:" + p + "/v" : null;
    }
}