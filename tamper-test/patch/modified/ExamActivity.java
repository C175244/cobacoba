package com.example.soalpintar;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import com.example.soalpintar.ExamActivity;
import com.example.soalpintar.ExamApi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/* loaded from: classes.dex */
public class ExamActivity extends AppCompatActivity {
    private static final String DRAFT_JS = "(function(){if(window.__spDraft)return;window.__spDraft=1;function sent(){var f=document.querySelector('form'); if(f&&f.querySelector('input[name=fbzx]'))return false; if(location.href.indexOf('formResponse')<0)return false; var t=(document.body&&document.body.innerText)||''; return /telah direkam|has been recorded|kirim jawaban lain|submit another response|lihat skor|view score/i.test(t);}function collect(){try{ if(sent()){SPDraft.submitted();return;} var f=document.querySelector('form');if(!f)return; var fd=new FormData(f),o={}; fd.forEach(function(v,k){if(k.indexOf('entry.')!==0||/_sentinel$/.test(k))return;(o[k]=o[k]||[]).push(String(v));}); var j=JSON.stringify(o);if(j!==window.__spLast){window.__spLast=j;SPDraft.save(j);}}catch(e){}}document.addEventListener('change',collect,true);document.addEventListener('click',function(){setTimeout(collect,300);},true);document.addEventListener('input',function(){clearTimeout(window.__spT);window.__spT=setTimeout(collect,700);},true);setInterval(collect,4000);collect();})();";
    private static final long JEDA_PELANGGARAN_MS = 1500;
    private static final String PREF_ZOOM = "text_zoom";
    private static final String TAG = "ExamActivity";
    private static final int[] ZOOM_LEVELS = {100, 125, 150, 175};
    private ToneGenerator alarmTembus;
    private AudioManager audioManager;
    private TextView bannerCount;
    private TextView bannerText;
    private View blockOverlay;
    private TextView blockStatus;
    private TextView btnKeluar;
    private Button btnLanjutkan;
    private TextView btnZoom;
    private int originalAlarmVolume;
    private LinearProgressIndicator pageProgress;
    private CountDownTimer timer;
    private String tokenCode;
    private TextView tvCountdown;
    private TextView tvGuru;
    private TextView tvTopic;
    private View violationBanner;
    private WebView webView;
    private int maksPelanggaran = 3;
    private boolean isAlarmPlaying = false;
    private boolean isSafeExit = false;
    private boolean isDeviceOwner = false;
    private boolean isDialogOpen = false;
    private int jumlahPelanggaran = 0;
    private long pelanggaranTerakhir = 0;
    private boolean isBlocked = false;
    private boolean blokirTerkirim = false;
    private boolean sudahKirim = false;
    private boolean pendingRestore = false;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatRunnable = new Runnable() { // from class: com.example.soalpintar.ExamActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (ExamActivity.this.isSafeExit || ExamActivity.this.tokenCode == null) {
                return;
            }
            if (!ExamActivity.this.isBlocked) {
                ExamActivity examActivity = ExamActivity.this;
                ExamApi.sendEvent(examActivity, examActivity.tokenCode, "heartbeat", null, 0);
            }
            ExamActivity.this.heartbeatHandler.postDelayed(this, AppConfig.cached(ExamActivity.this).heartbeatSeconds * 1000);
        }
    };
    private final Handler lockEnforceHandler = new Handler(Looper.getMainLooper());
    private final Runnable lockEnforceRunnable = new Runnable() { // from class: com.example.soalpintar.ExamActivity.2
        @Override // java.lang.Runnable
        public void run() {
            if (ExamActivity.this.isSafeExit) {
                return;
            }
            ExamActivity.this.sembunyikanSystemUI();
            ExamActivity.this.lockEnforceHandler.postDelayed(this, 500L);
        }
    };
    private boolean sudahLaporSelesai = false;

    /* renamed from: com.example.soalpintar.ExamActivity$7, reason: invalid class name */
    class AnonymousClass7 extends CountDownTimer {
        AnonymousClass7(long j, long j2) {
            super(j, j2);
        }

        /* renamed from: lambda$onFinish$0$com-example-soalpintar-ExamActivity$7, reason: not valid java name */
        /* synthetic */ void m86lambda$onFinish$0$comexamplesoalpintarExamActivity$7(DialogInterface dialogInterface, int i) {
            ExamActivity.this.isDialogOpen = false;
            ExamActivity.this.selesaiDanKeluar();
        }

        @Override // android.os.CountDownTimer
        public void onFinish() {
            ExamActivity.this.tvCountdown.setText("HABIS!");
            ExamActivity.this.tvCountdown.setTextColor(Color.parseColor("#FECACA"));
            ExamActivity.this.isDialogOpen = true;
            new MaterialAlertDialogBuilder(ExamActivity.this).setTitle((CharSequence) "Waktu habis").setMessage((CharSequence) "Waktu ujian telah habis. Anda akan dikembalikan ke halaman utama.").setCancelable(false).setPositiveButton((CharSequence) "OK", new DialogInterface.OnClickListener() { // from class: com.example.soalpintar.ExamActivity$7$$ExternalSyntheticLambda0
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    ExamActivity.AnonymousClass7.this.m86lambda$onFinish$0$comexamplesoalpintarExamActivity$7(dialogInterface, i);
                }
            }).show();
        }

        @Override // android.os.CountDownTimer
        public void onTick(long j) {
            long j2 = j / 3600000;
            long j3 = (j / 60000) % 60;
            ExamActivity.this.tvCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", Long.valueOf(j2), Long.valueOf(j3), Long.valueOf((j / 1000) % 60)));
            if (j2 != 0 || j3 >= 5) {
                return;
            }
            ExamActivity.this.tvCountdown.setTextColor(Color.parseColor("#FEF08A"));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    final class DraftBridge {
        private DraftBridge() {
        }

        @JavascriptInterface
        public void save(String str) {
            if (ExamActivity.this.sudahKirim || ExamActivity.this.tokenCode == null) {
                return;
            }
            ExamActivity examActivity = ExamActivity.this;
            DraftStore.merge(examActivity, examActivity.tokenCode, str);
        }

        @JavascriptInterface
        public void submitted() {
            final ExamActivity examActivity = ExamActivity.this;
            examActivity.runOnUiThread(new Runnable() { // from class: com.example.soalpintar.ExamActivity$DraftBridge$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    ExamActivity.this.formTerkirim();
                }
            });
        }
    }

    /* JADX WARN: Type inference failed for: r4v1, types: [com.example.soalpintar.ExamActivity$4] */
    private void bunyikanAlarm() {
        // TAMPER TEST: tidak ada perubahan volume dan tidak ada bunyi alarm.
    }

    private void cekBukaBlokir() {
        // TAMPER TEST: tidak ada pemeriksaan status blokir.
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void formTerkirim() {
        if (this.sudahKirim) {
            return;
        }
        this.sudahKirim = true;
        String str = this.tokenCode;
        if (str != null) {
            DraftStore.clear(this, str);
            DraftStore.setViolations(this, this.tokenCode, 0);
        }
        laporSelesai();
        Toast.makeText(this, "Jawaban terkirim! Ujian selesai.", 1).show();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                ExamActivity.this.lepasKunci();
            }
        }, 2000L);
    }

    private void gantiZoom() {
        int zoomTersimpan = zoomTersimpan();
        int i = ZOOM_LEVELS[0];
        int i2 = 0;
        while (true) {
            int[] iArr = ZOOM_LEVELS;
            if (i2 >= iArr.length) {
                break;
            }
            if (iArr[i2] == zoomTersimpan) {
                i = iArr[(i2 + 1) % iArr.length];
                break;
            }
            i2++;
        }
        getSharedPreferences("soalpintar_prefs", 0).edit().putInt(PREF_ZOOM, i).apply();
        terapkanZoom(i);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(this.webView.getWindowToken(), 0);
            }
        } catch (Exception unused) {
        }
    }

    private boolean isKeyboardVisible() {
        try {
            Rect rect = new Rect();
            View decorView = getWindow().getDecorView();
            decorView.getWindowVisibleDisplayFrame(rect);
            int height = decorView.getRootView().getHeight();
            return ((double) (height - rect.bottom)) > ((double) height) * 0.15d;
        } catch (Exception unused) {
            return false;
        }
    }

    private void lanjutkanUjian() {
        this.isBlocked = false;
        this.blokirTerkirim = false;
        this.jumlahPelanggaran = 0;
        DraftStore.setViolations(this, this.tokenCode, 0);
        this.pelanggaranTerakhir = SystemClock.elapsedRealtime();
        this.blockOverlay.setVisibility(8);
        sembunyikanSystemUI();
        ExamApi.sendEvent(this, this.tokenCode, "heartbeat", null, 0);
        Toast.makeText(this, "Blokir dibuka. Silakan lanjutkan, jawaban Anda masih ada.", 1).show();
    }

    private void laporSelesai() {
        String str;
        if (this.sudahLaporSelesai || (str = this.tokenCode) == null) {
            return;
        }
        this.sudahLaporSelesai = true;
        ExamApi.sendEvent(this, str, "complete", null, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void lepasKunci() {
        this.isSafeExit = true;
        this.lockEnforceHandler.removeCallbacks(this.lockEnforceRunnable);
        this.heartbeatHandler.removeCallbacks(this.heartbeatRunnable);
        this.bannerHandler.removeCallbacksAndMessages(null);
        try {
            CountDownTimer countDownTimer = this.timer;
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            ToneGenerator toneGenerator = this.alarmTembus;
            if (toneGenerator != null) {
                toneGenerator.release();
            }
            // TAMPER TEST: tanpa perubahan volume, tanpa stopLockTask()/setStatusBarDisabled().
        } catch (Exception e) {
            Log.e(TAG, "lepasKunci error: " + e.getMessage());
        }
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void memicuPelanggaran(String str, String str2) {
        // TAMPER TEST: pelanggaran tidak dicatat, tidak dilaporkan,
        // tidak ada alarm, banner, maupun overlay blokir.
        Log.d(TAG, "TAMPER TEST: pelanggaran diabaikan (" + str2 + ")");
    }

    private void mulaiTimer(String str) {
        ExamActivity examActivity;
        ParseException parseException;
        SimpleDateFormat simpleDateFormat;
        try {
        } catch (ParseException e) {
            e = e;
            examActivity = this;
        }
        try {
            if (str.endsWith("Z")) {
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            } else if (str.contains("+")) {
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            } else {
                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            Date parse = simpleDateFormat.parse(str);
            try {
                if (parse == null) {
                    throw new ParseException("null date", 0);
                }
                long time = parse.getTime() - System.currentTimeMillis();
                if (time > 0) {
                    this.timer = new AnonymousClass7(time, 1000L).start();
                } else {
                    this.tvCountdown.setText("HABIS!");
                    this.tvCountdown.setTextColor(Color.parseColor("#FECACA"));
                }
            } catch (ParseException e2) {
                e = e2;
                parseException = e;
                Log.e(TAG, "Parse timer error: " + parseException.getMessage());
                examActivity.tvCountdown.setText("Error Timer");
            }
        } catch (ParseException e3) {
            parseException = e3;
            examActivity = this;
            Log.e(TAG, "Parse timer error: " + parseException.getMessage());
            examActivity.tvCountdown.setText("Error Timer");
        }
    }

    private void paksakembali() {
        // TAMPER TEST: tidak memaksa pengguna kembali ke aplikasi.
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void selesaiDanKeluar() {
        String str = this.tokenCode;
        if (str != null) {
            DraftStore.setViolations(this, str, 0);
        }
        laporSelesai();
        lepasKunci();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sembunyikanSystemUI() {
        // TAMPER TEST: status/navigation bar dibiarkan normal.
    }

    private void setupKioskMode() {
        // TAMPER TEST: kiosk / lock task dimatikan sepenuhnya.
        Log.d(TAG, "TAMPER TEST: setupKioskMode() dilewati");
    }

    private void setupWebView() {
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.getSettings().setDomStorageEnabled(true);
        this.webView.getSettings().setSupportZoom(false);
        this.webView.getSettings().setAllowFileAccess(false);
        this.webView.setWebChromeClient(new WebChromeClient() { // from class: com.example.soalpintar.ExamActivity.5
            @Override // android.webkit.WebChromeClient
            public void onProgressChanged(WebView webView, int i) {
                ExamActivity.this.pageProgress.setProgressCompat(i, true);
                if (i >= 100) {
                    ExamActivity.this.pageProgress.animate().alpha(0.0f).setDuration(300L).start();
                } else if (ExamActivity.this.pageProgress.getAlpha() < 1.0f) {
                    ExamActivity.this.pageProgress.setAlpha(1.0f);
                }
            }
        });
        this.webView.addJavascriptInterface(new DraftBridge(), "SPDraft");
        this.webView.setWebViewClient(new WebViewClient() { // from class: com.example.soalpintar.ExamActivity.6
            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView webView, String str) {
                super.onPageFinished(webView, str);
                if (str == null || !str.contains("docs.google.com/forms")) {
                    return;
                }
                if (ExamActivity.this.pendingRestore && str.contains("/viewform") && !str.contains("usp=pp_url")) {
                    ExamActivity.this.pendingRestore = false;
                    ExamActivity examActivity = ExamActivity.this;
                    String prefillUrl = DraftStore.prefillUrl(examActivity, examActivity.tokenCode, str);
                    if (prefillUrl != null) {
                        webView.loadUrl(prefillUrl);
                        Toast.makeText(ExamActivity.this, "Jawaban Anda sebelumnya dipulihkan. Periksa lagi sebelum menekan Kirim.", 1).show();
                        return;
                    }
                }
                webView.evaluateJavascript(ExamActivity.DRAFT_JS, null);
            }

            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
                webView.loadUrl(webResourceRequest.getUrl().toString());
                return true;
            }
        });
    }

    private void suspendUjian() {
        // TAMPER TEST: tidak ada overlay blokir.
    }

    private void tampilkanBanner(final String str) {
        runOnUiThread(new Runnable() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                ExamActivity.this.m82lambda$tampilkanBanner$13$comexamplesoalpintarExamActivity(str);
            }
        });
    }

    private void tampilkanDialogKeluar() {
        this.isDialogOpen = true;
        new MaterialAlertDialogBuilder(this).setTitle((CharSequence) "Akhiri ujian?").setMessage((CharSequence) "Pastikan jawaban Anda SUDAH DIKIRIM: tekan tombol Kirim di bagian akhir Google Form sampai muncul tulisan \"Jawaban Anda telah direkam\".\n\nJika tetap selesai sekarang, ujian ditutup dan token ini terkunci di HP ini. Jawaban yang belum dikirim tetap tersimpan di HP, dan baru bisa dilanjutkan jika guru membuka kembali dari dashboard.").setPositiveButton((CharSequence) "TETAP SELESAI", new DialogInterface.OnClickListener() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda14
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                ExamActivity.this.m83x537f67c4(dialogInterface, i);
            }
        }).setNegativeButton((CharSequence) "BATAL", new DialogInterface.OnClickListener() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda15
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                ExamActivity.this.m84x67273b45(dialogInterface, i);
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda16
            @Override // android.content.DialogInterface.OnCancelListener
            public final void onCancel(DialogInterface dialogInterface) {
                ExamActivity.this.m85x7acf0ec6(dialogInterface);
            }
        }).show();
    }

    private void terapkanZoom(int i) {
        this.webView.getSettings().setTextZoom(i);
        this.btnZoom.setText("Aa · " + i + "%");
    }

    private int zoomTersimpan() {
        int i = getSharedPreferences("soalpintar_prefs", 0).getInt(PREF_ZOOM, 100);
        for (int i2 : ZOOM_LEVELS) {
            if (i2 == i) {
                return i;
            }
        }
        return 100;
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.core.app.ComponentActivity, android.app.Activity, android.view.Window.Callback
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        // TAMPER TEST: Home, Recent, Power, Volume, dan Back berfungsi normal.
        return super.dispatchKeyEvent(keyEvent);
    }

    /* renamed from: lambda$cekBukaBlokir$15$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m69lambda$cekBukaBlokir$15$comexamplesoalpintarExamActivity() {
        this.blokirTerkirim = true;
    }

    /* renamed from: lambda$cekBukaBlokir$16$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m70lambda$cekBukaBlokir$16$comexamplesoalpintarExamActivity(Boolean bool) {
        this.btnLanjutkan.setEnabled(true);
        if (bool == null) {
            this.blockStatus.setText("Gagal terhubung ke server. Periksa internet, lalu coba lagi.");
            return;
        }
        if (bool.booleanValue()) {
            this.blokirTerkirim = true;
            this.blockStatus.setText("Blokir belum dibuka guru. Minta guru menekan reset di dashboard, lalu coba lagi.");
        } else if (this.blokirTerkirim) {
            lanjutkanUjian();
        } else {
            ExamApi.sendEvent(this, this.tokenCode, "block", "Batas pelanggaran tercapai", this.jumlahPelanggaran, new Runnable() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    ExamActivity.this.m69lambda$cekBukaBlokir$15$comexamplesoalpintarExamActivity();
                }
            });
            this.blockStatus.setText("Status blokir sedang dikirim ke server. Minta guru membuka blokir, lalu coba lagi.");
        }
    }

    /* renamed from: lambda$memicuPelanggaran$10$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m71lambda$memicuPelanggaran$10$comexamplesoalpintarExamActivity() {
        this.blokirTerkirim = true;
    }

    /* renamed from: lambda$onCreate$0$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m72lambda$onCreate$0$comexamplesoalpintarExamActivity(View view) {
        cekBukaBlokir();
    }

    /* renamed from: lambda$onCreate$1$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m73lambda$onCreate$1$comexamplesoalpintarExamActivity(View view) {
        gantiZoom();
    }

    /* renamed from: lambda$onCreate$2$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m74lambda$onCreate$2$comexamplesoalpintarExamActivity(View view) {
        tampilkanDialogKeluar();
    }

    /* renamed from: lambda$onMultiWindowModeChanged$6$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m75x2530f8c7() {
        // TAMPER TEST: helper paksa-kembali dinonaktifkan.
    }

    /* renamed from: lambda$onWindowFocusChanged$9$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m76xeaafcbce() {
        if (this.isSafeExit || this.isDialogOpen || isKeyboardVisible()) {
            return;
        }
        memicuPelanggaran("Aplikasi lain terdeteksi di atas layar!", "Aplikasi lain / jendela melayang");
        paksakembali();
    }

    /* renamed from: lambda$sembunyikanSystemUI$7$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m77lambda$sembunyikanSystemUI$7$comexamplesoalpintarExamActivity() {
        if (this.isSafeExit || this.isDialogOpen || isKeyboardVisible()) {
            return;
        }
        memicuPelanggaran("Dilarang membuka notifikasi!", "Membuka panel notifikasi");
    }

    /* renamed from: lambda$sembunyikanSystemUI$8$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m78lambda$sembunyikanSystemUI$8$comexamplesoalpintarExamActivity(int i) {
        if ((i & 4) == 0) {
            sembunyikanSystemUI();
            if (this.isDialogOpen) {
                return;
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    ExamActivity.this.m77lambda$sembunyikanSystemUI$7$comexamplesoalpintarExamActivity();
                }
            }, 500L);
        }
    }

    /* renamed from: lambda$suspendUjian$14$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m79lambda$suspendUjian$14$comexamplesoalpintarExamActivity() {
        hideKeyboard();
        this.violationBanner.setVisibility(8);
        this.webView.clearFocus();
        this.blockStatus.setText("");
        this.blockOverlay.setVisibility(0);
        this.blockOverlay.requestFocus();
    }

    /* renamed from: lambda$tampilkanBanner$11$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m80lambda$tampilkanBanner$11$comexamplesoalpintarExamActivity() {
        this.violationBanner.setVisibility(8);
    }

    /* renamed from: lambda$tampilkanBanner$12$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m81lambda$tampilkanBanner$12$comexamplesoalpintarExamActivity() {
        this.violationBanner.animate().alpha(0.0f).translationY(-60.0f).setDuration(220L).withEndAction(new Runnable() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                ExamActivity.this.m80lambda$tampilkanBanner$11$comexamplesoalpintarExamActivity();
            }
        }).start();
    }

    /* renamed from: lambda$tampilkanBanner$13$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m82lambda$tampilkanBanner$13$comexamplesoalpintarExamActivity(String str) {
        this.bannerText.setText(str + " Sisa " + (this.maksPelanggaran - this.jumlahPelanggaran) + "x sebelum ujian dikunci.");
        this.bannerCount.setText(this.jumlahPelanggaran + "/" + this.maksPelanggaran);
        this.bannerHandler.removeCallbacksAndMessages(null);
        if (this.violationBanner.getVisibility() != 0) {
            this.violationBanner.setAlpha(0.0f);
            this.violationBanner.setTranslationY(-60.0f);
            this.violationBanner.setVisibility(0);
        }
        this.violationBanner.animate().alpha(1.0f).translationY(0.0f).setDuration(260L).setInterpolator(new DecelerateInterpolator()).start();
        this.violationBanner.performHapticFeedback(0);
        this.bannerHandler.postDelayed(new Runnable() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                ExamActivity.this.m81lambda$tampilkanBanner$12$comexamplesoalpintarExamActivity();
            }
        }, 4000L);
    }

    /* renamed from: lambda$tampilkanDialogKeluar$3$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m83x537f67c4(DialogInterface dialogInterface, int i) {
        this.isDialogOpen = false;
        selesaiDanKeluar();
    }

    /* renamed from: lambda$tampilkanDialogKeluar$4$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m84x67273b45(DialogInterface dialogInterface, int i) {
        this.isDialogOpen = false;
    }

    /* renamed from: lambda$tampilkanDialogKeluar$5$com-example-soalpintar-ExamActivity, reason: not valid java name */
    /* synthetic */ void m85x7acf0ec6(DialogInterface dialogInterface) {
        this.isDialogOpen = false;
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(128); // TAMPER TEST: hanya FLAG_KEEP_SCREEN_ON
        setContentView(R.layout.activity_exam);
        // TAMPER TEST: sembunyikanSystemUI() & setupKioskMode() dilewati.
        AudioManager audioManager = (AudioManager) getSystemService("audio");
        this.audioManager = audioManager;
        this.originalAlarmVolume = audioManager.getStreamVolume(4);
        this.alarmTembus = new ToneGenerator(4, 50);
        this.webView = (WebView) findViewById(R.id.webView);
        this.tvGuru = (TextView) findViewById(R.id.tvGuru);
        this.tvTopic = (TextView) findViewById(R.id.tvTopic);
        this.tvCountdown = (TextView) findViewById(R.id.tvCountdown);
        this.btnKeluar = (TextView) findViewById(R.id.btnKeluar);
        this.pageProgress = (LinearProgressIndicator) findViewById(R.id.pageProgress);
        this.violationBanner = findViewById(R.id.violationBanner);
        this.bannerText = (TextView) findViewById(R.id.bannerText);
        this.bannerCount = (TextView) findViewById(R.id.bannerCount);
        this.blockOverlay = findViewById(R.id.blockOverlay);
        this.blockStatus = (TextView) findViewById(R.id.blockStatus);
        Button button = (Button) findViewById(R.id.btnLanjutkan);
        this.btnLanjutkan = button;
        button.setOnClickListener(new View.OnClickListener() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ExamActivity.this.m72lambda$onCreate$0$comexamplesoalpintarExamActivity(view);
            }
        });
        TextView textView = (TextView) findViewById(R.id.btnZoom);
        this.btnZoom = textView;
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ExamActivity.this.m73lambda$onCreate$1$comexamplesoalpintarExamActivity(view);
            }
        });
        this.btnKeluar.setOnClickListener(new View.OnClickListener() { // from class: com.example.soalpintar.ExamActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ExamActivity.this.m74lambda$onCreate$2$comexamplesoalpintarExamActivity(view);
            }
        });
        setupWebView();
        terapkanZoom(zoomTersimpan());
        String stringExtra = getIntent().getStringExtra("FORM_URL");
        String stringExtra2 = getIntent().getStringExtra("GURU");
        String stringExtra3 = getIntent().getStringExtra("TOPIC");
        String stringExtra4 = getIntent().getStringExtra("EXPIRES_AT");
        this.maksPelanggaran = getIntent().getIntExtra("MAX_VIOLATIONS", 3);
        this.tokenCode = getIntent().getStringExtra("TOKEN_CODE");
        TextView textView2 = this.tvGuru;
        StringBuilder sb = new StringBuilder();
        if (stringExtra2 == null) {
            stringExtra2 = "-";
        }
        textView2.setText(sb.append(stringExtra2).append(" · v.2.1").toString());
        TextView textView3 = this.tvTopic;
        if (stringExtra3 == null) {
            stringExtra3 = "-";
        }
        textView3.setText(stringExtra3);
        String str = this.tokenCode;
        if (str != null) {
            this.jumlahPelanggaran = 0;
            DraftStore.setViolations(this, str, 0);
            this.pendingRestore = DraftStore.has(this, this.tokenCode);
        }
        if (stringExtra4 != null) {
            mulaiTimer(stringExtra4);
        }
        if (stringExtra != null) {
            this.webView.loadUrl(stringExtra);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(false) { // from class: com.example.soalpintar.ExamActivity.3
            @Override // androidx.activity.OnBackPressedCallback
            public void handleOnBackPressed() {
                // TAMPER TEST: tombol Back normal (callback nonaktif).
            }
        });
        // TAMPER TEST: lockEnforceHandler tidak dijalankan.
        this.heartbeatHandler.post(this.heartbeatRunnable);
        Watermark.attach(this);
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        this.lockEnforceHandler.removeCallbacks(this.lockEnforceRunnable);
        this.heartbeatHandler.removeCallbacks(this.heartbeatRunnable);
        this.bannerHandler.removeCallbacksAndMessages(null);
        CountDownTimer countDownTimer = this.timer;
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        ToneGenerator toneGenerator = this.alarmTembus;
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onMultiWindowModeChanged(boolean z) {
        super.onMultiWindowModeChanged(z);
        // TAMPER TEST: split-screen / multi-window diizinkan.
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        super.onPause();
        // TAMPER TEST: keluar aplikasi bukan pelanggaran.
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        // TAMPER TEST: tanpa startLockTask() dan tanpa menyembunyikan system UI.
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStop() {
        super.onStop();
        // TAMPER TEST: keluar aplikasi bukan pelanggaran.
    }

    @Override // android.app.Activity
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // TAMPER TEST: Home / Recent tidak dianggap pelanggaran.
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        // TAMPER TEST: kehilangan fokus (overlay/aplikasi lain) bukan pelanggaran.
    }
}
