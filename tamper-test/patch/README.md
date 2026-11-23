# Cara membangun tamper-test build

> Gunakan **source asli project** Anda (Android Studio). Folder `modified/` di
> sini adalah referensi patch yang sudah diverifikasi (lihat `CHANGES.diff`),
> bukan project Gradle yang siap dikompilasi.

## Opsi A — terapkan ke project asli (disarankan)

1. Pakai `modified/*.java` sebagai pembanding, lalu terapkan perubahan yang
   sama ke source asli Anda.
2. Tambahkan `modified/Watermark.java` ke
   `app/src/main/java/com/example/soalpintar/Watermark.java`.
3. Tambahkan `Watermark.attach(this);` di akhir `onCreate()` pada
   `MainActivity` dan `ExamActivity`.
4. Ganti `AndroidManifest.xml` dengan yang ada di folder ini.
5. Di `app/build.gradle`:

```gradle
android {
    defaultConfig {
        applicationId "com.example.soalpintar"
        // kalau ingin terpisah dari app asli, aktifkan baris ini:
        // applicationIdSuffix ".tampertest"
    }
    buildTypes {
        release {
            signingConfig signingConfigs.debug  // hanya untuk pengujian
        }
    }
}
```

6. Atur base API di `AppConfig.java`:

```java
private static final String TEST_API_BASE = "https://soalpintar.solusiedu.id/api/";
```

7. Build, pasang di perangkat uji, lalu uji dengan token uji coba dari dashboard.

> Jangan menandatangani dengan keystore produksi. Jangan mengunggah build ini.

## Opsi B — jalur apktool/smali (kalau tidak punya source)

Tidak disediakan sebagai skrip siap pakai. Kalau perlu, lakukan sendiri:

```bash
apktool d -f -o work soalpintar-v2-release.apk
# edit work/smali/.../ExamActivity.smali dan MainActivity.smali
apktool b work -o tampertest.apk
apksigner sign --ks debug.keystore ... tampertest.apk
```

Method yang harus di-NOP-kan (cari di `ExamActivity.smali`):

| Method | Aksi |
|---|---|
| `setupKioskMode` | `return-void` di awal |
| `sembunyikanSystemUI` | `return-void` |
| `bunyikanAlarm` | `return-void` |
| `memicuPelanggaran` | `return-void` |
| `paksakembali` | `return-void` |
| `suspendUjian` | `return-void` |
| `cekBukaBlokir` | `return-void` |
| `dispatchKeyEvent` | langsung `invoke-super` + `return` |
| `onWindowFocusChanged` | hanya `invoke-super` |
| `onMultiWindowModeChanged` | hanya `invoke-super` |
| `onPause` / `onStop` / `onUserLeaveHint` | hapus pemanggilan `paksakembali` |
| `onResume` | hapus `startLockTask` |
| `lepasKunci` | hapus `stopLockTask` + `setStatusBarDisabled` |
| `onCreate` | hapus `setFlags(8192,8192)` dan `addFlags(4719744)` |

Watermark lewat smali jauh lebih repot. Kalau memakai jalur ini, tambahkan
`TextView` secara manual di layout `activity_main.xml` dan `activity_exam.xml`
(gravity center) sebagai pengganti `Watermark.attach`.

## 2. Daftar perubahan per file

### `Watermark.java` (baru)

- Class `Watermark` dengan `attach(Activity)`.
- Membuat `TextView` "MODIFIKASI / TAMPER TEST", `Gravity.CENTER`,
  `addContentView`.
- Dipanggil dari `MainActivity.onCreate()` dan `ExamActivity.onCreate()`.

### `ExamActivity.java`

| Method / bagian | Perubahan |
|---|---|
| `setupKioskMode()` | body → no-op + log |
| `sembunyikanSystemUI()` | body → no-op |
| `bunyikanAlarm()` | body → no-op |
| `memicuPelanggaran(...)` | body → log saja |
| `paksakembali()` | body → no-op |
| `suspendUjian()` | body → no-op |
| `cekBukaBlokir()` | body → no-op |
| `dispatchKeyEvent(...)` | `return super.dispatchKeyEvent(...)` |
| `onWindowFocusChanged(...)` | hanya `super` |
| `onMultiWindowModeChanged(...)` | hanya `super` |
| `onResume()` | hanya `super` |
| `onPause()` / `onStop()` / `onUserLeaveHint()` | hanya `super` |
| `onCreate()` | `setFlags(8192,8192)` + `addFlags(4719744)` → `addFlags(128)`; lewati `sembunyikanSystemUI()`/`setupKioskMode()`; lewati `lockEnforceHandler.post`; violations → 0; tambah `Watermark.attach(this)` |
| `lepasKunci()` | hapus `setStreamVolume` + `stopLockTask` + `setStatusBarDisabled` |
| callback Back | `OnBackPressedCallback(false)` |
| helper `m75x2530f8c7()` | body → no-op |

### `MainActivity.java`

- Tambah `Watermark.attach(this);` di akhir `onCreate()`.
- Teks lain **tidak** diubah (tetap seperti asli).

### `AppConfig.java`

- Tambah `TEST_API_BASE` (default `https://soalpintar.solusiedu.id/api/`).
- `isCustomTestBuild()` → `true`.
- `apiBase()` → `TEST_API_BASE`.
- Allowlist tambah `10.0.2.2`, `localhost`, `127.0.0.1`.
- `lambda$fetch$1` → tidak fallback ke base lain.

### `ExamApi.java`

- Perbaiki bug kirim ganda: `if` tanpa `else` → satu `add()` saja.

### `BuildConfig.java`

- `API_BASE_URL` disamakan dengan `TEST_API_BASE`.

### `AndroidManifest.xml`

- Hapus: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`,
  `BIND_DEVICE_ADMIN`, `<receiver AdminReceiver>`.
- Hapus: `showWhenLocked`, `turnScreenOn`, `autoRemoveFromRecents`,
  `resizeableActivity="false"`.
- Label dan versi tetap seperti asli (`@string/app_name`, `2.1`).
- `allowBackup="false"`.
- Pertahankan `usesCleartextTraffic="true"` hanya untuk opsi mock HTTP.

## 3. Verifikasi

```bash
cd tamper-test/patch
python3 apply_patch.py
```

Skrip akan gagal kalau masih ada pemanggilan `startLockTask`, `setStreamVolume`,
`FLAG_SECURE`, dan sejenisnya.
