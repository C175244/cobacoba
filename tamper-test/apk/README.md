# APK siap instal — TAMPER TEST

> Build uji coba, **bukan** untuk produksi. Watermark tengah layar wajib ada.

## Artefak

| Item | Nilai |
|---|---|
| File | `soalpintar-tampertest-v2.1.apk` |
| Ukuran | 5.460.360 bytes |
| SHA-256 | `9b41c33b0996704bf1ccb9b3eeae1e53657bdde60ce030189267464f87c51b92` |
| Package | `com.example.soalpintar` |
| versionName / versionCode | `2.1` / `4` |
| Label | `SoalPintar v.2.1` (sama seperti asli) |
| Signature | v1 + v2 + v3, `CN=TamperTest` |
| Sertifikat SHA-256 | `caedab1d189af29cfd66d3a92f1c6d07d0351464a407de1cd4a300d1d779022d` |
| Base API | `https://soalpintar.solusiedu.id/api/` |

## Instal

Karena ditandatangani dengan kunci berbeda dari APK asli, aplikasi asli harus
dihapus dulu:

```bash
adb uninstall com.example.soalpintar
adb install -r soalpintar-tampertest-v2.1.apk
```

Atau salin APK ke perangkat lalu buka dari file manager (izinkan "install from
unknown sources").

> Hati-hati: `adb uninstall` menghapus data aplikasi asli di perangkat itu.

## Watermark

Tampil di tengah layar, di layar token maupun layar ujian:

```
┌──────────────────────┐
│      MODIFIKASI      │
│     TAMPER TEST      │
└──────────────────────┘
```

Di dalam APK, watermark adalah `TextView` di:

- `res/layout/activity_main.xml` (dibungkus `FrameLayout`, `layout_gravity="center"`)
- `res/layout/activity_exam.xml` (di dalam `FrameLayout` WebView, `layout_gravity="center"`)

Di jalur source Android Studio, padanannya ada di
`tamper-test/patch/modified/Watermark.java` dan dipanggil dari
`MainActivity.onCreate()` + `ExamActivity.onCreate()`.

## Yang diubah

| Area | Perubahan |
|---|---|
| Manifest | hapus `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`; hapus receiver `AdminReceiver`; hapus `showWhenLocked`, `turnScreenOn`, `autoRemoveFromRecents`, `resizeableActivity="false"`; `allowBackup=false` |
| `ExamActivity.onCreate` | `FLAG_SECURE` dilepas (`setFlags(0,0)`), hanya `KEEP_SCREEN_ON` (`addFlags(0x80)`) |
| `onResume` | hanya `super.onResume()` — tanpa `startLockTask` |
| `dispatchKeyEvent` | diteruskan ke `super` — Home/Recent/Power/Volume/Back normal |
| `setupKioskMode`, `sembunyikanSystemUI` | no-op |
| `bunyikanAlarm` | no-op |
| `memicuPelanggaran`, `paksakembali`, `suspendUjian`, `cekBukaBlokir` | no-op |
| `lambda$onMultiWindowModeChanged$6` | no-op — split-screen diizinkan |
| `lepasKunci` | `setStreamVolume` dihapus |
| Layout | watermark tengah layar |

Yang tetap berjalan: `validate-token`, buka `form_url`, `heartbeat`, deteksi
"telah direkam", `POST complete`, timer, dan pemulihan draft.

## Build ulang

```bash
# 1. decode
java -jar apktool.jar d -f -o work soalpintar-v2-release.apk

# 2. patch
python3 patch_apk.py work

# 3. build
java -jar apktool.jar b work -o unsigned.apk

# 4. sign (buat keystore test dulu kalau belum ada)
keytool -genkeypair -v -keystore tampertest.keystore -alias tampertest \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass tampertest -keypass tampertest \
  -dname "CN=TamperTest,OU=QA,O=Local,L=Local,ST=Local,C=ID"

java -jar uber-apk-signer.jar --apks unsigned.apk \
  --ks tampertest.keystore --ksAlias tampertest \
  --ksPass tampertest --ksKeyPass tampertest --allowResign --overwrite
```

`tampertest.keystore` adalah kunci test sekali pakai dan **tidak di-commit**
(masuk `.gitignore`). Simpan salinannya kalau ingin update APK tanpa uninstall.
Jangan dipakai untuk produksi.

## Catatan

- APK ini **belum diuji runtime** di environment pembuatan (tidak ada
  perangkat/emulator). Uji dulu di satu perangkat sebelum dipakai di banyak
  perangkat.
- Gunakan token uji coba dari dashboard, bukan token ujian bernilai.
- Jangan hapus watermark dan jangan sebarkan APK ini.
