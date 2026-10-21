package io.github.hpjxiangbabang.vcam;

import java.util.Arrays;

/**
 * 把一段 NV21 画面放进另一个宽高里。比例保持不变，放不下的地方是黑边。
 * 宽高相同则原样返回，不重新画。
 */
public final class FrameFitter {
    private FrameFitter() {
    }

    public static byte[] fitNv21(byte[] src, int srcW, int srcH, int dstW, int dstH) {
        if (src == null || srcW < 2 || srcH < 2 || dstW < 2 || dstH < 2) {
            return null;
        }
        int srcSize = srcW * srcH * 3 / 2;
        if (src.length < srcSize) {
            return null;
        }
        if (srcW == dstW && srcH == dstH) {
            return src;
        }

        int drawW = dstW;
        int drawH = dstW * srcH / srcW;
        if (drawH > dstH || drawH < 2) {
            drawH = dstH;
            drawW = dstH * srcW / srcH;
        }
        drawW &= ~1;
        drawH &= ~1;
        if (drawW < 2) {
            drawW = 2;
        }
        if (drawH < 2) {
            drawH = 2;
        }
        if (drawW > dstW) {
            drawW = dstW & ~1;
        }
        if (drawH > dstH) {
            drawH = dstH & ~1;
        }
        if (drawW < 2 || drawH < 2) {
            return null;
        }

        int offX = ((dstW - drawW) / 2) & ~1;
        int offY = ((dstH - drawH) / 2) & ~1;
        if (offX < 0) {
            offX = 0;
        }
        if (offY < 0) {
            offY = 0;
        }

        int ySize = dstW * dstH;
        int uvSize = (dstW / 2) * (dstH / 2) * 2;
        byte[] dst = new byte[ySize + uvSize];
        Arrays.fill(dst, ySize, dst.length, (byte) 128);

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

        int chromaDrawW = drawW / 2;
        int chromaDrawH = drawH / 2;
        int chromaOffX = offX / 2;
        int chromaOffY = offY / 2;
        int dstChromaStride = dstW / 2;
        int srcChromaStride = srcW / 2;
        int srcChromaH = srcH / 2;
        for (int y = 0; y < chromaDrawH; y++) {
            int sy = y * srcChromaH / chromaDrawH;
            if (sy >= srcChromaH) {
                sy = srcChromaH - 1;
            }
            int dstUv = ySize + ((chromaOffY + y) * dstChromaStride + chromaOffX) * 2;
            int srcUv = srcYSize + sy * srcChromaStride * 2;
            for (int x = 0; x < chromaDrawW; x++) {
                int sx = x * srcChromaStride / chromaDrawW;
                if (sx >= srcChromaStride) {
                    sx = srcChromaStride - 1;
                }
                int from = srcUv + sx * 2;
                int to = dstUv + x * 2;
                dst[to] = src[from];
                dst[to + 1] = src[from + 1];
            }
        }
        return dst;
    }
}
