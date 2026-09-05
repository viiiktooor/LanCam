package com.example.lancam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Standalone JVM regression tests: run with javac/java, no Android device needed. */
public final class FramePipelineTest {
    public static void main(String[] args) throws Exception {
        Nv21TransformTest.run();
        jitterDoesNotHalveFps();
        lowerTargetsRemainLimited();
        changingTargetAndRestartResetDeadline();
        longPauseDoesNotCreateBurst();
        pendingFrameIsReplaced();
        clearingPendingAndClosingDiscardWork();
        System.out.println("PASS: 6 Android frame pipeline regression tests");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        check(latch.await(5, TimeUnit.SECONDS), "Worker timed out");
    }

    private static void jitterDoesNotHalveFps() {
        FramePacer pacer = new FramePacer();
        int accepted = 0;
        for (int i = 0; i < 300; i++) {
            long jitter = i % 3 == 1 ? -1_500_000 : i % 3 == 2 ? 1_500_000 : 0;
            if (pacer.accept(1_000_000_000L + i * 1_000_000_000L / 30 + jitter, 30)) accepted++;
        }
        check(accepted == 300, "30 fps jitter discarded frames: " + accepted);
    }

    private static void lowerTargetsRemainLimited() {
        for (int fps : new int[]{5, 10, 15, 20, 30}) {
            FramePacer pacer = new FramePacer();
            int accepted = 0;
            for (int i = 0; i < 300; i++) {
                if (pacer.accept(i * 1_000_000_000L / 30, fps)) accepted++;
            }
            check(Math.abs(accepted - fps * 10) <= 1, "Wrong limit for " + fps + ": " + accepted);
        }
    }

    private static void changingTargetAndRestartResetDeadline() {
        FramePacer pacer = new FramePacer();
        check(pacer.accept(0, 5), "First frame");
        check(!pacer.accept(10_000_000, 5), "5 fps must wait");
        check(pacer.accept(11_000_000, 30), "FPS change must apply immediately");
        pacer.reset();
        check(pacer.accept(12_000_000, 30), "Restart must reset deadline");
    }

    private static void longPauseDoesNotCreateBurst() {
        FramePacer pacer = new FramePacer();
        pacer.accept(0, 30);
        check(pacer.accept(10_000_000_000L, 30), "Resume after long pause");
        check(!pacer.accept(10_001_000_000L, 30), "No catch-up burst");
    }

    private static void pendingFrameIsReplaced() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        List<Integer> processed = new ArrayList<>();
        try (LatestFrameWorker<Integer> worker = new LatestFrameWorker<>(frame -> {
            processed.add(frame);
            if (frame == 1) {
                entered.countDown();
                try { await(release); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
            completed.countDown();
        })) {
            try {
                check(worker.submit(1) == null, "First submit");
                await(entered);
                check(worker.submit(2) == null, "First pending");
                for (int i = 3; i <= 1000; i++) {
                    check(worker.submit(i) == i - 1, "Discarded buffer must return to caller");
                }
            } finally { release.countDown(); }
            await(completed);
            check(processed.equals(Arrays.asList(1, 1000)), "Only first and latest should be processed");
        }
    }

    private static void clearingPendingAndClosingDiscardWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        List<Integer> processed = new ArrayList<>();
        LatestFrameWorker<Integer> worker = new LatestFrameWorker<>(frame -> {
            processed.add(frame);
            entered.countDown();
            try { await(release); } catch (InterruptedException e) { throw new AssertionError(e); }
            completed.countDown();
        });
        try {
            worker.submit(1);
            await(entered);
            worker.submit(2);
            check(worker.clearPending() == 2, "Camera release must discard pending frame");
            check(worker.clearPending() == null, "Pending must be empty");
            worker.submit(3);
            worker.close();
            check(worker.submit(4) == 4, "Closed worker rejects input");
        } finally { worker.close(); release.countDown(); }
        await(completed);
        check(processed.equals(Arrays.asList(1)), "Closed worker must not process queued frames");
    }
}
