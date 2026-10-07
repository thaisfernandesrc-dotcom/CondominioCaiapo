package br.caiapo.condominio;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE_BACKUP = 1;
    private static final int REQ_OPEN_BACKUP = 2;

    private WebView web;
    private String pendingBackup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.voltar ? voltar() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    // ---------- arquivos ----------
    private File dataFile() { return new File(getFilesDir(), "dados.json"); }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private void writeAtomic(File f, String text) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new Exception("rename");
    }

    private void shareFile(File f, String mime, String title) {
        Uri uri = FileProvider.getUriForFile(this, "br.caiapo.condominio.files", f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, title));
    }

    private File sharedDir() {
        File d = new File(getCacheDir(), "shared");
        d.mkdirs();
        return d;
    }

    private void toast(String t) {
        runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_LONG).show());
    }

    private void callJs(String fn, String arg) {
        String js = fn + "(" + JSONObject.quote(arg) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) {
            if (req == REQ_OPEN_BACKUP) callJs("window.backupCarregado", "");
            return;
        }
        Uri uri = data.getData();
        try {
            if (req == REQ_SAVE_BACKUP && pendingBackup != null) {
                try (OutputStream o = getContentResolver().openOutputStream(uri, "wt")) {
                    o.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                }
                pendingBackup = null;
                toast("Backup salvo.");
            } else if (req == REQ_OPEN_BACKUP) {
                callJs("window.backupCarregado", readAll(getContentResolver().openInputStream(uri)));
            }
        } catch (Exception e) {
            toast("Não foi possível ler ou gravar o arquivo.");
        }
    }

    // ---------- ponte com a página ----------
    class Bridge {
        @JavascriptInterface
        public String loadData() {
            try {
                File f = dataFile();
                return f.exists() ? readAll(new FileInputStream(f)) : "";
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveData(String json) {
            try {
                writeAtomic(dataFile(), json);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void saveBackup(String json, String filename) {
            pendingBackup = json;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, filename);
            runOnUiThread(() -> startActivityForResult(i, REQ_SAVE_BACKUP));
        }

        @JavascriptInterface
        public void shareBackup(String json, String filename) {
            try {
                File f = new File(sharedDir(), filename);
                writeAtomic(f, json);
                runOnUiThread(() -> shareFile(f, "application/json", "Enviar backup"));
            } catch (Exception e) {
                toast("Não foi possível preparar o backup.");
            }
        }

        @JavascriptInterface
        public void openBackup() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            runOnUiThread(() -> startActivityForResult(i, REQ_OPEN_BACKUP));
        }

        @JavascriptInterface
        public void shareImage(String base64Png, String filename) {
            try {
                File f = new File(sharedDir(), filename);
                try (FileOutputStream o = new FileOutputStream(f)) {
                    o.write(Base64.decode(base64Png, Base64.DEFAULT));
                }
                runOnUiThread(() -> shareFile(f, "image/png", "Compartilhar balancete"));
            } catch (Exception e) {
                toast("Não foi possível gerar a imagem.");
            }
        }

        @JavascriptInterface
        public void shareText(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, "Compartilhar balancete")));
        }
    }
}
