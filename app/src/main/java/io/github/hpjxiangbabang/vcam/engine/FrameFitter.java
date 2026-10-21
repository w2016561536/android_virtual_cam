package io.github.hpjxiangbabang.vcam.engine;

import java.util.Arrays;

/**
 * NV21 帧在不同宽高间的适配。
 * P1 三种策略：inside（等比放入留黑边，原 fit_inside.jpg 行为）、
 * fill（等比铺满裁边）、stretch（拉伸铺满）。
 */
public final class FrameFitter {

    public static final int FIT_INSIDE = 0;
    public static final int FIT_FILL = 1;
    public static final int FIT_STRETCH = 2;

    private FrameFitter() {
    }

    /** 返回新缓冲；宽高相同或入参不合法时原样返回。 */
    public static byte[] adapt(byte[] src, int srcW, int srcH, int dstW, int dstH, int fitMode) {
        if (src == null || srcW < 2 || srcH < 2 || dstW < 2 || dstH < 2) {
            return src;
        }
        if (src.length < srcW * srcH * 3 / 2) {
            return src;
        }
        if (srcW == dstW && srcH == dstH) {
            return src;
        }
        if (fitMode == FIT_NONE) {
            return src;
        }
        if (fitMode == FIT_STRETCH) {
            return resize(src, srcW, srcH, dstW, dstH);
        }
        int drawW;
        int drawH;
        if (fitMode == FIT_FILL) {
            drawH = dstH;
            drawW = dstH * srcW / srcH;
            if (drawW < dstW) {
                drawW = dstW;
                drawH = dstW * srcH / srcW;
            }
        } else { // INSIDE
            drawW = dstW;
            drawH = dstW * srcH / srcW;
            if (drawH > dstH || drawH < 2) {
                drawH = dstH;
                drawW = dstH * srcW / srcH;
            }
        }
        drawW &= ~1;
        drawH &= ~1;
        if (drawW < 2 || drawH < 2) {
            return null;
        }
        if (fitMode == FIT_FILL) {
            // 铺满后居中裁边：先缩放到覆盖尺寸再裁
            byte[] scaled = resizeCropCenter(src, srcW, srcH, drawW, drawH, dstW, dstH);
            if (scaled == null) {
                return src;
            }
            return scaled;
        }
        return resizeWithLetterbox(src, srcW, srcH, drawW, drawH, dstW, dstH);
    }

    /** 等比放入 + 黑边（旧 FrameFitter.fitNv21 语义）。 */
    private static byte[] resizeWithLetterbox(byte[] src, int srcW, int srcH,
                                              int drawW, int drawH, int dstW, int dstH) {
        int offX = Math.max(0, ((dstW - drawW) / 2) & ~1);
        int offY = Math.max(0, ((dstH - drawH) / 2) & ~1);
        int ySize = dstW * dstH;
        byte[] dst = new byte[ySize + (dstW / 2) * (dstH / 2) * 2];
        Arrays.fill(dst, ySize, dst.length, (byte) 128);
        blit(src, srcW, srcH, drawW, drawH, dst, dstW, dstH, offX, offY);
        return dst;
    }

    /** 等比铺满 + 居中裁边。 */
    private static byte[] resizeCropCenter(byte[] src, int srcW, int srcH,
                                           int drawW, int drawH, int dstW, int dstH) {
        int offX = Math.max(0, (drawW - dstW) / 2) & ~1;
        int offY = Math.max(0, (drawH - dstH) / 2) & ~1;
        offX = Math.min(offX, drawW - dstW);
        offY = Math.min(offY, drawH - dstH);
        if (offX < 0 || offY < 0 || drawW < dstW || drawH < dstH) {
            return null;
        }
        byte[] mid = new byte[drawW * drawH * 3 / 2];
        blit(src, srcW, srcH, drawW, drawH, mid, drawW, drawH, 0, 0);
        int ySize = dstW * dstH;
        byte[] dst = new byte[ySize + (dstW / 2) * (dstH / 2) * 2];
        Arrays.fill(dst, ySize, dst.length, (byte) 128);
        // Y 平面裁切拷贝
        for (int y = 0; y < dstH; y++) {
            System.arraycopy(mid, (offY + y) * drawW + offX, dst, y * dstW, dstW);
        }
        // UV 平面裁切拷贝（NV21 交织）
        int midYSize = drawW * drawH;
        int dstYSize = dstW * dstH;
        int cw = dstW / 2;
        int ch = dstH / 2;
        for (int y = 0; y < ch; y++) {
            int srcRow = midYSize + ((offY / 2 + y) * (drawW / 2) + offX / 2) * 2;
            int dstRow = dstYSize + y * dstW;
            System.arraycopy(mid, srcRow, dst, dstRow, cw * 2);
        }
        return dst;
    }

    /** 拉伸铺满（非等比）。 */
    private static byte[] resize(byte[] src, int srcW, int srcH, int dstW, int dstH) {
        int ySize = dstW * dstH;
        byte[] dst = new byte[ySize + (dstW / 2) * (dstH / 2) * 2];
        Arrays.fill(dst, ySize, dst.length, (byte) 128);
        blit(src, srcW, srcH, dstW, dstH, dst, dstW, dstH, 0, 0);
        return dst;
    }

    /** 最近邻缩放 + 放置到 (offX, offY)，NV21 全平面。 */
    private static void blit(byte[] src, int srcW, int srcH, int drawW, int drawH,
                             byte[] dst, int dstW, int dstH, int offX, int offY) {
        int srcYSize = srcW * srcH;
        for (int y = 0; y < drawH; y++) {
            int sy = y * srcH / drawH;
            if (sy >= srcH) {
                sy = srcH - 1;
            }
            int dstRow = (offY + y) * dstW + offX;
            int srcRow = sy * srcW;
            for (int x = 0; x < drawW; x++) {
                int sx = x * srcW / drawW;
                if (sx >= srcW) {
                    sx = srcW - 1;
                }
                dst[dstRow + x] = src[srcRow + sx];
            }
        }
        int cw = drawW / 2;
        int ch = drawH / 2;
        int srcCStride = srcW / 2;
        int dstCStride = dstW / 2;
        int dstYSize = dstW * dstH;
        for (int y = 0; y < ch; y++) {
            int sy = y * (srcH / 2) / ch;
            if (sy >= srcH / 2) {
                sy = srcH / 2 - 1;
            }
            int dstUv = dstYSize + ((offY / 2 + y) * dstCStride + offX / 2) * 2;
            int srcUv = srcYSize + sy * srcCStride * 2;
            for (int x = 0; x < cw; x++) {
                int sx = x * srcCStride / cw;
                if (sx >= srcCStride) {
                    sx = srcCStride - 1;
                }
                int from = srcUv + sx * 2;
                int to = dstUv + x * 2;
                dst[to] = src[from];
                dst[to + 1] = src[from + 1];
            }
        }
    }
}