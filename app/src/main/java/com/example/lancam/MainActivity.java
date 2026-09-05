package com.example.lancam;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    private static final int REQ_CAMERA = 10;
    private static final int PORT = 4747;

    private static final String[] RESOLUTION_LABELS = {"640×480", "1280×720", "1920×1080"};
    private static final int[][] RESOLUTIONS = {{640, 480}, {1280, 720}, {1920, 1080}};
    private static final String[] FPS_LABELS = {"5 fps", "10 fps", "15 fps", "20 fps", "30 fps"};
    private static final int[] FPS_VALUES = {5, 10, 15, 20, 30};
    private static final String[] QUALITY_LABELS = {"50%", "70%", "85%", "95%"};
    private static final int[] QUALITY_VALUES = {50, 70, 85, 95};

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private TextureView preview;
    private TextView status;
    private TextView details;
    private Button torchButton;
    private Button mirrorButton;
    private Camera camera;
    private Camera.Size previewSize;
    private int currentCameraId = -1;
    private boolean front;
    private boolean torchEnabled;
    private boolean mirrorFront = true;
    private int targetWidth = 1280;
    private int targetHeight = 720;
    private volatile int targetFps = 10;
    private volatile int jpegQuality = 70;
    private volatile int streamRotation;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final FramePacer framePacer = new FramePacer();
    private LatestFrameWorker<RawFrame> encoder;
    private long cameraGeneration;
    private long statsStarted;
    private int capturedFrames;
    private int encodedFrames;
    private int replacedFrames;
    private long encodingNanos;
    private volatile String performanceJson = "\"captureFps\":0,\"encodedFps\":0,\"encodeMs\":0,\"replacedFrames\":0";
    private String performanceText = "medindo FPS…";
    private MjpegServer server;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        encoder = new LatestFrameWorker<>(this::encodeFrame);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        server = new MjpegServer(PORT, latestFrame, this::statusJson);
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
        root.setPadding(10, 10, 10, 10);

        preview = new TextureView(this);
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout infoBar = new LinearLayout(this);
        infoBar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setTextSize(14f);
        details = new TextView(this);
        details.setTextSize(12f);
        textBox.addView(status);
        textBox.addView(details);
        infoBar.addView(textBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button swap = new Button(this);
        swap.setText("Trocar câmera");
        swap.setOnClickListener(v -> {
            front = !front;
            torchEnabled = false;
            restartCamera();
        });
        infoBar.addView(swap);
        root.addView(infoBar);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        Spinner resolution = makeSpinner(RESOLUTION_LABELS, 1);
        resolution.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void selected(int position) {
                int newW = RESOLUTIONS[position][0];
                int newH = RESOLUTIONS[position][1];
                if (newW == targetWidth && newH == targetHeight) return;
                targetWidth = newW;
                targetHeight = newH;
                restartCamera();
            }
        });
        controls.addView(resolution, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Spinner fps = makeSpinner(FPS_LABELS, 1);
        fps.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void selected(int position) {
                int selectedFps = FPS_VALUES[position];
                if (selectedFps == targetFps) return;
                targetFps = selectedFps;
                restartCamera();
            }
        });
        controls.addView(fps, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Spinner quality = makeSpinner(QUALITY_LABELS, 1);
        quality.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void selected(int position) {
                jpegQuality = QUALITY_VALUES[position];
                updateStatus();
            }
        });
        controls.addView(quality, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        torchButton = new Button(this);
        torchButton.setText("Flash: off");
        torchButton.setOnClickListener(v -> {
            torchEnabled = !torchEnabled;
            applyTorch();
        });
        controls.addView(torchButton);

        mirrorButton = new Button(this);
        mirrorButton.setText("Espelho: sim");
        mirrorButton.setOnClickListener(v -> {
            mirrorFront = !mirrorFront;
            mirrorButton.setText(mirrorFront ? "Espelho: sim" : "Espelho: não");
            updateStatus();
        });
        controls.addView(mirrorButton);

        root.addView(controls);
        setContentView(root);
    }

    private Spinner makeSpinner(String[] items, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        return spinner;
    }

    private abstract static class SimpleSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
            selected(position);
        }
        @Override public void onNothingSelected(AdapterView<?> parent) { }
        public abstract void selected(int position);
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
        if (encoder != null) encoder.close();
        if (server != null) server.shutdown();
        super.onDestroy();
    }

    private void restartCamera() {
        if (preview == null || !preview.isAvailable()) {
            updateStatus();
            return;
        }
        releaseCamera();
        openCamera(preview.getSurfaceTexture());
    }

    private void openCamera(SurfaceTexture surface) {
        if (camera != null) return;
        try {
            int id = chooseCamera(front);
            if (id < 0) throw new IllegalStateException("Nenhuma câmera encontrada");
            currentCameraId = id;
            camera = Camera.open(id);
            Camera.Parameters params = camera.getParameters();
            previewSize = choosePreviewSize(params.getSupportedPreviewSizes(), targetWidth, targetHeight);
            params.setPreviewSize(previewSize.width, previewSize.height);
            params.setPreviewFormat(ImageFormat.NV21);
            setClosestFpsRange(params, targetFps);

            List<String> focus = params.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            applyTorchToParameters(params);
            camera.setParameters(params);

            int displayRotation = displayOrientation(id);
            streamRotation = rawFrameRotation(id);
            camera.setDisplayOrientation(displayRotation);
            surface.setDefaultBufferSize(previewSize.width, previewSize.height);
            camera.setPreviewTexture(surface);
            framePacer.reset();
            resetPerformance();
            camera.setPreviewCallbackWithBuffer(this);
            int bufferSize = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            for (int i = 0; i < 3; i++) camera.addCallbackBuffer(new byte[bufferSize]);
            camera.startPreview();
            refreshTorchButton();
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

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes, int wantedW, int wantedH) {
        Camera.Size best = sizes.get(0);
        double wantedRatio = (double) wantedW / wantedH;
        double bestScore = sizeScore(best, wantedW, wantedH, wantedRatio);
        for (Camera.Size s : sizes) {
            double score = sizeScore(s, wantedW, wantedH, wantedRatio);
            if (score < bestScore) {
                best = s;
                bestScore = score;
            }
        }
        return best;
    }

    private double sizeScore(Camera.Size size, int wantedW, int wantedH, double wantedRatio) {
        long wantedArea = (long) wantedW * wantedH;
        long area = (long) size.width * size.height;
        double areaError = Math.abs(area - wantedArea) / (double) wantedArea;
        double ratio = (double) Math.max(size.width, size.height) / Math.min(size.width, size.height);
        double normalizedWantedRatio = Math.max(wantedRatio, 1.0 / wantedRatio);
        double ratioError = Math.abs(ratio - normalizedWantedRatio);
        return areaError + ratioError * 4.0;
    }

    private void setClosestFpsRange(Camera.Parameters params, int fps) {
        try {
            List<int[]> ranges = params.getSupportedPreviewFpsRange();
            if (ranges == null || ranges.isEmpty()) return;
            int wanted = fps * 1000;
            int[] best = ranges.get(0);
            long bestScore = Long.MAX_VALUE;
            for (int[] range : ranges) {
                long score;
                if (wanted >= range[0] && wanted <= range[1]) {
                    score = (long) (range[1] - range[0]) + Math.abs(range[1] - wanted);
                } else {
                    score = Math.min(Math.abs((long) range[0] - wanted), Math.abs((long) range[1] - wanted)) + 1_000_000L;
                }
                if (score < bestScore) {
                    best = range;
                    bestScore = score;
                }
            }
            params.setPreviewFpsRange(best[0], best[1]);
        } catch (RuntimeException ignored) { }
    }

    private int displayOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = displayDegrees();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private int rawFrameRotation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = displayDegrees();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private int displayDegrees() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        return rotation == 1 ? 90 : rotation == 2 ? 180 : rotation == 3 ? 270 : 0;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera source) {
        if (source != camera || previewSize == null || data == null) return;
        capturedFrames++;
        refreshPerformance();
        if (!framePacer.accept(SystemClock.elapsedRealtimeNanos(), targetFps)) {
            returnBuffer(data, source, cameraGeneration);
            return;
        }
        RawFrame frame = new RawFrame(data, source, cameraGeneration, previewSize.width,
                previewSize.height, streamRotation, front && mirrorFront, jpegQuality);
        RawFrame discarded = encoder.submit(frame);
        if (discarded != null) {
            replacedFrames++;
            returnBuffer(discarded.data, discarded.source, discarded.generation);
        }
    }

    private static final class RawFrame {
        final byte[] data;
        final Camera source;
        final long generation;
        final int width, height, rotation, quality;
        final boolean mirror;
        RawFrame(byte[] data, Camera source, long generation, int width, int height,
                 int rotation, boolean mirror, int quality) {
            this.data = data;
            this.source = source;
            this.generation = generation;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.mirror = mirror;
            this.quality = quality;
        }
    }

    private void encodeFrame(RawFrame frame) {
        long started = SystemClock.elapsedRealtimeNanos();
        byte[] result = null;
        try {
            int firstPassQuality = (frame.rotation != 0 || frame.mirror) ? 92 : frame.quality;
            YuvImage yuv = new YuvImage(frame.data, ImageFormat.NV21,
                    frame.width, frame.height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (yuv.compressToJpeg(new Rect(0, 0, frame.width, frame.height), firstPassQuality, out)) {
                result = out.toByteArray();
                if (frame.rotation != 0 || frame.mirror) {
                    result = transformJpeg(result, frame.rotation, frame.mirror, frame.quality);
                }
            }
        } catch (RuntimeException e) {
            Log.w("LanCam", "Falha ao converter quadro", e);
        } finally {
            final byte[] jpg = result;
            final long duration = SystemClock.elapsedRealtimeNanos() - started;
            // Camera API calls and publication stay on its owning thread. An old
            // encoder completion cannot publish or return a buffer to a new camera.
            mainHandler.post(() -> {
                if (camera == frame.source && cameraGeneration == frame.generation) {
                    if (jpg != null) {
                        latestFrame.set(jpg);
                        encodedFrames++;
                        encodingNanos += duration;
                    }
                    returnBuffer(frame.data, frame.source, frame.generation);
                }
            });
        }
    }

    private void returnBuffer(byte[] data, Camera source, long generation) {
        if (source != camera || generation != cameraGeneration) return;
        try { source.addCallbackBuffer(data); }
        catch (RuntimeException e) { Log.w("LanCam", "Falha ao devolver buffer", e); }
    }

    private void resetPerformance() {
        statsStarted = SystemClock.elapsedRealtimeNanos();
        capturedFrames = encodedFrames = replacedFrames = 0;
        encodingNanos = 0;
        performanceText = "medindo FPS…";
        performanceJson = "\"captureFps\":0,\"encodedFps\":0,\"encodeMs\":0,\"replacedFrames\":0";
    }

    private void refreshPerformance() {
        long now = SystemClock.elapsedRealtimeNanos();
        long elapsed = now - statsStarted;
        if (elapsed < 1_000_000_000L) return;
        double captureFps = capturedFrames * 1_000_000_000.0 / elapsed;
        double encodedFps = encodedFrames * 1_000_000_000.0 / elapsed;
        double encodeMs = encodedFrames == 0 ? 0 : encodingNanos / (encodedFrames * 1_000_000.0);
        performanceText = String.format(Locale.US, "câmera %.1f · vídeo %.1f FPS · %.1f ms/quadro",
                captureFps, encodedFps, encodeMs);
        performanceJson = String.format(Locale.US,
                "\"captureFps\":%.1f,\"encodedFps\":%.1f,\"encodeMs\":%.1f,\"replacedFrames\":%d",
                captureFps, encodedFps, encodeMs, replacedFrames);
        capturedFrames = encodedFrames = replacedFrames = 0;
        encodingNanos = 0;
        statsStarted = now;
        updateStatus();
    }

    private byte[] transformJpeg(byte[] jpg, int rotation, boolean mirror, int quality) {
        Bitmap src = null;
        Bitmap transformed = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            src = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, options);
            if (src == null) return jpg;
            Matrix matrix = new Matrix();
            if (rotation != 0) matrix.postRotate(rotation);
            if (mirror) matrix.postScale(-1f, 1f);
            transformed = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformed.compress(Bitmap.CompressFormat.JPEG, quality, out);
            return out.toByteArray();
        } catch (RuntimeException e) {
            return jpg;
        } finally {
            if (transformed != null && transformed != src && !transformed.isRecycled()) transformed.recycle();
            if (src != null && !src.isRecycled()) src.recycle();
        }
    }

    private void applyTorch() {
        if (camera == null) {
            torchEnabled = false;
            refreshTorchButton();
            return;
        }
        try {
            Camera.Parameters params = camera.getParameters();
            if (!supportsTorch(params)) {
                torchEnabled = false;
            }
            applyTorchToParameters(params);
            camera.setParameters(params);
        } catch (RuntimeException e) {
            torchEnabled = false;
        }
        refreshTorchButton();
        updateStatus();
    }

    private void applyTorchToParameters(Camera.Parameters params) {
        if (!supportsTorch(params)) {
            torchEnabled = false;
            return;
        }
        params.setFlashMode(torchEnabled ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
    }

    private boolean supportsTorch(Camera.Parameters params) {
        List<String> modes = params.getSupportedFlashModes();
        return modes != null && modes.contains(Camera.Parameters.FLASH_MODE_TORCH)
                && modes.contains(Camera.Parameters.FLASH_MODE_OFF);
    }

    private void refreshTorchButton() {
        if (torchButton == null) return;
        boolean supported = false;
        try {
            supported = camera != null && supportsTorch(camera.getParameters());
        } catch (RuntimeException ignored) { }
        torchButton.setEnabled(supported);
        torchButton.setText(torchEnabled && supported ? "Flash: on" : "Flash: off");
    }

    private void releaseCamera() {
        cameraGeneration++;
        if (encoder != null) encoder.clearPending();
        latestFrame.set(null);
        framePacer.reset();
        resetPerformance();
        if (camera == null) return;
        try { camera.setPreviewCallbackWithBuffer(null); } catch (Exception ignored) { }
        try { camera.stopPreview(); } catch (Exception ignored) { }
        try { camera.release(); } catch (Exception ignored) { }
        camera = null;
        currentCameraId = -1;
        previewSize = null;
    }

    private void updateStatus() {
        String ip = localIpv4();
        if (status == null) return;
        if (ip == null) {
            status.setText("Conecte celular e PC à mesma rede Wi‑Fi.");
        } else {
            status.setText("No PC: http://" + ip + ":" + PORT + "/");
        }
        String size = previewSize == null ? targetWidth + "×" + targetHeight : previewSize.width + "×" + previewSize.height;
        details.setText(String.format(Locale.US, "%s · alvo %d fps · JPEG %d%% · %s\n%s",
                size, targetFps, jpegQuality, front ? "frontal" : "traseira", performanceText));
    }

    private String statusJson() {
        Camera.Size size = previewSize;
        int w = size == null ? targetWidth : size.width;
        int h = size == null ? targetHeight : size.height;
        return "{" +
                "\"name\":\"LanCam\"," +
                "\"version\":\"1.2.1\"," +
                "\"camera\":\"" + (front ? "front" : "back") + "\"," +
                "\"width\":" + w + "," +
                "\"height\":" + h + "," +
                "\"fps\":" + targetFps + "," +
                "\"jpegQuality\":" + jpegQuality + "," +
                "\"mirrorFront\":" + mirrorFront + "," +
                "\"torch\":" + torchEnabled + "," + performanceJson +
                "}";
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

    private interface StatusProvider {
        String getStatusJson();
    }

    private static final class MjpegServer extends Thread {
        private final int port;
        private final AtomicReference<byte[]> frame;
        private final StatusProvider statusProvider;
        private volatile boolean running = true;
        private ServerSocket serverSocket;

        MjpegServer(int port, AtomicReference<byte[]> frame, StatusProvider statusProvider) {
            super("LanCamServer");
            this.port = port;
            this.frame = frame;
            this.statusProvider = statusProvider;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket socket = serverSocket.accept();
                    Thread t = new Thread(() -> handle(socket), "LanCamClient");
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
                s.setTcpNoDelay(true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        s.getInputStream(), StandardCharsets.ISO_8859_1));
                String request = reader.readLine();
                if (request == null) return;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                String[] parts = request.split(" ");
                String path = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "/";
                if (path.startsWith("/stream") || path.startsWith("/video")) stream(s.getOutputStream());
                else if (path.startsWith("/shot.jpg")) snapshot(s.getOutputStream());
                else if (path.startsWith("/api/status")) status(s.getOutputStream());
                else index(s.getOutputStream());
            } catch (IOException ignored) { }
        }

        private void index(OutputStream out) throws IOException {
            String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>LanCam</title><style>body{margin:0;background:#111;color:#eee;text-align:center;font-family:sans-serif}" +
                    "main{max-width:1100px;margin:auto;padding:12px}img{max-width:100%;max-height:82vh;border-radius:8px}" +
                    "a{color:#9cf}small{color:#aaa}</style></head><body><main><h2>LanCam</h2>" +
                    "<img src='/stream'><p><a href='/shot.jpg'>Foto atual</a> · <a href='/api/status'>Status JSON</a></p>" +
                    "<small>Stream MJPEG local · porta " + port + "</small></main></body></html>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " +
                    body.length + "\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
            out.write(body);
            out.flush();
        }

        private void status(OutputStream out) throws IOException {
            byte[] body = statusProvider.getStatusJson().getBytes(StandardCharsets.UTF_8);
            write(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " +
                    body.length + "\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
            out.write(body);
            out.flush();
        }

        private void snapshot(OutputStream out) throws IOException {
            byte[] jpg = frame.get();
            if (jpg == null) {
                byte[] body = "Camera iniciando".getBytes(StandardCharsets.UTF_8);
                write(out, "HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain\r\nContent-Length: " +
                        body.length + "\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
                out.write(body);
            } else {
                write(out, "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " +
                        jpg.length + "\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
                out.write(jpg);
            }
            out.flush();
        }

        private void stream(OutputStream out) throws IOException {
            write(out, "HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                    "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
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
                SystemClock.sleep(10);
            }
        }

        private static void write(OutputStream out, String text) throws IOException {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
