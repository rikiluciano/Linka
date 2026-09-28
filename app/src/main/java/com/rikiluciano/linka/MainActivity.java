package com.rikiluciano.linka;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 19, 26);
    private static final int PANEL = Color.rgb(27, 32, 43);
    private static final int ACCENT = Color.rgb(126, 140, 255);
    private static final int TEXT = Color.rgb(242, 244, 250);
    private static final int MUTED = Color.rgb(166, 174, 191);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlInput;
    private TextView status;
    private TextView details;
    private Button downloadButton;
    private String inspectedUrl;
    private String mediaType;
    private boolean isDirectMedia;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        acceptSharedIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptSharedIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(28));
        scroll.addView(page);

        TextView brand = new TextView(this);
        brand.setText("Linka");
        brand.setTextColor(TEXT);
        brand.setTextSize(32);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(brand, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Guarda tus medios autorizados para verlos sin conexión");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(28));
        page.addView(subtitle, matchWrap());

        TextView label = new TextView(this);
        label.setText("Enlace compartido");
        label.setTextColor(TEXT);
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(label, matchWrap());

        urlInput = new EditText(this);
        urlInput.setSingleLine(false);
        urlInput.setMinLines(2);
        urlInput.setMaxLines(4);
        urlInput.setTextSize(15);
        urlInput.setTextColor(TEXT);
        urlInput.setHintTextColor(MUTED);
        urlInput.setHint("Pega un enlace directo a un archivo de audio o video");
        urlInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        urlInput.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(10);
        page.addView(urlInput, inputParams);

        Button inspectButton = button("Analizar enlace");
        LinearLayout.LayoutParams inspectParams = matchWrap();
        inspectParams.topMargin = dp(14);
        page.addView(inspectButton, inspectParams);
        inspectButton.setOnClickListener(v -> inspectCurrentUrl());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(22);
        page.addView(card, cardParams);

        status = new TextView(this);
        status.setText("Comparte un enlace desde otra app o pégalo arriba.");
        status.setTextColor(TEXT);
        status.setTextSize(15);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(status, matchWrap());

        details = new TextView(this);
        details.setText("Linka admite enlaces directos a archivos descargables. La calidad y el formato dependen del archivo de origen.");
        details.setTextColor(MUTED);
        details.setTextSize(13);
        details.setPadding(0, dp(10), 0, 0);
        card.addView(details, matchWrap());

        downloadButton = button("Descargar archivo original");
        downloadButton.setEnabled(false);
        LinearLayout.LayoutParams downloadParams = matchWrap();
        downloadParams.topMargin = dp(14);
        card.addView(downloadButton, downloadParams);
        downloadButton.setOnClickListener(v -> startDownload());

        TextView note = new TextView(this);
        note.setText("No extraemos videos desde YouTube, Instagram ni Facebook. Descarga solo contenido propio o para el que tengas permiso.");
        note.setTextColor(MUTED);
        note.setTextSize(12);
        note.setPadding(0, dp(22), 0, 0);
        page.addView(note, matchWrap());

        setContentView(scroll);
    }

    private Button button(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        return button;
    }

    private void acceptSharedIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (shared != null) {
            String link = findUrl(shared.toString());
            urlInput.setText(link.isEmpty() ? shared : link);
            if (!link.isEmpty()) inspectCurrentUrl();
        }
    }

    private String findUrl(String text) {
        if (text == null) return "";
        java.util.regex.Matcher matcher = Patterns.WEB_URL.matcher(text);
        if (!matcher.find()) return "";
        String link = matcher.group();
        while (!link.isEmpty() && ".,);]}>\"'".indexOf(link.charAt(link.length() - 1)) >= 0) {
            link = link.substring(0, link.length() - 1);
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) link = "https://" + link;
        return link;
    }

    private void inspectCurrentUrl() {
        hideKeyboard();
        String url = findUrl(urlInput.getText().toString().trim());
        if (url.isEmpty()) {
            resetInspection("Pega un enlace web válido.");
            return;
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            resetInspection("Solo se admiten enlaces HTTP o HTTPS.");
            return;
        }
        if (isUnsupportedSocialHost(url)) {
            resetInspection("Esta plataforma no ofrece a Linka un archivo directo autorizado.");
            details.setText("Puedes abrir el enlace en su app o usar la función de descarga oficial, si está disponible.");
            return;
        }

        inspectedUrl = url;
        isDirectMedia = false;
        downloadButton.setEnabled(false);
        status.setText("Comprobando el tipo de archivo…");
        details.setText(url);

        executor.execute(() -> {
            String resultType = inspectContentType(url);
            runOnUiThread(() -> {
                if (!url.equals(inspectedUrl)) return;
                mediaType = resultType;
                isDirectMedia = isSupportedMediaType(resultType, url);
                if (isDirectMedia) {
                    status.setText("Archivo directo disponible");
                    details.setText("Formato: " + labelForType(resultType) + " · Calidad: original (la que ofrece el enlace)\n" + url);
                    downloadButton.setEnabled(true);
                } else {
                    status.setText("No se detectó un archivo de audio o video directo");
                    details.setText("Para páginas web, comparte un enlace directo al archivo multimedia. Linka no inicia sesión ni extrae medios de sitios que no permiten descargarlos.");
                }
            });
        });
    }

    private String inspectContentType(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Linka/1.0 Android");
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
                connection.disconnect();
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Range", "bytes=0-0");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Linka/1.0 Android");
                code = connection.getResponseCode();
            }
            if (code < 200 || code >= 400) return "";
            String value = connection.getContentType();
            return value == null ? "" : value.split(";")[0].trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isSupportedMediaType(String type, String url) {
        if (type == null || type.isEmpty() || type.contains("text/html")) return false;
        if (type.startsWith("audio/") || type.startsWith("video/")) return true;
        String path = Uri.parse(url).getPath();
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")
                || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov");
    }

    private boolean isUnsupportedSocialHost(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) return false;
        String value = host.toLowerCase(Locale.ROOT);
        return value.equals("youtube.com") || value.endsWith(".youtube.com") || value.equals("youtu.be")
                || value.equals("instagram.com") || value.endsWith(".instagram.com")
                || value.equals("facebook.com") || value.endsWith(".facebook.com") || value.equals("fb.watch");
    }

    private String labelForType(String type) {
        if (type == null) return "archivo multimedia";
        if (type.equals("audio/mpeg")) return "MP3";
        if (type.equals("video/mp4")) return "MP4";
        if (type.equals("video/webm")) return "WebM";
        return type;
    }

    private void startDownload() {
        if (!isDirectMedia || TextUtils.isEmpty(inspectedUrl)) return;
        try {
            String fileName = Uri.parse(inspectedUrl).getLastPathSegment();
            if (TextUtils.isEmpty(fileName) || fileName.equals(".")) fileName = "linka-media";
            fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(inspectedUrl));
            request.setTitle(fileName);
            request.setDescription("Descarga iniciada por Linka");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("El gestor de descargas no está disponible.");
            manager.enqueue(request);
            Toast.makeText(this, "Descarga iniciada. El archivo aparecerá en Descargas.", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "No se pudo iniciar la descarga: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetInspection(String message) {
        inspectedUrl = null;
        mediaType = null;
        isDirectMedia = false;
        downloadButton.setEnabled(false);
        status.setText(message);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
