package br.caiapo.condominio;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE_BACKUP = 1;
    private static final int REQ_OPEN_BACKUP = 2;
    private static final int REQ_SAVE_IMAGE = 3;
    private static final int REQ_GALERIA = 4;
    private static final int REQ_CAMERA = 5;

    private WebView web;
    private String pendingBackup;
    private byte[] pendingImage;
    private String pendingFotoCb;
    private File fotoCamera;

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

    private void callJs(String fn, String a, String b) {
        String js = fn + "(" + JSONObject.quote(a) + "," + JSONObject.quote(b) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private File comprovantesDir() {
        File d = new File(getFilesDir(), "comprovantes");
        d.mkdirs();
        return d;
    }

    private static boolean nomeSeguro(String nome) {
        return nome != null && nome.matches("[A-Za-z0-9._-]{1,120}");
    }

    /** Lê a foto, reduz para no máximo 1600 px, corrige a rotação e devolve JPEG em base64. */
    private String prepararFoto(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        int maior = Math.max(o.outWidth, o.outHeight), amostra = 1;
        while (maior / (amostra * 2) >= 1600) amostra *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = amostra;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
        if (bmp == null) throw new Exception("imagem");
        int graus = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            int or = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (or == ExifInterface.ORIENTATION_ROTATE_90) graus = 90;
            else if (or == ExifInterface.ORIENTATION_ROTATE_180) graus = 180;
            else if (or == ExifInterface.ORIENTATION_ROTATE_270) graus = 270;
        } catch (Exception ignored) { }
        float escala = Math.min(1f, 1600f / Math.max(bmp.getWidth(), bmp.getHeight()));
        if (graus != 0 || escala < 1f) {
            Matrix m = new Matrix();
            m.postScale(escala, escala);
            m.postRotate(graus);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void entregarFoto(Uri uri) {
        String cb = pendingFotoCb;
        pendingFotoCb = null;
        if (cb == null) return;
        new Thread(() -> {
            String b64 = "";
            try { if (uri != null) b64 = prepararFoto(uri); } catch (Exception e) { toast("Não foi possível ler a foto."); }
            callJs("window.fotoEscolhida", cb, b64);
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CAMERA) {
            entregarFoto(res == RESULT_OK && fotoCamera != null && fotoCamera.length() > 0 ? Uri.fromFile(fotoCamera) : null);
            return;
        }
        if (req == REQ_GALERIA) {
            entregarFoto(res == RESULT_OK && data != null ? data.getData() : null);
            return;
        }
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
            } else if (req == REQ_SAVE_IMAGE && pendingImage != null) {
                try (OutputStream o = getContentResolver().openOutputStream(uri, "wt")) {
                    o.write(pendingImage);
                }
                pendingImage = null;
                toast("Imagem salva.");
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

        /** Salva a imagem na galeria (Android 10+) e devolve true; em versões antigas abre o seletor de pasta e devolve false. */
        @JavascriptInterface
        public boolean saveImage(String base64Png, String filename) {
            byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Condomínio Caiapó");
                    v.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("insert");
                    try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                        o.write(bytes);
                    }
                    v.clear();
                    v.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, v, null, null);
                    return true;
                } catch (Exception e) {
                    toast("Não foi possível salvar a imagem.");
                    return false;
                }
            }
            pendingImage = bytes;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_TITLE, filename);
            runOnUiThread(() -> startActivityForResult(i, REQ_SAVE_IMAGE));
            return false;
        }

        /** Abre a câmera ("camera") ou a galeria ("galeria"); a foto volta por window.fotoEscolhida(cb, base64). */
        @JavascriptInterface
        public void escolherFoto(String origem, String cb) {
            pendingFotoCb = cb;
            runOnUiThread(() -> {
                try {
                    if ("camera".equals(origem)) {
                        fotoCamera = new File(sharedDir(), "foto-camera.jpg");
                        if (fotoCamera.exists()) fotoCamera.delete();
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, "br.caiapo.condominio.files", fotoCamera);
                        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(i, REQ_CAMERA);
                    } else {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(Intent.createChooser(i, "Escolher comprovante"), REQ_GALERIA);
                    }
                } catch (Exception e) {
                    pendingFotoCb = null;
                    toast("Não foi possível abrir a câmera ou a galeria.");
                    callJs("window.fotoEscolhida", cb, "");
                }
            });
        }

        @JavascriptInterface
        public boolean salvarArquivo(String nome, String b64) {
            if (!nomeSeguro(nome)) return false;
            try (FileOutputStream o = new FileOutputStream(new File(comprovantesDir(), nome))) {
                o.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String lerArquivo(String nome) {
            if (!nomeSeguro(nome)) return "";
            File f = new File(comprovantesDir(), nome);
            if (!f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void apagarArquivo(String nome) {
            if (nomeSeguro(nome)) new File(comprovantesDir(), nome).delete();
        }

        /** Envia JSON por POST ao script do Google; a resposta volta por window.nuvemResposta(cb, texto). Texto vazio = falha de rede. */
        @JavascriptInterface
        public void enviarNuvem(String url, String corpo, String cb) {
            new Thread(() -> {
                String resposta = "";
                try {
                    if (!url.startsWith("https://script.google.com/")) throw new Exception("url");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                    try (OutputStream o = c.getOutputStream()) { o.write(corpo.getBytes(StandardCharsets.UTF_8)); }
                    int code = c.getResponseCode();
                    int voltas = 0;
                    while ((code == 301 || code == 302 || code == 303 || code == 307) && voltas++ < 5) {
                        String loc = c.getHeaderField("Location");
                        c.disconnect();
                        c = (HttpURLConnection) new URL(loc).openConnection();
                        c.setInstanceFollowRedirects(false);
                        c.setConnectTimeout(20000);
                        c.setReadTimeout(60000);
                        code = c.getResponseCode();
                    }
                    if (code == 200) resposta = readAll(c.getInputStream());
                    c.disconnect();
                } catch (Exception e) {
                    resposta = "";
                }
                callJs("window.nuvemResposta", cb, resposta);
            }).start();
        }

        @JavascriptInterface
        public void abrirLink(String url) {
            if (url == null || !url.startsWith("https://")) return;
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { toast("Não foi possível abrir o link."); }
            });
        }

        @JavascriptInterface
        public void copiarTexto(String texto) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Condomínio Caiapó", texto));
                toast("Copiado.");
            });
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
