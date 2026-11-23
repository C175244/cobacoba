package com.example.soalpintar;

import android.animation.ObjectAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Property;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.view.ViewCompat;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.soalpintar.AppConfig;
import com.example.soalpintar.ExamApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class MainActivity extends AppCompatActivity {
    private CircularProgressIndicator btnProgress;
    private MaterialButton btnSubmit;
    private AppConfig config;
    private View layoutCard;
    private TextInputEditText tokenInput;
    private TextInputLayout tokenLayout;
    private TextView tvAnnouncement;
    private TextView tvIdentity;
    private TextView tvMaintenance;
    private TextView tvStatus;
    private AlertDialog updateDialog;

    private void animasiMasuk() {
        View[] viewArr = {findViewById(R.id.layoutHeader), this.layoutCard, findViewById(R.id.layoutFooter)};
        for (int i = 0; i < 3; i++) {
            viewArr[i].setTranslationY(40.0f);
            viewArr[i].animate().alpha(1.0f).translationY(0.0f).setStartDelay(i * 120).setDuration(520L).setInterpolator(new DecelerateInterpolator(2.0f)).start();
        }
        ObjectAnimator ofFloat = ObjectAnimator.ofFloat(findViewById(R.id.liveDot), (Property<View, Float>) View.ALPHA, 1.0f, 0.35f);
        ofFloat.setDuration(900L);
        ofFloat.setRepeatMode(2);
        ofFloat.setRepeatCount(-1);
        ofFloat.start();
    }

    private void bukaUjian(String str, JSONObject jSONObject) throws JSONException {
        Intent intent = new Intent(this, (Class<?>) ExamActivity.class);
        intent.putExtra("FORM_URL", jSONObject.getString("form_url"));
        intent.putExtra("GURU", jSONObject.optString("guru", "Guru"));
        intent.putExtra("TOPIC", jSONObject.optString("topic", "Ujian"));
        intent.putExtra("TOKEN_CODE", str);
        intent.putExtra("MAX_VIOLATIONS", jSONObject.optInt("max_violations", 3));
        if (!jSONObject.isNull("expires_at")) {
            intent.putExtra("EXPIRES_AT", jSONObject.getString("expires_at"));
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void gagal(String str) {
        setLoading(false);
        tampilkanStatus(str);
        goyang(this.layoutCard);
    }

    private void getar() {
        Vibrator vibrator;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService("vibrator_manager");
                vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) getSystemService("vibrator");
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] jArr = {0, 70, 80, 110};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(jArr, -1));
                } else {
                    vibrator.vibrate(jArr, -1);
                }
            }
        } catch (Exception unused) {
        }
    }

    private void goyang(View view) {
        ObjectAnimator.ofFloat(view, (Property<View, Float>) View.TRANSLATION_X, 0.0f, 14.0f, -14.0f, 9.0f, -9.0f, 4.0f, -4.0f, 0.0f).setDuration(420L).start();
        getar();
    }

    static /* synthetic */ boolean lambda$tampilkanSheetIdentitas$6(Runnable runnable, TextView textView, int i, KeyEvent keyEvent) {
        if (i != 6) {
            return false;
        }
        runnable.run();
        return true;
    }

    private void mulai() {
        AppConfig appConfig = this.config;
        if (appConfig != null && appConfig.maintenance) {
            tampilkanStatus(this.config.maintenanceMessage.isEmpty() ? "Server sedang dalam perbaikan." : this.config.maintenanceMessage);
            goyang(this.layoutCard);
            return;
        }
        AppConfig appConfig2 = this.config;
        if (appConfig2 != null && appConfig2.needsUpdate()) {
            tampilkanWajibUpdate(this.config);
            return;
        }
        String upperCase = textOf(this.tokenInput).toUpperCase(Locale.ROOT);
        if (upperCase.length() < 4) {
            this.tokenLayout.setError("Masukkan token dari guru");
            goyang(this.layoutCard);
        } else {
            sembunyikanKeyboard();
            tampilkanSheetIdentitas(upperCase);
        }
    }

    private void sembunyikanKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
        if (inputMethodManager == null || getCurrentFocus() == null) {
            return;
        }
        inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sembunyikanStatus() {
        if (this.tvStatus.getVisibility() == 0) {
            this.tvStatus.animate().alpha(0.0f).setDuration(150L).withEndAction(new Runnable() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m92lambda$sembunyikanStatus$9$comexamplesoalpintarMainActivity();
                }
            }).start();
        }
    }

    private void setLoading(boolean z) {
        AppConfig appConfig = this.config;
        this.btnSubmit.setEnabled((z || (appConfig != null && (appConfig.maintenance || this.config.needsUpdate()))) ? false : true);
        this.btnSubmit.setText(z ? "" : "MULAI UJIAN");
        this.btnSubmit.setIconTint(z ? ColorStateList.valueOf(ViewCompat.MEASURED_SIZE_MASK) : ColorStateList.valueOf(-1));
        this.btnProgress.setVisibility(z ? 0 : 8);
        this.tokenInput.setEnabled(!z);
    }

    private boolean sudahKedaluwarsa(JSONObject jSONObject) {
        if (jSONObject.isNull("expires_at")) {
            return false;
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parse = simpleDateFormat.parse(jSONObject.getString("expires_at"));
            if (parse != null) {
                if (parse.before(new Date())) {
                    return true;
                }
            }
        } catch (Exception unused) {
        }
        return false;
    }

    private void tampilkanAtauSembunyikan(final TextView textView, String str) {
        if (str == null) {
            if (textView.getVisibility() == 0) {
                textView.animate().alpha(0.0f).setDuration(150L).withEndAction(new Runnable() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        textView.setVisibility(8);
                    }
                }).start();
            }
        } else {
            textView.setText(str);
            if (textView.getVisibility() != 0) {
                textView.setAlpha(0.0f);
                textView.setVisibility(0);
                textView.animate().alpha(1.0f).setDuration(250L).start();
            }
        }
    }

    private void tampilkanIdentitasTersimpan() {
        ExamApi.Identity loadIdentity = ExamApi.loadIdentity(this);
        this.tvIdentity.setText(loadIdentity.isComplete() ? "Masuk sebagai " + loadIdentity.name + " · " + loadIdentity.kelas + " · absen " + loadIdentity.absen + " · v.2.1" : "Token diberikan oleh guru/pengawas ujian · v.2.1");
    }

    private void tampilkanSheetIdentitas(final String str) {
        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        final View inflate = getLayoutInflater().inflate(R.layout.sheet_identity, (ViewGroup) null);
        bottomSheetDialog.setContentView(inflate);
        bottomSheetDialog.getBehavior().setState(3);
        bottomSheetDialog.getBehavior().setSkipCollapsed(true);
        final TextInputLayout textInputLayout = (TextInputLayout) inflate.findViewById(R.id.nameLayout);
        final TextInputLayout textInputLayout2 = (TextInputLayout) inflate.findViewById(R.id.classLayout);
        final TextInputLayout textInputLayout3 = (TextInputLayout) inflate.findViewById(R.id.absenLayout);
        final TextInputEditText textInputEditText = (TextInputEditText) inflate.findViewById(R.id.inputName);
        final TextInputEditText textInputEditText2 = (TextInputEditText) inflate.findViewById(R.id.inputClass);
        final TextInputEditText textInputEditText3 = (TextInputEditText) inflate.findViewById(R.id.inputAbsen);
        ((TextView) inflate.findViewById(R.id.sheetSubtitle)).setText("Token " + str + " · isi sesuai identitas di sekolah. Data ini terlihat oleh guru pengawas.");
        ExamApi.Identity loadIdentity = ExamApi.loadIdentity(this);
        textInputEditText.setText(loadIdentity.name);
        textInputEditText2.setText(loadIdentity.kelas);
        textInputEditText3.setText(loadIdentity.absen);
        final Runnable runnable = new Runnable() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m93xad403a11(textInputEditText, textInputEditText2, textInputEditText3, textInputLayout, textInputLayout2, textInputLayout3, inflate, bottomSheetDialog, str);
            }
        };
        inflate.findViewById(R.id.btnContinue).setOnClickListener(new View.OnClickListener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                runnable.run();
            }
        });
        textInputEditText3.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda6
            @Override // android.widget.TextView.OnEditorActionListener
            public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                return MainActivity.lambda$tampilkanSheetIdentitas$6(runnable, textView, i, keyEvent);
            }
        });
        bottomSheetDialog.show();
        if (loadIdentity.name.isEmpty()) {
            textInputEditText.requestFocus();
        }
    }

    private void tampilkanStatus(String str) {
        this.tvStatus.setText(str);
        if (this.tvStatus.getVisibility() != 0) {
            this.tvStatus.setAlpha(0.0f);
            this.tvStatus.setTranslationY(-12.0f);
            this.tvStatus.setVisibility(0);
            this.tvStatus.animate().alpha(1.0f).translationY(0.0f).setDuration(220L).start();
        }
    }

    private void tampilkanWajibUpdate(final AppConfig appConfig) {
        AlertDialog alertDialog = this.updateDialog;
        if (alertDialog == null || !alertDialog.isShowing()) {
            this.updateDialog = new MaterialAlertDialogBuilder(this).setTitle((CharSequence) "Perbarui aplikasi").setMessage((CharSequence) (appConfig.updateMessage.isEmpty() ? "Versi aplikasi Anda sudah tidak didukung. Silakan perbarui." : appConfig.updateMessage)).setCancelable(false).setPositiveButton((CharSequence) "PERBARUI", new DialogInterface.OnClickListener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda3
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m94xf89ef033(appConfig, dialogInterface, i);
                }
            }).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void terapkanConfig(AppConfig appConfig) {
        String str;
        if (isFinishing()) {
            return;
        }
        this.config = appConfig;
        tampilkanAtauSembunyikan(this.tvAnnouncement, appConfig.announcement.isEmpty() ? null : "📢  " + appConfig.announcement);
        TextView textView = this.tvMaintenance;
        if (appConfig.maintenance) {
            str = "🛠  " + (appConfig.maintenanceMessage.isEmpty() ? "Server sedang dalam perbaikan." : appConfig.maintenanceMessage);
        } else {
            str = null;
        }
        tampilkanAtauSembunyikan(textView, str);
        this.btnSubmit.setEnabled((appConfig.maintenance || appConfig.needsUpdate()) ? false : true);
        MaterialButton materialButton = this.btnSubmit;
        materialButton.setAlpha(materialButton.isEnabled() ? 1.0f : 0.5f);
        if (appConfig.needsUpdate()) {
            tampilkanWajibUpdate(appConfig);
            return;
        }
        AlertDialog alertDialog = this.updateDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
            this.updateDialog = null;
        }
    }

    private static String textOf(TextInputEditText textInputEditText) {
        return textInputEditText.getText() == null ? "" : textInputEditText.getText().toString().trim();
    }

    private void validasiToken(final String str, ExamApi.Identity identity) {
        setLoading(true);
        sembunyikanStatus();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(0, ExamApi.validateUrl(this, str, identity), null, new Response.Listener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda0
            @Override // com.android.volley.Response.Listener
            public final void onResponse(Object obj) {
                MainActivity.this.m95lambda$validasiToken$7$comexamplesoalpintarMainActivity(str, (JSONObject) obj);
            }
        }, new Response.ErrorListener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda2
            @Override // com.android.volley.Response.ErrorListener
            public final void onErrorResponse(VolleyError volleyError) {
                MainActivity.this.m96lambda$validasiToken$8$comexamplesoalpintarMainActivity(volleyError);
            }
        });
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1.5f));
        jsonObjectRequest.setShouldCache(false);
        ExamApi.queue(this).add(jsonObjectRequest);
    }

    /* renamed from: lambda$onCreate$0$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m90lambda$onCreate$0$comexamplesoalpintarMainActivity(TextView textView, int i, KeyEvent keyEvent) {
        if (i != 2) {
            return false;
        }
        mulai();
        return true;
    }

    /* renamed from: lambda$onCreate$1$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ void m91lambda$onCreate$1$comexamplesoalpintarMainActivity(View view) {
        mulai();
    }

    /* renamed from: lambda$sembunyikanStatus$9$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ void m92lambda$sembunyikanStatus$9$comexamplesoalpintarMainActivity() {
        this.tvStatus.setVisibility(8);
    }

    /* renamed from: lambda$tampilkanSheetIdentitas$4$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ void m93xad403a11(TextInputEditText textInputEditText, TextInputEditText textInputEditText2, TextInputEditText textInputEditText3, TextInputLayout textInputLayout, TextInputLayout textInputLayout2, TextInputLayout textInputLayout3, View view, BottomSheetDialog bottomSheetDialog, String str) {
        ExamApi.Identity identity = new ExamApi.Identity(textOf(textInputEditText), textOf(textInputEditText2).toUpperCase(Locale.ROOT), textOf(textInputEditText3));
        textInputLayout.setError(identity.name.isEmpty() ? "Nama wajib diisi" : null);
        textInputLayout2.setError(identity.kelas.isEmpty() ? "Wajib" : null);
        textInputLayout3.setError(identity.absen.isEmpty() ? "Wajib" : null);
        if (!identity.isComplete()) {
            goyang(view);
            return;
        }
        ExamApi.saveIdentity(this, identity);
        bottomSheetDialog.dismiss();
        validasiToken(str, identity);
    }

    /* renamed from: lambda$tampilkanWajibUpdate$2$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ void m94xf89ef033(AppConfig appConfig, DialogInterface dialogInterface, int i) {
        if (!appConfig.updateUrl.isEmpty()) {
            try {
                startActivity(new Intent("android.intent.action.VIEW", Uri.parse(appConfig.updateUrl)));
            } catch (Exception unused) {
                tampilkanStatus("Buka link ini untuk memperbarui: " + appConfig.updateUrl);
            }
        }
        this.updateDialog = null;
    }

    /* renamed from: lambda$validasiToken$7$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ void m95lambda$validasiToken$7$comexamplesoalpintarMainActivity(String str, JSONObject jSONObject) {
        try {
            if (!"success".equals(jSONObject.optString(NotificationCompat.CATEGORY_STATUS))) {
                gagal(jSONObject.optString("message", "Token salah atau sudah tidak aktif."));
            } else if (sudahKedaluwarsa(jSONObject)) {
                gagal("Waktu ujian untuk token ini sudah habis.");
            } else {
                bukaUjian(str, jSONObject);
            }
        } catch (JSONException unused) {
            gagal("Data dari server tidak lengkap. Coba lagi.");
        }
    }

    /* renamed from: lambda$validasiToken$8$com-example-soalpintar-MainActivity, reason: not valid java name */
    /* synthetic */ void m96lambda$validasiToken$8$comexamplesoalpintarMainActivity(VolleyError volleyError) {
        gagal(ExamApi.errorMessage(volleyError, "Token salah atau sudah tidak aktif."));
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        this.tokenLayout = (TextInputLayout) findViewById(R.id.tokenLayout);
        this.tokenInput = (TextInputEditText) findViewById(R.id.tokenInput);
        this.btnSubmit = (MaterialButton) findViewById(R.id.btnSubmit);
        this.btnProgress = (CircularProgressIndicator) findViewById(R.id.btnProgress);
        this.tvStatus = (TextView) findViewById(R.id.tvStatus);
        this.tvIdentity = (TextView) findViewById(R.id.tvIdentity);
        ((TextView) findViewById(R.id.tvVersi)).setText("Ujian Terkunci x Google Form v.2.1 (4)");
        this.layoutCard = findViewById(R.id.layoutCard);
        this.tvMaintenance = (TextView) findViewById(R.id.tvMaintenance);
        this.tvAnnouncement = (TextView) findViewById(R.id.tvAnnouncement);
        terapkanConfig(AppConfig.cached(this));
        getSharedPreferences("soalpintar_prefs", 0).edit().remove("token_dipakai").apply();
        animasiMasuk();
        this.tokenInput.addTextChangedListener(new TextWatcher() { // from class: com.example.soalpintar.MainActivity.1
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
                String replaceAll = editable.toString().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
                if (!replaceAll.equals(editable.toString())) {
                    MainActivity.this.tokenInput.setText(replaceAll);
                    MainActivity.this.tokenInput.setSelection(replaceAll.length());
                }
                MainActivity.this.tokenInput.setLetterSpacing(replaceAll.isEmpty() ? 0.0f : 0.3f);
                MainActivity.this.tokenLayout.setError(null);
                MainActivity.this.sembunyikanStatus();
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }
        });
        this.tokenInput.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda7
            @Override // android.widget.TextView.OnEditorActionListener
            public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                return MainActivity.this.m90lambda$onCreate$0$comexamplesoalpintarMainActivity(textView, i, keyEvent);
            }
        });
        this.btnSubmit.setOnClickListener(new View.OnClickListener() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m91lambda$onCreate$1$comexamplesoalpintarMainActivity(view);
            }
        });
        tampilkanIdentitasTersimpan();
        Watermark.attach(this);
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        setLoading(false);
        tampilkanIdentitasTersimpan();
        AppConfig.refresh(this, new AppConfig.Callback() { // from class: com.example.soalpintar.MainActivity$$ExternalSyntheticLambda9
            @Override // com.example.soalpintar.AppConfig.Callback
            public final void onConfig(AppConfig appConfig) {
                MainActivity.this.terapkanConfig(appConfig);
            }
        });
    }
}
