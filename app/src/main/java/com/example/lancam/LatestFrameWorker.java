package com.example.lancam;

/** One processing frame and at most one pending frame, replaced by newer input. */
final class LatestFrameWorker<T> implements AutoCloseable {
    interface Processor<T> { void accept(T frame); }
    private final Processor<T> process;
    private final Thread thread;
    private T pending;
    private boolean closed;

    LatestFrameWorker(Processor<T> process) {
        this.process = process;
        thread = new Thread(this::run, "LanCamEncoder");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized T submit(T frame) {
        if (closed) return frame;
        T discarded = pending;
        pending = frame;
        notifyAll();
        return discarded;
    }

    synchronized T clearPending() {
        T discarded = pending;
        pending = null;
        return discarded;
    }

    @Override public synchronized void close() {
        closed = true;
        pending = null;
        notifyAll();
    }

    private void run() {
        while (true) {
            T frame;
            synchronized (this) {
                while (!closed && pending == null) {
                    try { wait(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (closed) return;
                frame = pending;
                pending = null;
            }
            process.accept(frame);
        }
    }
}
