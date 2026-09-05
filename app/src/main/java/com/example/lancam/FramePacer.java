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
        // Camera callback delivery can vary by several milliseconds even at a
        // steady sensor cadence. Round to the nearest slot, preserving the phase
        // and long-term cap without discarding on-time sensor frames as "early".
        long tolerance = interval / 2;
        if (now + tolerance < next) return false;
        if (now - next >= interval) {
            next += ((now - next) / interval + 1) * interval;
        } else {
            next += interval;
        }
        return true;
    }
}
