#!/usr/bin/env python3
"""
Menghasilkan source "tamper-test build" dari source hasil decompile.

Build ini SENGAJA mematikan seluruh enforcement sisi klien supaya tim developer
bisa menguji: (a) apakah aplikasi memang mudah dimodifikasi, dan (b) bagaimana
server bereaksi ketika klien nakal. Build ini:

  * TIDAK mengunci layar / lock task
  * TIDAK menyembunyikan status/navigation bar
  * TIDAK memblokir tombol apa pun
  * TIDAK mendeteksi keluar aplikasi
  * TIDAK mendeteksi overlay / kehilangan fokus
  * TIDAK melarang split-screen
  * TIDAK memakai FLAG_SECURE
  * TIDAK membunyikan alarm / mengubah volume
  * TIDAK mengirim violation / block, dan tidak menampilkan overlay blokir

Heartbeat dan complete TETAP dikirim (aktivitas normal), sehingga di dashboard
server build ini terlihat seperti peserta biasa dengan pelanggaran 0.

Pemakaian: python3 apply_patch.py [--check]
"""

from __future__ import annotations

import argparse
import difflib
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ORIGINAL = HERE / "original"
MODIFIED = HERE / "modified"
RES_SRC = HERE.parent.parent / "analysis" / "src" / "resources"


def patch(text: str, name: str, pattern: str, repl: str, count: int = 1) -> str:
    new, n = re.subn(pattern, lambda _m: repl, text, count=0, flags=re.S)
    if n != count:
        raise SystemExit(f"[FAIL] {name}: expected {count} match(es), got {n}")
    print(f"[ok] {name}")
    return new


