package io.github.hpjxiangbabang.vcam.server;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * P2.1：静态图片 → 循环视频（H.264, 25fps × 5s）。
 *
 * 图片源直接复用整条视频管线（camera1/2 渲染面、NV21 回调、宽高适配、
 * 循环播放），不需要为图片单独写 GL/ImageWriter 渲染路径。
 * 在模块进程里生成一次，缓存成 mp4，由源服务喂给目标 App。
 */
public final class ImageVideoEncoder {

    private static final String TAG = "VCAM-IMG";
    private static final int FPS = 25;
    private static final int SECONDS = 5;
    private static final int LONG_EDGE_CAP = 1280;

    private ImageVideoEncoder() {
    }

    /** 生成静态循环视频。返回缓存文件；失败返回 null。 */
    public static File encode(Bitmap bitmap, File outFile) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (Math.max(w, h) > LONG_EDGE_CAP) {
            float scale = LONG_EDGE_CAP / (float) Math.max(w, h);
            w = Math.round(w * scale);
            h = Math.round(h * scale);
        }
        w &= ~1;
        h &= ~1;
        if (w < 16 || h < 16) {
            log("尺寸过小: " + bitmap.getWidth() + "×" + bitmap.getHeight());
            return null;
        }
        Bitmap frame = Bitmap.createScaledBitmap(bitmap, w, h, true);

        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        Surface input = null;
        boolean muxerStarted = false;
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", w, h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, estimateBitrate(w, h));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder = MediaCodec.createEncoderByType("video/avc");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = encoder.createInputSurface();
            encoder.start();

            muxer = new MediaMuxer(outFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = -1;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int total = FPS * SECONDS;

            for (int i = 0; i <= total; i++) {
                if (i < total) {
                    android.graphics.Canvas c = input.lockCanvas(null);
                    if (c != null) {
                        c.drawBitmap(frame, 0, 0, null);
                        input.unlockCanvasAndPost(c);
                    }
                } else {
                    encoder.signalEndOfInputStream();
                }

                // 排空编码输出（pts 用编码器自身时间戳，画布逐帧按时长 post，天然匀速）
                while (true) {
                    int outIdx = encoder.dequeueOutputBuffer(info, 10000);
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(encoder.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    } else if (outIdx >= 0) {
                        ByteBuffer buf = encoder.getOutputBuffer(outIdx);
                        if (buf != null && info.size > 0 && muxerStarted) {
                            muxer.writeSampleData(track, buf, info);
                        }
                        encoder.releaseOutputBuffer(outIdx, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return done(muxer, encoder, outFile);
                        }
                    }
                }

                if (i < total - 1) {
                    try {
                        Thread.sleep(1000 / FPS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            log("encode failed: " + t);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.release();
                } catch (Throwable ignored) {
                }
            }
            try {
                if (encoder != null) {
                    encoder.stop();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (encoder != null) {
                    encoder.release();
                }
            } catch (Throwable ignored) {
            }
            if (muxerStarted) {
                try {
                    muxer.stop();
                } catch (Throwable ignored) {
                }
            }
            try {
                if (muxer != null) {
                    muxer.release();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static File done(MediaMuxer muxer, MediaCodec encoder, File outFile) {
        boolean ok = outFile.isFile() && outFile.length() > 4096;
        log(ok ? "编码完成: " + outFile.getName() : "编码输出异常小");
        return ok ? outFile : null;
    }

    private static int estimateBitrate(int w, int h) {
        return Math.max(2_000_000, w * h * FPS / 4);
    }

    private static void log(String msg) {
        try {
            android.util.Log.i(TAG, msg);
        } catch (Throwable ignored) {
        }
    }
}