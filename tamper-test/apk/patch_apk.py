#!/usr/bin/env python3
"""
Patch hasil `apktool d` supaya menjadi build TAMPER TEST yang bisa diinstal.

Yang diubah:
  * manifest  : hapus izin/device-admin/flag kiosk
  * layout    : tambah watermark tengah layar (activity_main & activity_exam)
  * smali     : no-op method enforcement + lepas FLAG_SECURE + lepas lock task

Pemakaian:
    python3 patch_apk.py /path/ke/apkwork
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

def watermark_xml() -> str:
    path = Path(__file__).with_name("watermark.txt")
    if path.exists():
        lines = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        if lines:
            return "&#10;".join(lines)
    return "MODIFIKASI&#10;TAMPER TEST"


def wm_view() -> str:
    return (
        f'        <TextView android:text="{watermark_xml()}" '
        'android:textColor="#ffffffff" android:textSize="16.0sp" '
        'android:textStyle="bold" android:gravity="center" '
        'android:background="#99000000" android:padding="12.0dip" '
        'android:layout_gravity="center" android:elevation="24.0dip" '
        'android:clickable="false" android:focusable="false" '
        'android:layout_width="wrap_content" android:layout_height="wrap_content" />'
    )

NOOP = "    .locals 0\n\n    return-void\n"


def must_replace(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    n = text.count(old)
    if n != count:
        raise SystemExit(f"[FAIL] {name}: expected {count}, got {n}")
    print(f"[ok] {name}")
    return text.replace(old, new)


def replace_method(text: str, needle: str, body: str, name: str) -> str:
    pat = re.compile(
        r"(?ms)^\.method [^\n]*" + re.escape(needle) + r"[^\n]*\n.*?^\.end method\n"
    )
    matches = pat.findall(text)
    if len(matches) != 1:
        raise SystemExit(f"[FAIL] {name}: {len(matches)} match(es)")
    m = pat.search(text)
    header = m.group(0).split("\n", 1)[0]
    repl = header + "\n" + body + ".end method\n"
    print(f"[ok] {name}")
    return text[: m.start()] + repl + text[m.end():]


def patch_manifest(root: Path) -> None:
    p = root / "AndroidManifest.xml"
    s = p.read_text()
    for perm in (
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.RECEIVE_BOOT_COMPLETED",
    ):
        s = must_replace(
            s,
            f'    <uses-permission android:name="{perm}"/>\n',
            "",
            f"manifest: hapus izin {perm}",
        )
    for attr in (
        ' android:autoRemoveFromRecents="true"',
        ' android:resizeableActivity="false"',
        ' android:showWhenLocked="true"',
        ' android:turnScreenOn="true"',
    ):
        s = must_replace(s, attr, "", f"manifest: hapus {attr.strip()}")
    s = must_replace(
        s, 'android:allowBackup="true"', 'android:allowBackup="false"', "manifest: allowBackup=false"
    )
    s2, n = re.subn(r"\n\s*<receiver[^>]*AdminReceiver[^>]*>.*?</receiver>", "", s, flags=re.S)
    if n != 1:
        raise SystemExit(f"[FAIL] manifest: receiver AdminReceiver -> {n} match(es)")
    print("[ok] manifest: hapus receiver AdminReceiver")
    p.write_text(s2)


def patch_layouts(root: Path) -> None:
    exam = root / "res/layout/activity_exam.xml"
    s = exam.read_text()
    if s.count("    </FrameLayout>") != 1:
        raise SystemExit("[FAIL] activity_exam: jumlah </FrameLayout> tidak 1")
    s = s.replace("    </FrameLayout>", wm_view() + "\n    </FrameLayout>", 1)
    exam.write_text(s)
    print("[ok] layout: watermark di activity_exam")

    main = root / "res/layout/activity_main.xml"
    s = main.read_text()
    if s.count("<ScrollView") != 1:
        raise SystemExit("[FAIL] activity_main: jumlah <ScrollView> tidak 1")
    s = s.replace(
        "<ScrollView",
        '<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:app="http://schemas.android.com/apk/res-auto" '
        'android:layout_width="fill_parent" android:layout_height="fill_parent">\n<ScrollView',
        1,
    )
    s = s.rstrip()
    if not s.endswith("</ScrollView>"):
        raise SystemExit("[FAIL] activity_main: tidak berakhir dengan </ScrollView>")
    s = s[: -len("</ScrollView>")] + "</ScrollView>\n" + wm_view() + "\n</FrameLayout>\n"
    main.write_text(s)
    print("[ok] layout: watermark di activity_main")


def patch_smali(root: Path) -> None:
    p = root / "smali/com/example/soalpintar/ExamActivity.smali"
    s = p.read_text()

    for needle, name in (
        ("bunyikanAlarm()V", "bunyikanAlarm"),
        ("cekBukaBlokir()V", "cekBukaBlokir"),
        ("memicuPelanggaran(Ljava/lang/String;Ljava/lang/String;)V", "memicuPelanggaran"),
        ("paksakembali()V", "paksakembali"),
        ("sembunyikanSystemUI()V", "sembunyikanSystemUI"),
        ("setupKioskMode()V", "setupKioskMode"),
        ("suspendUjian()V", "suspendUjian"),
        (
            "lambda$onMultiWindowModeChanged$6$com-example-soalpintar-ExamActivity()V",
            "lambda onMultiWindowModeChanged",
        ),
    ):
        s = replace_method(s, needle, NOOP, f"smali: no-op {name}")

    s = replace_method(
        s,
        "onResume()V",
        "    .locals 0\n\n"
        "    invoke-super {p0}, Landroidx/appcompat/app/AppCompatActivity;->onResume()V\n\n"
        "    return-void\n",
        "smali: onResume tanpa startLockTask",
    )

    s = replace_method(
        s,
        "dispatchKeyEvent(Landroid/view/KeyEvent;)Z",
        "    .locals 1\n\n"
        "    invoke-super {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->"
        "dispatchKeyEvent(Landroid/view/KeyEvent;)Z\n\n"
        "    move-result v0\n\n"
        "    return v0\n",
        "smali: dispatchKeyEvent lewat super",
    )

    # onCreate: lepas FLAG_SECURE dan fullscreen/lock-screen flags
    pat = re.compile(
        r"(?ms)^\.method protected onCreate\(Landroid/os/Bundle;\)V\n.*?^\.end method\n"
    )
    m = pat.search(s)
    if not m:
        raise SystemExit("[FAIL] onCreate tidak ditemukan")
    block = m.group(0)
    if block.count("const/16 v0, 0x2000") != 1 or block.count("const v0, 0x480480") != 1:
        raise SystemExit("[FAIL] onCreate: konstanta flag tidak sesuai")
    block = block.replace("const/16 v0, 0x2000", "const/16 v0, 0x0")
    block = block.replace("const v0, 0x480480", "const/16 v0, 0x80")
    s = s[: m.start()] + block + s[m.end():]
    print("[ok] smali: onCreate tanpa FLAG_SECURE, hanya KEEP_SCREEN_ON")

    # lepas AudioManager.setStreamVolume(...) di lepasKunci
    vol_block = (
        "    iget-object v0, p0, Lcom/example/soalpintar/ExamActivity;->"
        "audioManager:Landroid/media/AudioManager;\n\n"
        "    iget v1, p0, Lcom/example/soalpintar/ExamActivity;->"
        "originalAlarmVolume:I\n\n"
        "    const/4 v2, 0x0\n\n"
        "    const/4 v3, 0x4\n\n"
        "    invoke-virtual {v0, v3, v1, v2}, Landroid/media/AudioManager;->"
        "setStreamVolume(III)V\n\n"
    )
    s = must_replace(
        s, vol_block, "    const/4 v2, 0x0\n\n", "smali: hapus setStreamVolume di lepasKunci"
    )

    p.write_text(s)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_apk.py <decoded-dir>")
    root = Path(sys.argv[1]).resolve()
    if not (root / "AndroidManifest.xml").exists():
        raise SystemExit(f"bukan hasil apktool: {root}")
    patch_manifest(root)
    patch_layouts(root)
    patch_smali(root)
    print("\nSelesai. Lanjut: apktool b, lalu sign.")


if __name__ == "__main__":
    main()
