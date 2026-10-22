package io.github.hpjxiangbabang.vcam;

/**
 * 播放运行时控制状态（模块进程内的唯一权威，UI 写、Provider 读、hook 端轮询）。
 * 只存内存，不持久化：重启 App 后回到默认（麦克风静音模式 / 播放中）。
 */
public final class ControlState {

    /** true = 暂停（冻结当前帧）；false = 播放中 */
    public static volatile boolean paused = false;

    /** true = 播放视频自带声音；false = 静音（麦克风模式：通话走真实麦克风） */
    public static volatile boolean videoSound = false;

    private ControlState() {
    }
}