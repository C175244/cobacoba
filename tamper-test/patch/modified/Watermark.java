package com.example.soalpintar;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Watermark tengah layar untuk build TAMPER TEST.
 *
 * Ini satu-satunya pembeda visual dari aplikasi asli. Dipasang dari:
 *   - MainActivity.onCreate()  (layar token)
 *   - ExamActivity.onCreate()  (layar ujian / WebView)
 *
 * Lokasi file di project asli:
 *   app/src/main/java/com/example/soalpintar/Watermark.java
 */
final class Watermark {
    private static final String TEXT = "MODIFIKASI\nTAMPER TEST";
    private static final int BG = 0x99000000;

    private Watermark() {
    }

    static void attach(Activity activity) {
        TextView view = new TextView(activity);
        view.setText(TEXT);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setAlpha(0.9f);
        view.setBackgroundColor(BG);
        int pad = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        view.setClickable(false);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setElevation(24f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        activity.addContentView(view, lp);
    }
}