def build_exam_activity(text: str) -> str:
    text = patch(
        text,
        "ExamActivity.setupKioskMode",
        r"    private void setupKioskMode\(\) \{.*?\n    \}",
        "    private void setupKioskMode() {\n"
        "        // TAMPER TEST: kiosk / lock task dimatikan sepenuhnya.\n"
        '        Log.d(TAG, "TAMPER TEST: setupKioskMode() dilewati");\n'
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.sembunyikanSystemUI",
        r"    public void sembunyikanSystemUI\(\) \{.*?\n    \}",
        "    public void sembunyikanSystemUI() {\n"
        "        // TAMPER TEST: status/navigation bar dibiarkan normal.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.bunyikanAlarm",
        r"    private void bunyikanAlarm\(\) \{.*?\n    \}",
        "    private void bunyikanAlarm() {\n"
        "        // TAMPER TEST: tidak ada perubahan volume dan tidak ada bunyi alarm.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.memicuPelanggaran",
        r"    public void memicuPelanggaran\(String str, String str2\) \{.*?\n    \}",
        "    public void memicuPelanggaran(String str, String str2) {\n"
        "        // TAMPER TEST: pelanggaran tidak dicatat, tidak dilaporkan,\n"
        "        // tidak ada alarm, banner, maupun overlay blokir.\n"
        '        Log.d(TAG, "TAMPER TEST: pelanggaran diabaikan (" + str2 + ")");\n'
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.paksakembali",
        r"    private void paksakembali\(\) \{.*?\n    \}",
        "    private void paksakembali() {\n"
        "        // TAMPER TEST: tidak memaksa pengguna kembali ke aplikasi.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.suspendUjian",
        r"    private void suspendUjian\(\) \{.*?\n    \}",
        "    private void suspendUjian() {\n"
        "        // TAMPER TEST: tidak ada overlay blokir.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.cekBukaBlokir",
        r"    private void cekBukaBlokir\(\) \{.*?\n    \}",
        "    private void cekBukaBlokir() {\n"
        "        // TAMPER TEST: tidak ada pemeriksaan status blokir.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.dispatchKeyEvent",
        r"    public boolean dispatchKeyEvent\(KeyEvent keyEvent\) \{.*?\n    \}",
        "    public boolean dispatchKeyEvent(KeyEvent keyEvent) {\n"
        "        // TAMPER TEST: Home, Recent, Power, Volume, dan Back berfungsi normal.\n"
        "        return super.dispatchKeyEvent(keyEvent);\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.onWindowFocusChanged",
        r"    public void onWindowFocusChanged\(boolean z\) \{.*?\n    \}",
        "    public void onWindowFocusChanged(boolean z) {\n"
        "        super.onWindowFocusChanged(z);\n"
        "        // TAMPER TEST: kehilangan fokus (overlay/aplikasi lain) bukan pelanggaran.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.onMultiWindowModeChanged",
        r"    public void onMultiWindowModeChanged\(boolean z\) \{.*?\n    \}",
        "    public void onMultiWindowModeChanged(boolean z) {\n"
        "        super.onMultiWindowModeChanged(z);\n"
        "        // TAMPER TEST: split-screen / multi-window diizinkan.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.onResume",
        r"    protected void onResume\(\) \{.*?\n    \}",
        "    protected void onResume() {\n"
        "        super.onResume();\n"
        "        // TAMPER TEST: tanpa startLockTask() dan tanpa menyembunyikan system UI.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.onPause",
        r"    protected void onPause\(\) \{.*?\n    \}",
        "    protected void onPause() {\n"
        "        super.onPause();\n"
        "        // TAMPER TEST: keluar aplikasi bukan pelanggaran.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.onStop",
        r"    protected void onStop\(\) \{.*?\n    \}",
        "    protected void onStop() {\n"
        "        super.onStop();\n"
        "        // TAMPER TEST: keluar aplikasi bukan pelanggaran.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.onUserLeaveHint",
        r"    protected void onUserLeaveHint\(\) \{.*?\n    \}",
        "    protected void onUserLeaveHint() {\n"
        "        super.onUserLeaveHint();\n"
        "        // TAMPER TEST: Home / Recent tidak dianggap pelanggaran.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.window flags (FLAG_SECURE + fullscreen)",
        r"        getWindow\(\)\.setFlags\(8192, 8192\);\n        getWindow\(\)\.addFlags\(4719744\);",
        "        getWindow().addFlags(128); // TAMPER TEST: hanya FLAG_KEEP_SCREEN_ON",
    )
    text = patch(
        text,
        "ExamActivity.onCreate enforcement startup",
        r"        sembunyikanSystemUI\(\);\n        setupKioskMode\(\);",
        "        // TAMPER TEST: sembunyikanSystemUI() & setupKioskMode() dilewati.",
    )
    text = patch(
        text,
        "ExamActivity.onCreate violation restore",
        r"            int violations = DraftStore\.violations\(this, str\);\n"
        r"            this\.jumlahPelanggaran = violations;\n"
        r"            if \(violations >= this\.maksPelanggaran\) \{\n"
        r"                this\.jumlahPelanggaran = 0;\n"
        r"                DraftStore\.setViolations\(this, this\.tokenCode, 0\);\n"
        r"            \}",
        "            this.jumlahPelanggaran = 0;\n"
        "            DraftStore.setViolations(this, str, 0);",
    )
    text = patch(
        text,
        "ExamActivity.onCreate lock handler",
        r"        this\.lockEnforceHandler\.post\(this\.lockEnforceRunnable\);",
        "        // TAMPER TEST: lockEnforceHandler tidak dijalankan.",
    )
    text = patch(
        text,
        "ExamActivity.lepasKunci",
        r"            this\.audioManager\.setStreamVolume\(4, this\.originalAlarmVolume, 0\);\n"
        r"            if \(this\.isDeviceOwner\) \{\n"
        r"                \(\(DevicePolicyManager\) getSystemService\(\"device_policy\"\)\)\.setStatusBarDisabled\(new ComponentName\(this, \(Class<\?>\) AdminReceiver\.class\), false\);\n"
        r"            \}\n"
        r"            stopLockTask\(\);",
        "            // TAMPER TEST: tanpa perubahan volume, tanpa stopLockTask()/setStatusBarDisabled().",
    )
    text = patch(
        text,
        "ExamActivity.unused force-return helper",
        r"    /\* synthetic \*/ void m75x2530f8c7\(\) \{.*?\n    \}",
        "    /* synthetic */ void m75x2530f8c7() {\n"
        "        // TAMPER TEST: helper paksa-kembali dinonaktifkan.\n"
        "    }",
    )
    text = patch(
        text,
        "ExamActivity.back callback",
        r"        getOnBackPressedDispatcher\(\)\.addCallback\(this, new OnBackPressedCallback\(true\) \{.*?\n        \}\);",
        "        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(false) { // from class: com.example.soalpintar.ExamActivity.3\n"
        "            @Override // androidx.activity.OnBackPressedCallback\n"
        "            public void handleOnBackPressed() {\n"
        "                // TAMPER TEST: tombol Back normal (callback nonaktif).\n"
        "            }\n"
        "        });",
    )
    text = patch(
        text,
        "ExamActivity.watermark overlay",
        r"        this\.heartbeatHandler\.post\(this\.heartbeatRunnable\);\n    \}",
        "        this.heartbeatHandler.post(this.heartbeatRunnable);\n"
        "        Watermark.attach(this);\n"
        "    }",
    )
    return text


