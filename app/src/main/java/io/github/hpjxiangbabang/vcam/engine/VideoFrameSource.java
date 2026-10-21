package io.github.hpjxiangbabang.vcam.engine;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

/**
 * 视频帧源（P1 核心重构，替代旧 VideoToFrames）。
 *
 * 与旧实现的差别：
 * 1. 循环播放不再 stop/start MediaCodec —— EOS 后 seekTo(0) + flush 复用同一编解码器；
 * 2. 输出节奏按 presentationTime 对齐（旧实现按帧序猜测，快慢取决于机器）；
 * 3. NV21 输出走 FrameBuffer（最新帧语义），消费端不再忙等；
 * 4. 缓冲模式支持按目标宽高适配（inside/fill/stretch）。
 *
 * 两种模式（同一实现，构造参数决定）：
 *   render 模式 —— 直接解码渲染到目标 Surface（camera2 的 ImageReader/预览面）
 *   buffer 模式 —— 解出 NV21 交给 FrameBuffer（camera1 onPreviewFrame / 拍照）
 */
public final class VideoFrameSource {

    private static final long TIMEOUT_US = 10000L;

    public interface ErrorListener {
        void onError(Throwable t, String stage);
    }

    private final String mPath;
    private final Surface mRenderSurface;   // null => buffer 模式
    private final FrameBuffer mBuffer;      // buffer 模式输出；render 模式为 null
    private final AtomicBoolean mFitDirty;  // 目标尺寸变化标记（buffer 模式）
    private volatile int mTargetW;
    private volatile int mTargetH;
    private volatile int mFitMode = FrameFitter.FIT_INSIDE;
    private volatile boolean mStopped = false;
    private Thread mThread;
    private ErrorListener mErrorListener;

    public VideoFrameSource(String videoPath, Surface renderSurface) {
        mPath = videoPath;
        mRenderSurface = renderSurface;
        mBuffer = null;
        mFitDirty = null;
    }

    public VideoFrameSource(String videoPath, FrameBuffer buffer) {
        mPath = videoPath;
        mRenderSurface = null;
        mBuffer = buffer;
        mFitDirty = new AtomicBoolean(false);
    }

    public void setTargetSize(int width, int height, int fitMode) {
        mTargetW = width;
        mTargetH = height;
        mFitMode = fitMode;
        if (mFitDirty != null) {
            mFitDirty.set(true);
        }
    }

    public void setErrorListener(ErrorListener l) {
        mErrorListener = l;
    }

    public synchronized void start() {
        if (mThread != null || mStopped) {
            return;
        }
        mStopped = false;
        mThread = new Thread(this::loop, "vcam-frame-source");
        mThread.setPriority(Thread.NORM_PRIORITY - 1);
        mThread.start();
    }

    public void stop() {
        mStopped = true;
        synchronized (this) {
            if (mThread != null) {
                mThread.interrupt();
            }
            mThread = null;
        }
        if (mBuffer != null) {
            mBuffer.close();
        }
    }

    private void report(Throwable t, String stage) {
        try {
            XposedBridge.log("【VCAM】【source】" + stage + ":" + t);
        } catch (Throwable ignored) {
        }
        ErrorListener l = mErrorListener;
        if (l != null) {
            try {
                l.onError(t, stage);
            } catch (Throwable ignored) {
            }
        }
    }

    private void loop() {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        String mime = null;
        MediaFormat format = null;
        try {
            File f = new File(mPath);
            if (!f.exists() || !f.isFile() || f.length() < 1024) {
                report(new IllegalStateException("视频不存在或过小: " + mPath), "open");
                return;
            }
            extractor = new MediaExtractor();
            extractor.setDataSource(mPath);
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fm = extractor.getTrackFormat(i);
                if (fm.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    track = i;
                    format = fm;
                    break;
                }
            }
            if (track < 0) {
                report(new IllegalStateException("无视频轨: " + mPath), "open");
                return;
            }
            extractor.selectTrack(track);
            mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);

            if (mRenderSurface != null) {
                decoder.configure(format, mRenderSurface, null, 0);
            } else {
                // 缓冲模式：YUV420Flexible，getOutputImage 取 NV21
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                decoder.configure(format, null, null, 0);
            }
            decoder.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startWallMs = -1;
            long ptsBaseUs = -1;
            boolean sawInputEos = false;
            boolean sawOutputEos = false;
            int lastW = 0;
            int lastH = 0;

