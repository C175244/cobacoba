#!/usr/bin/env python3
"""
Mock server lokal untuk menguji tamper-test build TANPA menyentuh produksi.

Meniru kontrak API SoalPintar + menyediakan Google-Form palsu supaya seluruh
alur (token -> form -> draft -> submit -> complete) bisa diuji offline.

Jalankan:
    python3 server.py                 # 0.0.0.0:8080
    python3 server.py --port 9000

Dari emulator Android, host-nya adalah 10.0.2.2.
Dari HP fisik, pakai IP LAN komputer ini dan ubah TEST_API_BASE di AppConfig.java.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

LOCK = threading.Lock()
STARTED = time.time()

# ---------------------------------------------------------------- state

def seed() -> dict:
    return {
        "TES123": {
            "guru": "Bu Guru (TEST)",
            "topic": "Ujian Latihan — Tamper Test",
            "max_violations": 3,
            "expires_at": (datetime.now(timezone.utc) + timedelta(hours=2))
            .isoformat(timespec="milliseconds")
            .replace("+00:00", "Z"),
            "active": True,
        }
    }


EXAMS = seed()
EVENTS: list[dict] = []
BLOCKED: dict[str, bool] = {}
LAST_SEEN: dict[str, float] = {}

FORM_FIELDS = [
    ("entry.1001", "text", "Nama lengkap", ""),
    ("entry.1002", "radio", "Kelas", ["9A", "9B", "9C"]),
    ("entry.1003", "textarea", "Jawaban singkat", ""),
]


def now() -> str:
    return datetime.now().strftime("%H:%M:%S")


def record(kind: str, token: str, payload: dict | None = None) -> None:
    with LOCK:
        EVENTS.append(
            {
                "t": now(),
                "kind": kind,
                "token": token,
                "device_id": (payload or {}).get("device_id", "-"),
                "name": (payload or {}).get("name", "-"),
                "class": (payload or {}).get("class", "-"),
                "absen": (payload or {}).get("absen", "-"),
                "reason": (payload or {}).get("reason", ""),
                "count": (payload or {}).get("count", ""),
            }
        )
        if payload and payload.get("device_id"):
            LAST_SEEN[payload["device_id"]] = time.time()
    print(f"[{now()}] {kind:14s} token={token} dev={ (payload or {}).get('device_id','-') }"
          f" name={(payload or {}).get('name','-')} count={(payload or {}).get('count','')}"
          f" reason={(payload or {}).get('reason','')}")


# ---------------------------------------------------------------- html

def page(title: str, body: str, refresh: int | None = None) -> bytes:
    meta = f'<meta http-equiv="refresh" content="{refresh}">' if refresh else ""
    return f"""<!doctype html>
