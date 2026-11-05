#!/usr/bin/env python3
"""
Diagnostic: listens to the app's line recordings with a Hebrew speech recognizer (ivrit.ai Whisper)
and lists the words it does not hear as written - a pronunciation check that needs no human ear.

  python tools/voice/asr_check.py --assets app/src/main/assets --out asr-out [--limit N]
"""
import argparse
import difflib
import json
import re
import sys
import time
from pathlib import Path

NIKUD = re.compile(r"[֑-ׇ]")
OVERRIDE = re.compile(r"\{([^{}|]+)\|([^{}]+)\}")
FINALS = str.maketrans("ךםןףץ", "כמנפצ")


def plain_of(raw):
    """Same as DualText.parse for the plain (ktiv male) version."""
    if "||" in raw:
        return raw.split("||", 1)[1].strip()
    return NIKUD.sub("", OVERRIDE.sub(lambda m: "\u0000" + m.group(2) + "\u0001", raw)).replace("\u0000", "").replace("\u0001", "")


def nikud_of(raw):
    if "||" in raw:
        return raw.split("||", 1)[0].strip()
    return OVERRIDE.sub(lambda m: m.group(1), raw)


def key(word):
    """Spelling-tolerant comparison key: letters only, no final forms, no ו/י (ktiv male varies)."""
    w = "".join(c for c in word if "א" <= c <= "ת").translate(FINALS)
    return w.replace("ו", "").replace("י", "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--model", default="ivrit-ai/whisper-large-v3-turbo-ct2")
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    assets = Path(args.assets)

    import unicodedata

    import miniaudio
    import numpy as np
    from faster_whisper import WhisperModel

    manifest = json.loads((assets / "speech" / "manifest.json").read_text(encoding="utf-8"))["items"]
    passages = json.loads((assets / "content" / "passages.json").read_text(encoding="utf-8"))
    lines = []
    for p in passages:
        for raw in p["lines"]:
            n = unicodedata.normalize("NFC", " ".join(nikud_of(raw).split()))
            lines.append((p["id"], n, " ".join(plain_of(raw).split())))
    if args.limit and args.limit < len(lines):
        step = len(lines) / args.limit  # an even sample over all levels
        lines = [lines[int(i * step)] for i in range(args.limit)]

    model = None
    for name in (args.model, "large-v3-turbo"):
        try:
            model = WhisperModel(name, device="cpu", compute_type="int8")
            print("speech recognizer:", name, flush=True)
            break
        except Exception as e:  # noqa: BLE001
            print("could not load", name, repr(e)[:200], flush=True)
    if model is None:
        return 1
    total = ok = 0
    rows, missed = [], {}
    t0 = time.time()
    for i, (pid, nikud, plain) in enumerate(lines, 1):
        item = manifest.get(nikud)
        if item is None:
            rows.append(f"{pid}\tNO RECORDING\t{plain}")
            continue
        decoded = miniaudio.decode_file(str(assets / "speech" / item["f"]), nchannels=1, sample_rate=16000)
        audio = np.frombuffer(decoded.samples, dtype=np.int16).astype(np.float32) / 32768.0
        segs, _ = model.transcribe(audio, language="he", beam_size=1,
                                   condition_on_previous_text=False, vad_filter=False)
        heard = " ".join(s.text for s in segs).strip()
        exp_words = plain.split()
        exp_keys = [key(w) for w in exp_words]
        got_keys = [key(w) for w in heard.split()]
        sm = difflib.SequenceMatcher(None, exp_keys, got_keys, autojunk=False)
        good = sum(b.size for b in sm.get_matching_blocks())
        bad_words = []
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag != "equal":
                bad_words += [(exp_words[k], " ".join(heard.split()[j1:j2])) for k in range(i1, i2)]
        total += len(exp_keys)
        ok += good
        for w, h in bad_words:
            missed.setdefault(w.strip(".,!?:;\"'"), []).append(h)
        if bad_words:
            rows.append(f"{pid}\t{plain}\tHEARD: {heard}\tWRONG: " + "; ".join(f"{w}→{h or '∅'}" for w, h in bad_words))
        if i % 10 == 0:
            print(f"  {i}/{len(lines)}  accuracy so far {100 * ok / max(1, total):.1f}%  ({time.time() - t0:.0f}s)", flush=True)
    summary = {"lines": len(lines), "words": total, "heard_as_written_%": round(100 * ok / max(1, total), 1),
               "lines_with_differences": sum(1 for r in rows if "WRONG:" in r)}
    (out / "asr_lines.tsv").write_text("\n".join(rows), encoding="utf-8")
    worst = sorted(missed.items(), key=lambda kv: -len(kv[1]))
    (out / "asr_words.tsv").write_text("\n".join(f"{w}\t{len(h)}\t{' | '.join(h[:5])}" for w, h in worst), encoding="utf-8")
    (out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    sys.exit(main())