            while (!mStopped && !Thread.currentThread().isInterrupted()) {
                // ---- 输入 ----
                if (!sawInputEos) {
                    int inIdx = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inIdx >= 0) {
                        ByteBuffer in = decoder.getInputBuffer(inIdx);
                        int size = in != null ? extractor.readSampleData(in, 0) : -1;
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEos = true;
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                // ---- 输出 ----
                int outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIdx >= 0) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (info.size > 0) {
                        if (startWallMs < 0) {
                            startWallMs = System.currentTimeMillis();
                            ptsBaseUs = info.presentationTimeUs;
                        }
                        if (mRenderSurface != null) {
                            long dueMs = (info.presentationTimeUs - ptsBaseUs) / 1000L;
                            long waitMs = dueMs - (System.currentTimeMillis() - startWallMs);
                            if (waitMs > 0 && waitMs < 400) {
                                try {
                                    Thread.sleep(waitMs);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            decoder.releaseOutputBuffer(outIdx, true);
                        } else {
                            android.media.Image image = decoder.getOutputImage(outIdx);
                            if (image != null) {
                                android.graphics.Rect crop = image.getCropRect();
                                int w = crop.width();
                                int h = crop.height();
                                if (mFitDirty == null || mFitDirty.compareAndSet(true, false)) {
                                    lastW = w;
                                    lastH = h;
                                }
                                byte[] nv21 = extractNv21(image, w, h);
                                image.close();
                                if (nv21 != null && mBuffer != null) {
                                    int tw = mTargetW > 2 ? mTargetW : w;
                                    int th = mTargetH > 2 ? mTargetH : h;
                                    byte[] out = FrameFitter.adapt(nv21, w, h, tw, th, mFitMode);
                                    mBuffer.put(out != null ? out : nv21,
                                            out != null ? tw : w, out != null ? th : h);
                                }
                            }
                            decoder.releaseOutputBuffer(outIdx, false);
                        }
                    }
                    if (eos) {
                        sawOutputEos = true;
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = decoder.getOutputFormat();
                    // 实际视频宽高以输出 format 为准（容器里可能是 0）
                    if (of.containsKey(MediaFormat.KEY_WIDTH) && mTargetW <= 2 && mRenderSurface == null) {
                        lastW = of.getInteger(MediaFormat.KEY_WIDTH);
                        lastH = of.getInteger(MediaFormat.KEY_HEIGHT);
                    }
                }

                // ---- 循环播放：复用编解码器，不重建 ----
                if (sawOutputEos && (sawInputEos || !hasPendingInput(decoder))) {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    try {
                        decoder.flush();
                    } catch (Throwable t) {
                        report(t, "flush");
                        decoder.stop();
                        decoder.release();
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        decoder = MediaCodec.createDecoderByType(mime);
                        if (mRenderSurface != null) {
                            decoder.configure(format, mRenderSurface, null, 0);
                        } else {
                            decoder.configure(format, null, null, 0);
                        }
                        decoder.start();
                    }
                    sawInputEos = false;
                    sawOutputEos = false;
                    startWallMs = -1;
                    ptsBaseUs = -1;
                }
            }
        } catch (Throwable t) {
            if (!mStopped) {
                report(t, "loop");
            }
        } finally {
            try {
                if (decoder != null) {
                    decoder.stop();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (decoder != null) {
                    decoder.release();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (extractor != null) {
                    extractor.release();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean hasPendingInput(MediaCodec decoder) {
        // flush 后 EOS 输入旗标会清掉，这里仅用于保守判断；恒 false 让 EOS→flush 正常推进
        return false;
    }

    /** Image → NV21（移植自旧 VideoToFrames.getDataFromImage，去日志噪音）。 */
    private static byte[] extractNv21(android.media.Image image, int width, int height) {
        try {
            android.graphics.Rect crop = image.getCropRect();
            android.media.Image.Plane[] planes = image.getPlanes();
            byte[] data = new byte[width * height * 3 / 2];
            byte[] rowData = new byte[planes[0].getRowStride()];
            int channelOffset = 0;
            int outputStride = 1;
            for (int i = 0; i < planes.length; i++) {
                switch (i) {
                    case 0:
                        channelOffset = 0;
                        outputStride = 1;
                        break;
                    case 1: // NV21: VU 交织，U 起点 +1
                        channelOffset = width * height + 1;
                        outputStride = 2;
                        break;
                    case 2:
                        channelOffset = width * height;
                        outputStride = 2;
                        break;
                    default:
                        break;
                }
                ByteBuffer buffer = planes[i].getBuffer();
                int rowStride = planes[i].getRowStride();
                int pixelStride = planes[i].getPixelStride();
                int shift = (i == 0) ? 0 : 1;
                int w = width >> shift;
                int h = height >> shift;
                buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
                for (int row = 0; row < h; row++) {
                    int length;
                    if (pixelStride == 1 && outputStride == 1) {
                        length = w;
                        buffer.get(data, channelOffset, length);
                        channelOffset += length;
                    } else {
                        length = (w - 1) * pixelStride + 1;
                        buffer.get(rowData, 0, length);
                        for (int col = 0; col < w; col++) {
                            data[channelOffset] = rowData[col * pixelStride];
                            channelOffset += outputStride;
                        }
                    }
                    if (row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                }
            }
            return data;
        } catch (Throwable t) {
            try {
                de.robv.android.xposed.XposedBridge.log("【VCAM】【source】nv21:" + t);
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}