<html lang="id"><head><meta charset="utf-8">{meta}
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<style>
 body{{font:14px/1.5 system-ui,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:24px}}
 h1{{font-size:20px;margin:0 0 4px}} h2{{font-size:15px;margin:24px 0 8px;color:#94a3b8}}
 .pill{{display:inline-block;padding:2px 10px;border-radius:999px;background:#1e293b;font-size:12px}}
 table{{border-collapse:collapse;width:100%;font-size:13px}}
 th,td{{text-align:left;padding:6px 8px;border-bottom:1px solid #1e293b}}
 th{{color:#94a3b8;font-weight:600}} .ok{{color:#34d399}} .bad{{color:#f87171}}
 .warn{{background:#7f1d1d33;border:1px solid #7f1d1d;padding:12px;border-radius:8px;margin:12px 0}}
 a{{color:#38bdf8}} input,textarea{{background:#111827;color:#e2e8f0;border:1px solid #334155;
 padding:10px;border-radius:8px;width:100%;box-sizing:border-box;margin:4px 0 14px}}
 button{{background:#059669;color:#fff;border:0;padding:12px 20px;border-radius:8px;font-weight:600}}
 label{{color:#cbd5e1}}
</style></head><body>{body}</body></html>""".encode()


def dashboard() -> bytes:
    with LOCK:
        events = list(EVENTS)
        violations = sum(1 for e in events if e["kind"] == "violation")
        blocks = sum(1 for e in events if e["kind"] == "block")
        heartbeats = sum(1 for e in events if e["kind"] == "heartbeat")
        completes = sum(1 for e in events if e["kind"] == "complete")
        rows = "".join(
            "<tr>"
            f"<td>{e['t']}</td><td>{html.escape(e['kind'])}</td>"
            f"<td>{html.escape(e['token'])}</td>"
            f"<td>{html.escape(str(e['device_id']))}</td>"
            f"<td>{html.escape(str(e['name']))}</td>"
            f"<td>{html.escape(str(e['class']))}</td>"
            f"<td>{html.escape(str(e['count']))}</td>"
            f"<td>{html.escape(str(e['reason']))}</td>"
            "</tr>"
            for e in reversed(events[-200:])
        ) or '<tr><td colspan="8" style="color:#64748b">belum ada event</td></tr>'

    body = f"""
<h1>SoalPintar — Mock Server (TAMPER TEST)</h1>
<span class="pill">uptime {int(time.time()-STARTED)}s</span>
<span class="pill">violation: <b class="{'ok' if violations==0 else 'bad'}">{violations}</b></span>
<span class="pill">block: <b class="{'ok' if blocks==0 else 'bad'}">{blocks}</b></span>
<span class="pill">heartbeat: {heartbeats}</span>
<span class="pill">complete: {completes}</span>
<div class="warn">
  Ini server tiruan. Build tamper-test seharusnya <b>tidak pernah</b> mengirim
  <code>violation</code> atau <code>block</code>. Kalau angka di atas &gt; 0,
  berarti masih ada enforcement yang tersisa.
</div>
<h2>Token uji</h2>
<p>Gunakan token <code>TES123</code> di aplikasi.</p>
<h2>Event ({len(events)})</h2>
<table><tr><th>waktu</th><th>jenis</th><th>token</th><th>device</th><th>nama</th>
<th>kelas</th><th>count</th><th>alasan</th></tr>{rows}</table>
<h2>Aksi</h2>
<form method="post" action="/api/_reset"><button>Reset semua event</button></form>
<p><a href="/api/_state">lihat state JSON</a> · <a href="/">refresh</a></p>
"""
    return page("Mock Server — SoalPintar TAMPER TEST", body, refresh=3)


def form_page(token: str, query: dict[str, list[str]], host: str) -> bytes:
    exam = EXAMS.get(token, {})
    values = {k: v[-1] for k, v in query.items() if k.startswith("entry.")}
    fields = []
    for name, kind, label, opts in FORM_FIELDS:
        val = html.escape(values.get(name, ""))
        if kind == "textarea":
            fields.append(
                f'<label>{html.escape(label)}</label>'
                f'<textarea name="{name}">{val}</textarea>'
            )
        elif kind == "radio":
            choices = "".join(
                f'<label style="display:block"><input type="radio" name="{name}" '
                f'value="{html.escape(o)}" {"checked" if values.get(name)==o else ""}> '
                f'{html.escape(o)}</label>'
                for o in opts
            )
            fields.append(f'<label>{html.escape(label)}</label>{choices}<br>')
        else:
            fields.append(
                f'<label>{html.escape(label)}</label>'
                f'<input type="text" name="{name}" value="{val}">'
            )

    body = f"""
<h1>{html.escape(exam.get('topic','Ujian'))}</h1>
<span class="pill">guru: {html.escape(exam.get('guru','-'))}</span>
<span class="pill">token: {html.escape(token)}</span>
<span class="pill">max_violations: {exam.get('max_violations', 3)}</span>
<p style="color:#94a3b8">Ini Google Form tiruan. Isi lalu tekan Kirim.</p>
<form method="post" action="/docs.google.com/forms/d/e/{html.escape(token)}/formResponse">
  <input type="hidden" name="fbzx" value="mock-{int(time.time())}">
  {''.join(fields)}
  <button type="submit">KIRIM</button>
</form>
<p style="color:#64748b">URL: http://{html.escape(host)}/docs.google.com/forms/d/e/{html.escape(token)}/viewform</p>
"""
    return page(exam.get("topic", "Form"), body)


def submitted_page(token: str) -> bytes:
    body = f"""
<h1>Jawaban Anda telah direkam</h1>
<p>Terima kasih. Token <code>{html.escape(token)}</code> selesai.</p>
<p><a href="/docs.google.com/forms/d/e/{html.escape(token)}/viewform">Kirim jawaban lain</a></p>
"""
    return page("Terkirim", body)


# ---------------------------------------------------------------- handler

class Handler(BaseHTTPRequestHandler):
    server_version = "SoalPintarMock/1.0"

    def log_message(self, fmt, *args):  # ringkas
        pass

    # -- util
    def send(self, code: int, body: bytes, ctype: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def json(self, code: int, obj: dict) -> None:
        self.send(code, json.dumps(obj).encode(), "application/json; charset=utf-8")

    def body_json(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        ctype = (self.headers.get("Content-Type") or "").lower()
        if "json" in ctype:
            try:
                return json.loads(raw or b"{}")
            except json.JSONDecodeError:
                return {}
        return {k: v[-1] for k, v in parse_qs(raw.decode(errors="replace")).items()}

    @property
    def host(self) -> str:
        return self.headers.get("Host") or "10.0.2.2:8080"

    # -- GET
    def do_GET(self) -> None:
        u = urlparse(self.path)
        path, q = u.path, parse_qs(u.query)

        if path == "/":
            return self.send(200, dashboard(), "text/html; charset=utf-8")

        if path == "/api/app-config":
            return self.json(200, {
                "status": "success",
                "api_base": f"http://{self.host}/api/",
                "maintenance": {"enabled": False, "message": ""},
                "min_version_code": 0,
                "update_url": "",
                "update_message": "",
                "announcement": "MOCK SERVER — TAMPER TEST BUILD",
                "heartbeat_seconds": 60,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            })

        m = re.fullmatch(r"/api/validate-token/([^/]+)", path)
        if m:
            token = m.group(1).strip().upper()
            exam = EXAMS.get(token)
            payload = {
                "device_id": (q.get("device_id") or ["-"])[-1],
                "name": (q.get("name") or ["-"])[-1],
                "class": (q.get("class") or ["-"])[-1],
                "absen": (q.get("absen") or ["-"])[-1],
            }
            if not exam or not exam.get("active"):
                record("validate_fail", token, payload)
                return self.json(404, {"status": "error", "message": "Token tidak ditemukan."})
            record("validate_ok", token, payload)
            return self.json(200, {
                "status": "success",
                "form_url": f"http://{self.host}/docs.google.com/forms/d/e/{token}/viewform",
                "guru": exam["guru"],
                "topic": exam["topic"],
                "max_violations": exam["max_violations"],
                "expires_at": exam.get("expires_at"),
            })

        m = re.fullmatch(r"/api/exam/([^/]+)/device-status", path)
        if m:
            token = m.group(1).strip().upper()
            dev = (q.get("device_id") or ["-"])[-1]
            payload = {"device_id": dev}
            record("device_status", token, payload)
            return self.json(200, {"blocked": BLOCKED.get(f"{token}|{dev}", False)})

        if path == "/api/_state":
            with LOCK:
                return self.json(200, {
                    "exams": EXAMS,
                    "events": EVENTS,
                    "blocked": BLOCKED,
                    "last_seen": LAST_SEEN,
                })

        m = re.fullmatch(r"/docs\.google\.com/forms/d/e/([^/]+)/viewform", path)
        if m:
            return self.send(200, form_page(m.group(1).strip().upper(), q, self.host),
                             "text/html; charset=utf-8")

        return self.json(404, {"status": "error", "message": "not found"})

    # -- POST
    def do_POST(self) -> None:
        u = urlparse(self.path)
        path = u.path

        if path == "/api/_reset":
            with LOCK:
                EVENTS.clear()
                BLOCKED.clear()
                LAST_SEEN.clear()
            return self.send(200, dashboard(), "text/html; charset=utf-8")

        if path == "/api/_unblock":
            data = self.body_json()
            token = str(data.get("token", "")).upper()
            dev = str(data.get("device_id", ""))
            with LOCK:
                BLOCKED[f"{token}|{dev}"] = False
            return self.json(200, {"status": "success"})

        m = re.fullmatch(r"/api/exam/([^/]+)/(heartbeat|violation|block|complete)", path)
        if m:
            token = m.group(1).strip().upper()
            action = m.group(2)
            payload = self.body_json()
            record(action, token, payload)
            if action == "block":
                with LOCK:
                    BLOCKED[f"{token}|{payload.get('device_id','')}"] = True
            return self.json(200, {"status": "success"})

        m = re.fullmatch(r"/docs\.google\.com/forms/d/e/([^/]+)/formResponse", path)
        if m:
            token = m.group(1).strip().upper()
            payload = self.body_json()
            record("form_submit", token, {
                "device_id": "-",
                "name": payload.get("entry.1001", "-"),
                "class": payload.get("entry.1002", "-"),
                "absen": "-",
                "reason": "google-form",
            })
            return self.send(200, submitted_page(token), "text/html; charset=utf-8")

        return self.json(404, {"status": "error", "message": "not found"})


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8080)
    args = ap.parse_args()

    print("=" * 62)
    print(" SoalPintar mock server (TAMPER TEST)")
    print(f"  dashboard : http://127.0.0.1:{args.port}/")
    print(f"  api base  : http://10.0.2.2:{args.port}/api/   (dari emulator)")
    print("  token uji : TES123")
    print("=" * 62)
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
