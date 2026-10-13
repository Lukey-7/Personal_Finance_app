#!/usr/bin/env python3
"""
Privacy audit of a built FinTrack APK (README: "Does the OCR phone home?").

1. Telemetry: no class from Google's logging/telemetry transports may be in the dex
   (clearcut, phenotype, datatransport, firelog, Firebase, Crashlytics).
2. Endpoints: lists every http(s) URL in the dex and native libraries. Only the AI providers in Settings > AI
   (domain/ai/Llm.kt, ProviderKind) may be real endpoints, each contacted only once the person picks it; anything
   else must be a documentation string and is printed for a human to judge.

A debug APK is the strict check: R8 has not removed unused classes yet, so it holds everything a library could
bring in. v1.2 already carries Google's datatransport/firebase-encoders classes through ML Kit text recognition;
their counts are recorded in scripts/audit_baseline.json, and the audit fails if any telemetry count rises above it.

Usage: python scripts/audit_apk.py path/to/app.apk [--write-baseline]
"""
import json
import os
import re
import sys
import zipfile

TELEMETRY = [
    b"Lcom/google/android/gms/clearcut/",
    b"Lcom/google/android/gms/phenotype/",
    b"Lcom/google/android/datatransport/",
    b"Lcom/google/firebase/",
    b"Lcom/google/android/gms/measurement/",
    b"firelog",
    b"Lcom/crashlytics/",
]
URL = re.compile(rb"https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]{4,}")
# The AI providers FinTrack can be pointed at (v1.5.1). Adding one here needs the owner's go-ahead.
ALLOWED_ENDPOINTS = (
    b"https://api.openai.com/",
    b"https://api.anthropic.com/",
    b"https://generativelanguage.googleapis.com/",
    b"https://bedrock-runtime.",
    b"https://api.groq.com/",
    b"https://openrouter.ai/",
    b"https://api.mistral.ai/",
    b"https://api.deepseek.com/",
    b"http://localhost:",
)


HERE = os.path.dirname(os.path.abspath(__file__))


def main(apk: str, write_baseline: bool = False) -> int:
    # R8 rewrites release dex, so release and debug APKs each have their own v1.2 baseline.
    BASELINE = os.path.join(HERE, "audit_baseline_release.json" if "release" in os.path.basename(apk) else "audit_baseline.json")
    hits, urls = {}, set()
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not (name.endswith(".dex") or name.endswith(".so")):
                continue
            data = z.read(name)
            if name.endswith(".dex"):
                for t in TELEMETRY:
                    n = data.count(t)
                    if n:
                        hits[t.decode()] = hits.get(t.decode(), 0) + n
            urls.update(m.group(0) for m in URL.finditer(data))
    if write_baseline:
        with open(BASELINE, "w") as f:
            json.dump(hits, f, indent=2, sort_keys=True)
    base = json.load(open(BASELINE)) if os.path.exists(BASELINE) else {}
    risen = {k: v for k, v in hits.items() if v > base.get(k, 0)}
    print("Telemetry classes:", "none" if not hits else "")
    for k, v in sorted(hits.items()):
        print(f"  {'NEW ' if k in risen else 'known'} {k} x{v} (baseline {base.get(k, 0)})")
    print(f"URLs ({len(urls)}):")
    for u in sorted(urls):
        tag = "ENDPOINT" if u.startswith(ALLOWED_ENDPOINTS) else "string"
        print(f"  [{tag}] {u.decode(errors='replace')}")
    return 1 if risen else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], "--write-baseline" in sys.argv))