def build_app_config(text: str) -> str:
    text = patch(
        text,
        "AppConfig.test base constant",
        r'    private static final String PRODUCTION_API_BASE = "https://soalpintar\.solusiedu\.id/api/";',
        '    private static final String PRODUCTION_API_BASE = "https://soalpintar.solusiedu.id/api/";\n'
        '    // TAMPER TEST: arahkan ke base API server Anda (wajib diakhiri "/api/").\n'
        '    // Untuk mock lokal di emulator, pakai: "http://10.0.2.2:8080/api/"\n'
        '    private static final String TEST_API_BASE = "https://soalpintar.solusiedu.id/api/";',
    )
    text = patch(
        text,
        "AppConfig.allowlist",
        r'    private static final String\[\] ALLOWED_DOMAINS = \{"solusiedu\.id"\};',
        '    private static final String[] ALLOWED_DOMAINS = {"solusiedu.id", "10.0.2.2", "localhost", "127.0.0.1"};',
    )
    text = patch(
        text,
        "AppConfig.apiBase uses test base",
        r'        return isCustomTestBuild\(\) \? "https://soalpintar\.solusiedu\.id/api/" : cached\(context\)\.apiBase;',
        "        return isCustomTestBuild() ? TEST_API_BASE : cached(context).apiBase;",
    )
    text = patch(
        text,
        "AppConfig.isCustomTestBuild",
        r"    private static boolean isCustomTestBuild\(\) \{\n        return false;\n    \}",
        "    private static boolean isCustomTestBuild() {\n"
        "        // TAMPER TEST: paksa memakai TEST_API_BASE, jangan pernah ke produksi.\n"
        "        return true;\n"
        "    }",
    )
    text = patch(
        text,
        "AppConfig.cached fallback",
        r'new AppConfig\(new JSONObject\(prefs\(context\)\.getString\("config_json", "\{\}"\)\), "https://soalpintar\.solusiedu\.id/api/"\)',
        'new AppConfig(new JSONObject(prefs(context).getString("config_json", "{}")), TEST_API_BASE)',
    )
    text = patch(
        text,
        "AppConfig.cached catch fallback",
        r'new AppConfig\(new JSONObject\(\), "https://soalpintar\.solusiedu\.id/api/"\)',
        "new AppConfig(new JSONObject(), TEST_API_BASE)",
    )
    text = patch(
        text,
        "AppConfig no production retry",
        r"        if \(!z \|\| str\.equals\(\"https://soalpintar\.solusiedu\.id/api/\"\)\) \{\n"
        r"            callback\.onConfig\(cached\(context\)\);\n"
        r"        \} else \{\n"
        r"            fetch\(context, \"https://soalpintar\.solusiedu\.id/api/\", false, callback\);\n"
        r"        \}",
        "        // TAMPER TEST: jangan pernah fallback ke server produksi.\n"
        "        callback.onConfig(cached(context));",
    )
    return text


def build_exam_api(text: str) -> str:
    text = patch(
        text,
        "ExamApi double-send fix",
        r"            if \(!\"block\"\.equals\(str2\) && !\"complete\"\.equals\(str2\)\) \{\n"
        r"                i2 = 2;\n"
        r"                jsonObjectRequest\.setRetryPolicy\(new DefaultRetryPolicy\(8000, i2, 1\.5f\)\);\n"
        r"                jsonObjectRequest\.setShouldCache\(false\);\n"
        r"                queue\(context\)\.add\(jsonObjectRequest\);\n"
        r"            \}\n"
        r"            i2 = 4;\n"
        r"            jsonObjectRequest\.setRetryPolicy\(new DefaultRetryPolicy\(8000, i2, 1\.5f\)\);\n"
        r"            jsonObjectRequest\.setShouldCache\(false\);\n"
        r"            queue\(context\)\.add\(jsonObjectRequest\);",
        '            i2 = ("block".equals(str2) || "complete".equals(str2)) ? 4 : 2;\n'
        "            jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(8000, i2, 1.5f));\n"
        "            jsonObjectRequest.setShouldCache(false);\n"
        "            queue(context).add(jsonObjectRequest);",
    )
    return text


