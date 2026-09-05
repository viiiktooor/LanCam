package com.example.lancam;

/** Monotonic, phase-preserving limiter. Used only by the camera callback thread. */
final class FramePacer {
    private long next;
    private int previousFps;

    void reset() { previousFps = 0; }

    boolean accept(long now, int fps) {
        fps = Math.max(1, fps);
        long interval = 1_000_000_000L / fps;
        if (previousFps != fps) {
            previousFps = fps;
            next = now;
        }
        // Small callback jitter must not turn a 30 fps camera into a 15 fps stream.
        long tolerance = Math.min(2_000_000L, interval / 10);
        if (now + tolerance < next) return false;
        if (now - next >= interval) {
            next += ((now - next) / interval + 1) * interval;
        } else {
            next += interval;
        }
        return true;
    }
}
