package io.github.hpjxiangbabang.vcam;

public class FrameFitterTest {
    public static void main(String[] args) {
        byte[] same = solid(4, 2, (byte) 40, (byte) 90, (byte) 110);
        byte[] unchanged = FrameFitter.fitNv21(same, 4, 2, 4, 2);
        check(unchanged == same, "same size should keep the original buffer");

        byte[] wide = FrameFitter.fitNv21(solid(2, 2, (byte) 255, (byte) 128, (byte) 128), 2, 2, 6, 2);
        check(wide != null && wide.length == 6 * 2 * 3 / 2, "wide buffer length");
        check(wide[0] == 0 && wide[1] == 0, "left bar is black");
        check(wide[2] == (byte) 255 && wide[3] == (byte) 255, "center keeps the picture");
        check(wide[4] == 0 && wide[5] == 0, "right bar is black");

        byte[] tall = FrameFitter.fitNv21(solid(2, 2, (byte) 200, (byte) 128, (byte) 128), 2, 2, 2, 6);
        check(tall[0] == 0 && tall[1] == 0, "top bar is black");
        check(tall[2 * 2 + 0] == (byte) 200, "middle row keeps the picture");
        check(tall[4 * 2] == 0, "bottom bar is black");

        byte[] phone = FrameFitter.fitNv21(solid(1920, 1080, (byte) 180, (byte) 128, (byte) 128), 1920, 1080, 2340, 1080);
        check(phone.length == 2340 * 1080 * 3 / 2, "2340x1080 buffer");
        check(phone[0] == 0, "side bar");
        check(phone[210] == (byte) 180, "picture starts after the side bar");
        if (failed) {
            throw new RuntimeException("fit checks failed");
        }
        System.out.println("fit checks ok");
    }

    private static boolean failed;

    private static void check(boolean ok, String message) {
        if (!ok) {
            failed = true;
            System.out.println("FAIL " + message);
        }
    }

    private static byte[] solid(int width, int height, byte y, byte v, byte u) {
        byte[] data = new byte[width * height * 3 / 2];
        for (int i = 0; i < width * height; i++) {
            data[i] = y;
        }
        for (int i = width * height; i < data.length; i += 2) {
            data[i] = v;
            data[i + 1] = u;
        }
        return data;
    }
}
