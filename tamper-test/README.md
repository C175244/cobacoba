# Tamper-Test Build — SoalPintar

> **Khusus pengujian internal oleh pemilik/pengembang aplikasi.**
> Build ini melepas seluruh enforcement sisi klien dan **wajib** tetap memakai
> watermark tengah layar. Jangan menghapus watermark, jangan menyebarkan build
> ini, dan gunakan token uji coba — bukan token ujian sungguhan.

## 1. Tujuan

Menguji: *"Kalau APK dimodifikasi, apakah enforcement bisa dilepas, dan apakah
server tetap terlihat normal?"*

Jawaban dari analisis statis + build ini: **ya, bisa, dan ya, server tetap
terlihat normal.** Sebabnya:

1. Semua penguncian (kiosk, blokir tombol, deteksi keluar/overlay, alarm,
   pelanggaran) berjalan di sisi klien dan bisa di-patch.
2. API aplikasi tidak punya autentikasi/attestation. Siapa pun yang tahu token
   bisa mengirim `heartbeat`/`complete`.
3. Token ujian adalah *bearer secret* di path URL; tidak ada tanda tangan
   permintaan maupun verifikasi integritas klien.

Konsekuensinya: **tunda pemakaian untuk ujian bernilai tinggi** sampai minimal
item P0 di [`HARDENING.md`](HARDENING.md) terpasang.

## 2. Isi folder

```
tamper-test/
├── README.md                  ← dokumen ini
├── HARDENING.md               ← rekomendasi perbaikan (untuk diskusi tim)
├── apk/
│   ├── soalpintar-tampertest-v2.1.apk  ← APK siap instal (sudah ditandatangani)
│   ├── patch_apk.py           ← patch smali/manifest/layout
│   └── README.md              ← hash, instal, build ulang
├── mock-server/
│   └── server.py              ← API + Google-Form tiruan (opsional, 100% lokal)
└── patch/
    ├── apply_patch.py         ← generator patch (ada assertion + verifikasi)
    ├── original/              ← 7 class asli hasil decompile (referensi)
    ├── modified/              ← hasil patch, termasuk Watermark.java
    ├── CHANGES.diff           ← diff lengkap original → modified
    └── AndroidManifest.xml    ← manifest "aplikasi biasa"
```

## APK siap instal

`apk/soalpintar-tampertest-v2.1.apk` sudah ditandatangani (v1+v2+v3) dan bisa
langsung dipasang:

```bash
adb uninstall com.example.soalpintar
adb install -r tamper-test/apk/soalpintar-tampertest-v2.1.apk
```

Detail hash, sertifikat, dan cara build ulang ada di [`apk/README.md`](apk/README.md).

## 3. Perilaku yang diubah

| Fitur asli | Di build ini |
|---|---|
| `startLockTask` / `setLockTaskPackages` / `setStatusBarDisabled` | dihapus — tidak ada kiosk |
| `sembunyikanSystemUI` (immersive, dipaksa tiap 500 ms) | no-op — status/nav bar normal |
| `dispatchKeyEvent` blokir Home/Recent/Power/Volume/Back | `super` — semua tombol normal |
| `onPause` / `onStop` / `onUserLeaveHint` → `paksakembali` | no-op — bebas keluar aplikasi |
| `onWindowFocusChanged` deteksi overlay | no-op — overlay/aplikasi lain diizinkan |
| `onMultiWindowModeChanged` larangan split-screen | `super` — split-screen diizinkan |
| `FLAG_SECURE` + flag fullscreen/lock-screen | dihapus — screenshot & rekam layar normal |
| `bunyikanAlarm` (volume ALARM 88% + nada) | no-op — tidak ada perubahan volume/bunyi |
| `memicuPelanggaran` (hitung + kirim + banner + blokir) | no-op — tidak ada pelanggaran |
| `suspendUjian` / `cekBukaBlokir` / overlay blokir | no-op — tidak pernah muncul |
| `POST violation` / `POST block` | tidak pernah dipanggil |
| `POST heartbeat` / `POST complete` | **tetap** — aktivitas normal |
| Timer dari `expires_at` | tetap |
| Simpan/pulihkan draft jawaban | tetap |

## 4. Watermark (satu-satunya pembeda visual)

Build ini **tidak** mengubah label, ikon, versi, teks beranda, maupun header
ujian. Satu-satunya pembeda adalah kotak watermark di tengah layar:

