package com.example.lancam;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    private static final int REQ_CAMERA = 10;
    private static final int PORT = 4747;
    private static final long FRAME_INTERVAL_MS = 100;

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private TextureView preview;
    private TextView status;
    private Camera camera;
    private Camera.Size previewSize;
    private boolean front;
    private long lastFrameTime;
    private MjpegServer server;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        server = new MjpegServer(PORT, latestFrame);
        server.start();

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            preview.setSurfaceTextureListener(this);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        updateStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        preview = new TextureView(this);
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        status = new TextView(this);
        status.setTextSize(15f);
        bar.addView(status, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button swap = new Button(this);
        swap.setText("Trocar câmera");
        swap.setOnClickListener(v -> {
            front = !front;
            restartCamera();
        });
        bar.addView(swap);
        root.addView(bar);
        setContentView(root);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            preview.setSurfaceTextureListener(this);
            if (preview.isAvailable()) openCamera(preview.getSurfaceTexture());
        } else if (requestCode == REQ_CAMERA) {
            status.setText("Permita acesso à câmera para iniciar o LanCam.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preview != null && preview.isAvailable() && camera == null
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(preview.getSurfaceTexture());
        }
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        if (server != null) server.shutdown();
        super.onDestroy();
    }

    private void restartCamera() {
        if (!preview.isAvailable()) return;
        releaseCamera();
        openCamera(preview.getSurfaceTexture());
    }

    private void openCamera(SurfaceTexture surface) {
        try {
            int id = chooseCamera(front);
            if (id < 0) throw new IllegalStateException("Nenhuma câmera encontrada");
            camera = Camera.open(id);
            Camera.Parameters params = camera.getParameters();
            previewSize = choosePreviewSize(params.getSupportedPreviewSizes());
            params.setPreviewSize(previewSize.width, previewSize.height);
            params.setPreviewFormat(ImageFormat.NV21);
            List<String> focus = params.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            camera.setParameters(params);
            camera.setDisplayOrientation(displayOrientation(id));
            surface.setDefaultBufferSize(previewSize.width, previewSize.height);
            camera.setPreviewTexture(surface);
            camera.setPreviewCallback(this);
            camera.startPreview();
            updateStatus();
        } catch (Exception e) {
            releaseCamera();
            status.setText("Falha ao abrir a câmera: " + e.getMessage());
        }
    }

    private int chooseCamera(boolean wantFront) {
        int wanted = wantFront ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        int fallback = -1;
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (fallback < 0) fallback = i;
            if (info.facing == wanted) return i;
        }
        return fallback;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        long target = 1280L * 720L;
        long delta = Math.abs((long) best.width * best.height - target);
        for (Camera.Size s : sizes) {
            long d = Math.abs((long) s.width * s.height - target);
            if (d < delta) {
                best = s;
                delta = d;
            }
        }
        return best;
    }

    private int displayOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == 1 ? 90 : rotation == 2 ? 180 : rotation == 3 ? 270 : 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera source) {
        if (previewSize == null || data == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastFrameTime < FRAME_INTERVAL_MS) return;
        lastFrameTime = now;
        try {
            YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
                    previewSize.width, previewSize.height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (yuv.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 70, out)) {
                latestFrame.set(out.toByteArray());
            }
        } catch (RuntimeException ignored) { }
    }

    private void releaseCamera() {
        if (camera == null) return;
        try { camera.setPreviewCallback(null); } catch (Exception ignored) { }
        try { camera.stopPreview(); } catch (Exception ignored) { }
        try { camera.release(); } catch (Exception ignored) { }
        camera = null;
    }

    private void updateStatus() {
        String ip = localIpv4();
        if (ip == null) {
            status.setText("Conecte celular e PC à mesma rede Wi‑Fi.");
        } else {
            status.setText("No PC: http://" + ip + ":" + PORT + "/");
        }
    }

    private static String localIpv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openCamera(surface); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { releaseCamera(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    private static final class MjpegServer extends Thread {
        private final int port;
        private final AtomicReference<byte[]> frame;
        private volatile boolean running = true;
        private ServerSocket serverSocket;

        MjpegServer(int port, AtomicReference<byte[]> frame) {
            super("LanCamServer");
            this.port = port;
            this.frame = frame;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket socket = serverSocket.accept();
                    Thread t = new Thread(() -> handle(socket));
                    t.setDaemon(true);
                    t.start();
                }
            } catch (IOException ignored) { }
        }

        void shutdown() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        }

        private void handle(Socket socket) {
            try (Socket s = socket) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        s.getInputStream(), StandardCharsets.ISO_8859_1));
                String request = reader.readLine();
                if (request == null) return;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                String[] parts = request.split(" ");
                String path = parts.length > 1 ? parts[1] : "/";
                if (path.startsWith("/stream")) stream(s.getOutputStream());
                else if (path.startsWith("/shot.jpg")) snapshot(s.getOutputStream());
                else index(s.getOutputStream());
            } catch (IOException ignored) { }
        }

        private void index(OutputStream out) throws IOException {
            String html = "<!doctype html><html><meta name='viewport' content='width=device-width'>" +
                    "<title>LanCam</title><body style='margin:0;background:#111;color:white;text-align:center;font-family:sans-serif'>" +
                    "<h2>LanCam</h2><img src='/stream' style='max-width:100%;height:auto'>" +
                    "<p><a style='color:#9cf' href='/shot.jpg'>Foto atual</a></p></body></html>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " +
                    body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n");
            out.write(body);
            out.flush();
        }

        private void snapshot(OutputStream out) throws IOException {
            byte[] jpg = frame.get();
            if (jpg == null) {
                byte[] body = "Camera iniciando".getBytes(StandardCharsets.UTF_8);
                write(out, "HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain\r\nContent-Length: " +
                        body.length + "\r\nConnection: close\r\n\r\n");
                out.write(body);
            } else {
                write(out, "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " +
                        jpg.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n");
                out.write(jpg);
            }
            out.flush();
        }

        private void stream(OutputStream out) throws IOException {
            write(out, "HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                    "Cache-Control: no-store\r\nConnection: close\r\n\r\n");
            byte[] last = null;
            while (running) {
                byte[] jpg = frame.get();
                if (jpg != null && jpg != last) {
                    write(out, "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + jpg.length + "\r\n\r\n");
                    out.write(jpg);
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    last = jpg;
                }
                SystemClock.sleep(25);
            }
        }

        private static void write(OutputStream out, String text) throws IOException {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
