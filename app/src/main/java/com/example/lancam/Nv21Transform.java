package com.example.lancam;

/** Rotates NV21 luma and VU pairs together, then mirrors in output coordinates. */
final class Nv21Transform {
    static void transform(byte[] source, byte[] target, int width, int height,
                          int rotation, boolean mirror) {
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0
                || (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)) {
            throw new IllegalArgumentException("Invalid NV21 dimensions or rotation");
        }
        long required = (long) width * height * 3 / 2;
        if (source == target || source.length < required || target.length < required) {
            throw new IllegalArgumentException("NV21 requires distinct, complete buffers");
        }
        copyPlane(source, target, 0, width, height, 1, rotation, mirror);
        copyPlane(source, target, width * height, width / 2, height / 2, 2, rotation, mirror);
    }

    private static void copyPlane(byte[] source, byte[] target, int offset, int width,
                                  int height, int bytesPerPixel, int rotation, boolean mirror) {
        int outputWidth = rotation == 90 || rotation == 270 ? height : width;
        int input = offset;
        for (int y = 0; y < height; y++) {
            int outputX, outputY, stepX, stepY;
            switch (rotation) {
                case 90: outputX = height - 1 - y; outputY = 0; stepX = 0; stepY = 1; break;
                case 180: outputX = width - 1; outputY = height - 1 - y; stepX = -1; stepY = 0; break;
                case 270: outputX = y; outputY = width - 1; stepX = 0; stepY = -1; break;
                default: outputX = 0; outputY = y; stepX = 1; stepY = 0;
            }
            if (mirror) {
                outputX = outputWidth - 1 - outputX;
                stepX = -stepX;
            }
            int output = offset + (outputY * outputWidth + outputX) * bytesPerPixel;
            int step = (stepY * outputWidth + stepX) * bytesPerPixel;
            if (bytesPerPixel == 1) {
                for (int x = 0; x < width; x++, output += step) target[output] = source[input++];
            } else {
                for (int x = 0; x < width; x++, output += step) {
                    target[output] = source[input++];
                    target[output + 1] = source[input++];
                }
            }
        }
    }
}