```
┌──────────────────────┐
│      MODIFIKASI      │
│     TAMPER TEST      │
└──────────────────────┘
```

### Lokasi file & kode

| Item | Lokasi |
|---|---|
| Class watermark | `tamper-test/patch/modified/Watermark.java` |
| Di project Android Studio | `app/src/main/java/com/example/soalpintar/Watermark.java` |
| Dipanggil (layar token) | `MainActivity.java` → akhir `onCreate()` → `Watermark.attach(this);` |
| Dipanggil (layar ujian) | `ExamActivity.java` → akhir `onCreate()` → `Watermark.attach(this);` |

Isi method utamanya:

```java
static void attach(Activity activity) {
    TextView view = new TextView(activity);
    view.setText("MODIFIKASI\nTAMPER TEST");
    view.setTextColor(Color.WHITE);
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    view.setTypeface(Typeface.DEFAULT_BOLD);
    view.setGravity(Gravity.CENTER);
    view.setAlpha(0.9f);
    view.setBackgroundColor(0x99000000);
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
```

Watermark dipasang lewat `addContentView`, jadi muncul di atas konten (termasuk
di atas WebView) dan tidak menangkap sentuhan.

## 5. Konfigurasi server

Base API diatur di **satu tempat**:

| File | Baris |
|---|---|
| `tamper-test/patch/modified/AppConfig.java` | `private static final String TEST_API_BASE = "...";` |
| `tamper-test/patch/modified/BuildConfig.java` | `API_BASE_URL` (informatif) |

Default saat ini:

```java
private static final String TEST_API_BASE = "https://soalpintar.solusiedu.id/api/";
```

Ganti nilainya kalau server Anda berbeda. Syarat: HTTPS dan diakhiri `/api/`.
Untuk mock lokal di emulator, pakai `http://10.0.2.2:8080/api/`.

> Saat menguji ke server asli, buat **token uji coba** khusus dan pantau
> dashboard. Data uji akan tercatat sebagai sesi biasa.

## 6. Cara build & uji

### a. Build
Lihat [`patch/README.md`](patch/README.md). Ringkas:
- Terapkan perubahan ke project Android Studio Anda (disarankan), atau pakai
  `patch/modified/` sebagai referensi.
- Tambahkan `Watermark.java` dan dua pemanggilan `Watermark.attach(this);`.
- Tanda tangan dengan debug key Anda sendiri.

### b. Uji
Pasang di perangkat uji, masukkan token dari dashboard, isi identitas, kerjakan
form, lalu kirim. Alur `validate-token` → `form_url` → `heartbeat` →
`complete` berjalan sama seperti aplikasi asli.

### c. Mock server (opsional)
```bash
cd tamper-test/mock-server
python3 server.py          # http://127.0.0.1:8080
```
Token uji: `TES123`. Dashboard: `http://127.0.0.1:8080/`.

## 7. Checklist pengujian

| # | Aksi | Hasil yang diharapkan |
|---|---|---|
| 1 | Buka aplikasi | watermark tengah layar terlihat |
| 2 | Tekan Home / Recent / Back | keluar normal, tidak ada banner/pelanggaran |
| 3 | Buka aplikasi lain, kembali | tidak dipaksa kembali, tidak ada pelanggaran |
| 4 | Split-screen | berjalan normal |
| 5 | Screenshot / rekam layar | berhasil (tidak hitam) |
| 6 | Tekan tombol volume | volume berubah normal, tidak ada alarm |
| 7 | Tarik notification shade | normal, tidak ada peringatan |
| 8 | Isi form, tutup paksa app, buka lagi | draft dipulihkan via `usp=pp_url` |
| 9 | Kirim form | terdeteksi "telah direkam" → `POST complete` → keluar |
| 10 | Lihat dashboard server | `violation = 0`, `block = 0`, heartbeat bertambah |

Kalau ada `violation`/`block` > 0, berarti masih ada jalur enforcement yang
belum terlepas.

## 8. Batasan & etika

- **Watermark wajib dipertahankan.** Tanpa watermark, build ini menjadi alat
  nyontek yang tidak bisa dibedakan dari aplikasi asli.
- Gunakan hanya di perangkat uji milik tim. Jangan dipasang di perangkat murid.
- Gunakan token uji coba; jangan pakai token ujian bernilai.
- Jangan menyebarkan APK/source build ini.
- Uji hanya untuk mengukur risiko, bukan untuk produksi.