def build_main_activity(text: str) -> str:
    text = patch(
        text,
        "MainActivity.watermark overlay",
        r"        tampilkanIdentitasTersimpan\(\);\n    \}",
        "        tampilkanIdentitasTersimpan();\n"
        "        Watermark.attach(this);\n"
        "    }",
    )
    return text


def build_admin_receiver(text: str) -> str:
    return patch(
        text,
        "AdminReceiver.onEnabled",
        r"    public void onEnabled\(Context context, Intent intent\) \{.*?\n    \}",
        "    public void onEnabled(Context context, Intent intent) {\n"
        "        super.onEnabled(context, intent);\n"
        "        // TAMPER TEST: receiver ini tidak didaftarkan di manifest dan tidak\n"
        "        // menetapkan lock task policy apa pun.\n"
        "    }",
    )


def build_build_config(text: str) -> str:
    return patch(
        text,
        "BuildConfig.API_BASE_URL",
        r'    public static final String API_BASE_URL = "https://soalpintar\.solusiedu\.id/api/";',
        '    public static final String API_BASE_URL = "https://soalpintar.solusiedu.id/api/";',
    )


WATERMARK_JAVA = '''package com.example.soalpintar;

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
    private static final String TEXT = "MODIFIKASI\\nTAMPER TEST";
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
'''


FORBIDDEN = [
    ("startLockTask(", "kiosk lock"),
    ("stopLockTask(", "kiosk unlock"),
    ("setLockTaskPackages(", "lock task policy"),
    ("setStatusBarDisabled(", "status bar policy"),
    ("setStreamVolume(", "volume change"),
    ("setSystemUiVisibility(", "system UI hiding"),
    ("moveTaskToFront(", "force return"),
    ("FLAG_SECURE", "screenshot blocking"),
    ("8192, 8192", "FLAG_SECURE"),
    ("4719744", "fullscreen/lock flags"),
]


def verify() -> None:
    problems = []
    for path in sorted(MODIFIED.glob("*.java")):
        for lineno, line in enumerate(path.read_text().splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            for token, label in FORBIDDEN:
                if token in line:
                    problems.append(f"{path.name}:{lineno}: {label} -> {stripped[:100]}")
    if problems:
        print("\n[VERIFY] masih ada pemanggilan terlarang:")
        for p in problems:
            print("  " + p)
        raise SystemExit(1)
    print("[verify] tidak ada pemanggilan enforcement tersisa")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="hanya periksa hasil, tidak menulis file")
    args = ap.parse_args()

    if not ORIGINAL.exists():
        raise SystemExit(f"folder original tidak ditemukan: {ORIGINAL}")

    MODIFIED.mkdir(parents=True, exist_ok=True)
    builders = {
        "ExamActivity.java": build_exam_activity,
        "AppConfig.java": build_app_config,
        "ExamApi.java": build_exam_api,
        "MainActivity.java": build_main_activity,
        "BuildConfig.java": build_build_config,
        "AdminReceiver.java": build_admin_receiver,
    }

    for name, builder in builders.items():
        src = ORIGINAL / name
        out_path = MODIFIED / name
        out_path.write_text(builder(src.read_text()))
        print(f"[write] {out_path.relative_to(HERE.parent.parent)}")

    for name in ("DraftStore.java",):
        shutil.copy2(ORIGINAL / name, MODIFIED / name)
        print(f"[copy ] {name}")

    # diff ringkas
    diff_path = HERE / "CHANGES.diff"
    chunks = []
    for name in builders:
        a = (ORIGINAL / name).read_text().splitlines(keepends=True)
        b = (MODIFIED / name).read_text().splitlines(keepends=True)
        chunks.extend(
            difflib.unified_diff(a, b, fromfile=f"original/{name}", tofile=f"modified/{name}")
        )
    # watermark class
    wm_path = MODIFIED / "Watermark.java"
    wm_path.write_text(WATERMARK_JAVA)
    print(f"[write] {wm_path.relative_to(HERE.parent.parent)}")

    wm_lines = WATERMARK_JAVA.splitlines(keepends=True)
    chunks.append("--- /dev/null\n")
    chunks.append("+++ modified/Watermark.java\n")
    chunks.append(f"@@ -0,0 +1,{len(wm_lines)} @@\n")
    chunks.extend("+" + line for line in wm_lines)

    diff_path.write_text("".join(chunks))
    print(f"[write] {diff_path.relative_to(HERE.parent.parent)}")

    verify()
    print("\nSelesai. Source termodifikasi ada di tamper-test/patch/modified/")


if __name__ == "__main__":
    main()
