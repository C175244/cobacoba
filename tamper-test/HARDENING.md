# Rekomendasi Hardening — SoalPintar

Disusun dari hasil analisis APK v2.1 dan dari tamper-test build. Urutan
berdasarkan dampak, bukan usaha.

## Akar masalah

| # | Masalah | Konsekuensi |
|---|---|---|
| A | Semua enforcement ada di klien | Cukup patch APK, semua kontrol hilang |
| B | API tanpa autentikasi/attestation | Klien apa pun dengan token bisa ikut ujian |
| C | Token = bearer secret di URL, tanpa pengikatan sesi | Token bocor/dibagikan tidak terdeteksi |
| D | Server mempercayai `count`/`reason` dari klien | Pelanggaran bisa dipalsukan jadi 0 |
| E | Identitas siswa dikirim sebagai query string | Bocor ke log/proxy, mudah dipalsukan |
| F | `device_id` = `ANDROID_ID` | Persisten, mudah dipalsukan, tidak bisa direset |
| G | `complete` hanya berdasarkan klien | Jawaban asli tidak diverifikasi ke Google Form |
| H | Tidak ada integritas build | APK modifikasi tidak bisa dibedakan server |

## P0 — wajib sebelum ujian bernilai tinggi

1. **Play Integrity API (atau equivalent)**
   - Minta *integrity verdict* di setiap `validate-token` dan minimal di
     `heartbeat` berkala.
   - Tolak (`403`) bila `MEETS_DEVICE_INTEGRITY`/`MEETS_STRONG_INTEGRITY` gagal
     atau `appIntegrity.appRecognitionVerdict != PLAY_RECOGNIZED`.
   - Catat verdict di dashboard; tampilkan badge "unverified client".

2. **Sesi ujian yang diikat server**
   - `validate-token` membuat `session_id` acak (server-side) + `session_secret`
     berumur pendek (mis. 15 menit, di-*refresh*).
   - Semua request wajib membawa `session_id` + HMAC (`session_secret` atas
     method+path+body+timestamp+nonce). Tolak timestamp > ±60 detik dan nonce
     yang dipakai ulang.
   - Satu token = satu sesi aktif (atau daftar sesi yang disetujui pengawas).
     Sesi kedua → tandai & blokir, bukan diam-diam diterima.

3. **Hitung pelanggaran di server, bukan di klien**
   - Klien hanya mengirim *fakta mentah* (`event_type`, `timestamp_monotonic`,
     `foreground_package` bila tersedia, `screen_state`).
   - Server yang menentukan ambang, waktu, dan status blokir.
   - Abaikan `count` dari klien; jangan pernah percaya `reason` sebagai vonis.

4. **Deteksi klien "hilang" / diam**
   - Jika heartbeat berhenti > 2× interval, tandai sesi `stale` di dashboard.
   - Jangan menampilkan "normal" hanya karena tidak ada pelanggaran; tampilkan
     juga status *freshness*.

5. **Verifikasi penyelesaian**
   - Cocokkan `complete` dengan respons Google Form (Apps Script webhook atau
     Sheets API): timestamp, email/identitas, jumlah jawaban.
   - `complete` tanpa respons form yang cocok → tandai `unverified`.

## P1 — penting

6. **Pindahkan identitas ke body POST**, bukan query string.
7. **Ganti `device_id`** menjadi ID acak yang dibuat server saat enrollment,
   disimpan di Keystore, dan bisa dicabut/di-reset pengawas.
8. **Batasi token**: masa berlaku pendek, sekali pakai per perangkat, rotasi
   setelah selesai, dan *rate limit* per token/IP/device.
9. **TLS pinning** + matikan `usesCleartextTraffic`.
10. **`allowBackup=false`**, hapus aturan backup kosong.
11. **Hapus izin & device-admin yang tidak dipakai** (`SYSTEM_ALERT_WINDOW`,
    `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `wipe-data`).
12. **WebView**: allowlist domain form, matikan `shouldOverrideUrlLoading` untuk
    origin asing, dan jangan pasang JS bridge ke origin yang tidak dikenal.
13. **Ganti package `com.example.*`** dan siapkan signing v2+v3 yang aman.

## P2 — pengerasan tambahan (bukan solusi utama)

14. **R8/obfuscation** + pemeriksaan tanda tangan aplikasi saat runtime.
    Ini hanya menaikkan biaya serangan, bukan mencegah.
15. **Deteksi tamper**: cek `PackageManager` signature, `Debug.isDebuggerConnected`,
    root/emulator. Semua bisa dilewati; jangan dijadikan satu-satunya kontrol.
16. **Perketat UI**: `FLAG_SECURE` hanya berguna sebagai pencegah pasif.
17. **Log & audit**: simpan semua event mentah dengan hash rantai (append-only)
    supaya manipulasi data bisa dideteksi.

## Yang tidak bisa diperbaiki secara teknis

- Murid memakai HP kedua, kertas, atau orang lain.
- Murid memfoto layar dengan perangkat lain.
- Murid mengerjakan di luar pengawasan.

Kontrol teknis hanya mengurangi *casual cheating*. Untuk ujian bernilai tinggi,
tetap perlu pengawasan manusia dan/atau ujian lisan/praktik.

## Rekomendasi keputusan

| Skenario | Saran |
|---|---|
| Kuis harian, formatif | Boleh jalan, dengan P0 #1 dan #4 minimal |
| Ujian tengah/akhir | Tunggu P0 lengkap + verifikasi `complete` |
| Ujian kelulusan / sertifikasi | P0 + P1 + pengawasan manusia; pertimbangkan mode ujian di lab sekolah dengan perangkat terkelola (device owner) |
