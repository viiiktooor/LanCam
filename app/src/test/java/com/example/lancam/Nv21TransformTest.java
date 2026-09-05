package com.example.lancam;

import java.util.Arrays;

final class Nv21TransformTest {
    static void run() {
        byte[] source = {0,1,2,3,4,5,6,7,100,101,102,103};
        byte[][] expected = {
                {0,1,2,3,4,5,6,7,100,101,102,103},
                {4,0,5,1,6,2,7,3,100,101,102,103},
                {7,6,5,4,3,2,1,0,102,103,100,101},
                {3,7,2,6,1,5,0,4,102,103,100,101}
        };
        for (int rotation = 0; rotation < 360; rotation += 90) {
            for (boolean mirror : new boolean[]{false, true}) {
                byte[] wanted = expected[rotation / 90].clone();
                int width = rotation % 180 == 0 ? 4 : 2;
                int height = rotation % 180 == 0 ? 2 : 4;
                if (mirror) {
                    mirrorRows(wanted, 0, width, height, 1);
                    mirrorRows(wanted, 8, width / 2, height / 2, 2);
                }
                byte[] actual = new byte[12];
                Arrays.fill(actual, (byte) -1);
                Nv21Transform.transform(source, actual, 4, 2, rotation, mirror);
                check(Arrays.equals(actual, wanted), "Wrong rotation/mirror " + rotation + "/" + mirror);
            }
        }
        check(Arrays.equals(source, expected[0]), "Source modified");
        // Exercise both dimensions of chroma on a non-square image.
        byte[] bigger = new byte[8 * 6 * 3 / 2];
        for (int i = 0; i < bigger.length; i++) bigger[i] = (byte) i;
        byte[] rotated = new byte[bigger.length];
        byte[] restored = new byte[bigger.length];
        for (int rotation : new int[]{0,90,180,270}) {
            int width = rotation % 180 == 0 ? 8 : 6;
            int height = rotation % 180 == 0 ? 6 : 8;
            Nv21Transform.transform(bigger, rotated, 8, 6, rotation, false);
            Nv21Transform.transform(rotated, restored, width, height, (360-rotation)%360, false);
            check(Arrays.equals(bigger, restored), "Rotation inverse " + rotation);
            Nv21Transform.transform(bigger, rotated, 8, 6, rotation, true);
            Nv21Transform.transform(rotated, restored, width, height, 0, true);
            Nv21Transform.transform(restored, rotated, width, height, (360-rotation)%360, false);
            check(Arrays.equals(bigger, rotated), "Mirror/rotation inverse " + rotation);
        }
        expectInvalid(() -> Nv21Transform.transform(source, source, 4, 2, 0, false));
        expectInvalid(() -> Nv21Transform.transform(source, new byte[11], 4, 2, 0, false));
        expectInvalid(() -> Nv21Transform.transform(source, new byte[12], 3, 2, 0, false));
        expectInvalid(() -> Nv21Transform.transform(source, new byte[12], 4, 2, 45, false));
        System.out.println("PASS: NV21 fixtures for 8 rotation/mirror combinations, chroma round trips and invalid buffers");
    }

    private static void mirrorRows(byte[] bytes, int offset, int width, int height, int stride) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                for (int channel = 0; channel < stride; channel++) {
                    int a = offset + (y * width + x) * stride + channel;
                    int b = offset + (y * width + width - 1 - x) * stride + channel;
                    byte temp = bytes[a]; bytes[a] = bytes[b]; bytes[b] = temp;
                }
            }
        }
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid NV21 input to be rejected");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
