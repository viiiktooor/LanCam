package com.example.lancam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateLauncherActivity extends Activity {
    private static final String RELEASE_API = "https://api.github.com/repos/viiiktooor/LanCam/releases/latest";
    private static final Pattern APK_PATTERN = Pattern.compile("^LanCam-Android-([0-9]+(?:\\.[0-9]+){1,3})\\.apk$", Pattern.CASE_INSENSITIVE);
    private static final String PREFS = "lancam_updates";
    private static final String PREF_PENDING = "pending_apk";
    private static final String PREF_LAST_CHECK = "last_check";
    private static final long CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private TextView message;
    private boolean handedOff;
    private boolean waitingForInstallPermission;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();

        String pending = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_PENDING, null);
        if (pending != null) {
            File file = new File(pending);
            if (file.exists()) {
                message.setText("Há uma atualização já baixada.");
                requestInstall(file);
                return;
            }
            clearPending();
        }

        long lastCheck = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(PREF_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
            launchMain();
            return;
        }
        checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!waitingForInstallPermission) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = false;
            String pending = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_PENDING, null);
            if (pending != null && new File(pending).exists()) {
                installApk(new File(pending));
                return;
            }
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("LanCam");
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        message = new TextView(this);
        message.setText("Verificando atualizações...");
        message.setTextSize(16f);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, 24, 0, 24);
        root.addView(message, msgParams);

        Button skip = new Button(this);
        skip.setText("Abrir LanCam agora");
        skip.setOnClickListener(v -> launchMain());
        root.addView(skip);

        setContentView(root);
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                UpdateAsset asset = fetchLatestApk();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                if (handedOff) return;
                if (asset != null && compareVersions(asset.version, currentVersion()) > 0) {
                    runOnUiThread(() -> offerUpdate(asset));
                } else {
                    runOnUiThread(this::launchMain);
                }
            } catch (Exception ignored) {
                if (!handedOff) runOnUiThread(this::launchMain);
            }
        }, "LanCamUpdateCheck").start();
    }

    private UpdateAsset fetchLatestApk() throws Exception {
        HttpURLConnection connection = open(RELEASE_API, 3000, 3000);
        try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
            String json = readUtf8(in);
            JSONObject release = new JSONObject(json);
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) return null;
            UpdateAsset best = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject item = assets.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "");
                Matcher matcher = APK_PATTERN.matcher(name);
                if (!matcher.matches()) continue;
                String version = matcher.group(1);
                String url = item.optString("browser_download_url", "");
                if (url.isEmpty()) continue;
                if (best == null || compareVersions(version, best.version) > 0) {
                    best = new UpdateAsset(version, url);
                }
            }
            return best;
        } finally {
            connection.disconnect();
        }
    }

    private void offerUpdate(UpdateAsset asset) {
        if (handedOff || isFinishing()) return;
        message.setText(String.format(Locale.US, "Nova versão %s disponível.", asset.version));
        new AlertDialog.Builder(this)
                .setTitle("Atualização do LanCam")
                .setMessage("Há uma nova versão do LanCam. Deseja baixar e instalar agora?")
                .setPositiveButton("Atualizar", (dialog, which) -> downloadUpdate(asset))
                .setNegativeButton("Agora não", (dialog, which) -> launchMain())
                .setCancelable(false)
                .show();
    }

    private void downloadUpdate(UpdateAsset asset) {
        message.setText("Baixando LanCam " + asset.version + "...");
        new Thread(() -> {
            try {
                File dir = getExternalFilesDir("updates");
                if (dir == null) throw new IllegalStateException("Armazenamento indisponível");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Não foi possível criar a pasta de atualização");
                File apk = new File(dir, "LanCam-update.apk");

                HttpURLConnection connection = open(asset.url, 10000, 20000);
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                    }
                } finally {
                    connection.disconnect();
                }

                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_PENDING, apk.getAbsolutePath()).apply();
                runOnUiThread(() -> requestInstall(apk));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    message.setText("Falha ao baixar atualização.");
                    new AlertDialog.Builder(this)
                            .setTitle("Atualização do LanCam")
                            .setMessage("Não foi possível baixar a atualização agora. O LanCam continuará funcionando normalmente.")
                            .setPositiveButton("Continuar", (dialog, which) -> launchMain())
                            .show();
                });
            }
        }, "LanCamUpdateDownload").start();
    }

    private void requestInstall(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            message.setText("Permita que o LanCam instale a atualização.");
            Toast.makeText(this, "Ative 'Permitir desta fonte' para o LanCam.", Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        installApk(apk);
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            clearPending();
            new AlertDialog.Builder(this)
                    .setTitle("Atualização do LanCam")
                    .setMessage("O Android não conseguiu abrir o instalador da atualização.")
                    .setPositiveButton("Continuar", (dialog, which) -> launchMain())
                    .show();
        }
    }

    private void launchMain() {
        if (handedOff || isFinishing()) return;
        handedOff = true;
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void clearPending() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_PENDING).apply();
    }

    private String currentVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    private static int compareVersions(String a, String b) {
        int[] av = parseVersion(a);
        int[] bv = parseVersion(b);
        for (int i = 0; i < av.length; i++) {
            if (av[i] != bv[i]) return av[i] < bv[i] ? -1 : 1;
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        int[] out = new int[]{0, 0, 0, 0};
        String[] parts = value == null ? new String[0] : value.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.min(parts.length, out.length); i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private static HttpURLConnection open(String url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", "LanCam-Android");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static String readUtf8(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }

    private static final class UpdateAsset {
        final String version;
        final String url;
        UpdateAsset(String version, String url) {
            this.version = version;
            this.url = url;
        }
    }
}
