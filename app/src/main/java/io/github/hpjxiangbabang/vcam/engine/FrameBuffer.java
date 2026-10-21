package io.github.hpjxiangbabang.vcam.engine;

/**
 * 单槽"最新帧"缓冲，替代旧实现的忙等（while(data_buffer == null) {}）。
 * 生产者持续覆盖，消费者带超时等待；帧过旧时消费者自行决定是否复用。
 */
public final class FrameBuffer {

    public static final class Frame {
        public final byte[] data;
        public final int width;
        public final int height;
        public final long timestampMs;
        public final long frameIndex;

        Frame(byte[] data, int width, int height, long timestampMs, long frameIndex) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.timestampMs = timestampMs;
            this.frameIndex = frameIndex;
        }
    }

    private Frame latest;
    private long counter;
    private boolean closed;

    public void put(byte[] data, int width, int height) {
        synchronized (this) {
            if (closed || data == null || width < 2 || height < 2) {
                return;
            }
            counter++;
            latest = new Frame(data, width, height, System.currentTimeMillis(), counter);
            notifyAll();
        }
    }

    /** 带超时等待新帧。超时返回 null；maxAgeMs < 0 表示不检查陈旧度。 */
    public Frame awaitFresh(long timeoutMs, long maxAgeMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (this) {
            long lastSeen = counter;
            while (true) {
                if (closed) {
                    return null;
                }
                if (latest != null && counter != lastSeen) {
                    if (maxAgeMs < 0 || System.currentTimeMillis() - latest.timestampMs <= maxAgeMs) {
                        return latest;
                    }
                }
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    return latest; // 超时：宁可给旧帧也不给 null，让目标 App 画面冻结得体面些
                }
                try {
                    wait(Math.min(remain, 100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return latest;
                }
            }
        }
    }

    /** 不等待，直接取当前帧。 */
    public synchronized Frame latest() {
        return latest;
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    public synchronized void reopen() {
        closed = false;
    }
}