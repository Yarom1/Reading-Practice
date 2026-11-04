#!/usr/bin/env python3
"""
Diagnostic: does a voice actually follow the stress mark (ˈ) it is given?

Every test word is synthesized twice inside the same carrier sentence - once marked milra, once
mil'el. For the last two vowels of the word we measure duration (from the model's own durations,
counting the stress-mark token as part of its vowel), pitch and loudness (from the audio).
Modes: the voice as is ("raw") and with the stressed vowel's length enforced ("fixed",
build_speech.Voice). Also renders story lines in both modes for listening.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from huggingface_hub import hf_hub_download
from phonikud import phonemize

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_speech as bs  # noqa: E402

WORDS = [
    "אַבָּא", "אִמָּא", "סַבָּא", "מַיִם", "בַּיִת", "יֶלֶד", "סֵפֶר", "כֶּלֶב", "בֹּקֶר", "לַיְלָה", "דָּנִי", "נוֹעָה",
    "יַחַד", "פַּעַם", "שֶׁמֶשׁ", "עֶרֶב", "גֶּשֶׁם", "אֵיזֶה", "יֹפִי", "כַּמָּה",
    "שָׁלוֹם", "תּוֹדָה", "יָפֶה", "גָּדוֹל", "קָטָן", "חָתוּל", "סִפּוּר", "צִפּוֹר", "גִּנָּה", "גָּמָל", "כֻּלָּם",
    "מַהֵר", "אֲבָל", "קָדִימָה", "שׁוּרָה", "יְלָדִים", "פְּרָחִים", "חָבֵר", "תָּמִיד", "מָתוֹק",
]
CARRIER = ("ʔanˈi ʔomˈeʁ ", " ʔaχʃˈav.")
MODES = [  # repo, model, config, name, enforce stressed vowel length, pitch accent
    ("thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx", "he_IL-noa-medium.onnx.json", "A_noa_now", False, 0.0),
    ("thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx", "he_IL-noa-medium.onnx.json", "B_noa_length", True, 0.0),
    ("thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx", "he_IL-noa-medium.onnx.json", "C_noa_length_pitch", True, 0.12),
    ("thewh1teagle/phonikud-tts-checkpoints", "model.onnx", "model.config.json", "D_male", False, 0.0),
]
LISTEN_LINES = 6


def f0(seg, sr):
    if len(seg) < int(0.03 * sr):
        return math.nan
    seg = seg - seg.mean()
    ac = np.correlate(seg, seg, "full")[len(seg) - 1:]
    lo, hi = int(sr / 400), min(len(ac) - 1, int(sr / 70))
    if hi <= lo or ac[0] <= 0:
        return math.nan
    k = lo + int(np.argmax(ac[lo:hi]))
    return sr / k if ac[k] > 0.3 * ac[0] else math.nan


def vowels_of(word_ph, base, audio, frames, owner, sr):
    hop = len(audio) / frames.sum()
    ends = np.cumsum(frames) * hop
    starts = ends - frames * hop
    out = []
    for k, ch in enumerate(word_ph):
        if ch not in "aeiou":
            continue
        own = {base + k, base + k - 1} if k > 0 and word_ph[k - 1] == "ˈ" else {base + k}
        toks = [i for i, o in enumerate(owner) if o in own]
        if not toks:
            continue
        a, b = int(starts[toks[0]]), int(ends[toks[-1]])
        seg = audio[a:b]
        out.append((float(frames[toks].sum()), f0(seg, sr), float(np.sqrt(np.mean(seg ** 2))) if len(seg) else 0.0))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--catalog", default="")
    ap.add_argument("--words", default="")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    lines = []
    if args.catalog and args.words:
        table = {}
        for row in Path(args.words).read_text(encoding="utf-8").splitlines()[1:]:
            c = row.split("\t")
            table[c[0]] = c[1]
        catalog = [e for e in json.loads(Path(args.catalog).read_text(encoding="utf-8")) if e["kind"] in ("line", "msg")]
        picks = [catalog[i] for i in (4, 40, 75, 110, 150, 200) if i < len(catalog)][:LISTEN_LINES]
        for e in picks:
            parts, ranges, pos = [], [], 0
            for tok, core in zip(e["tokens"], e["cores"]):
                ph = table[core]
                trail = "".join(ch for ch in tok[len(tok.rstrip(".,!?:;")):] if ch in ".,!?:;")
                ranges.append((pos, pos + len(ph)))
                parts.append(ph + trail)
                pos += len(ph + trail) + 1
            lines.append((e["text"], " ".join(parts), ranges))

    results = {}
    for repo, model_file, cfg_file, name, enforce, pitch in MODES:
        voice = bs.Voice(hf_hub_download(repo, model_file), hf_hub_download(repo, cfg_file), length_scale=0.9,
                         enforce_stress=enforce, pitch_accent=pitch)
        rows = []
        for w in WORDS:
            per = {}
            for syl in (1, 2):
                word_ph = phonemize(bs.place_stress(bs.initial_vocal_shva(w), syl))
                phon = CARRIER[0] + word_ph + CARRIER[1]
                base = len(CARRIER[0])
                ranges = [(0, 5), (6, base - 1), (base, base + len(word_ph)), (base + len(word_ph) + 1, len(phon) - 1)]
                audio, frames, owner = voice.synth(phon, ranges)
                per[syl] = {"phonemes": word_ph, "vowels": vowels_of(word_ph, base, audio, frames, owner, voice.sr)}
            rows.append({"word": w, "v1": per[1], "v2": per[2]})
        n = mr_d = ml_d = mr_p = ml_p = n_p = 0
        for r in rows:
            a, b = r["v1"]["vowels"], r["v2"]["vowels"]
            if len(a) < 2 or len(b) < 2:
                continue
            n += 1
            mr_d += a[-1][0] > a[-2][0]
            ml_d += b[-2][0] > b[-1][0]
            if not any(math.isnan(x) for x in (a[-1][1], a[-2][1], b[-1][1], b[-2][1])):
                n_p += 1
                mr_p += a[-1][1] > a[-2][1]
                ml_p += b[-2][1] > b[-1][1]
        results[name] = {"milra_longer_%": round(100 * mr_d / n), "milel_longer_%": round(100 * ml_d / n),
                         "milra_higher_%": round(100 * mr_p / max(1, n_p)), "milel_higher_%": round(100 * ml_p / max(1, n_p)),
                         "rows": rows}
        print(name, {k: v for k, v in results[name].items() if k != "rows"}, flush=True)
        for i, (text, phon, ranges) in enumerate(lines, 1):
            audio, _, _ = voice.synth(phon, ranges)
            sf.write(out / f"line{i}_{name}.wav", audio, voice.sr)
    results["lines"] = [{"text": t, "phonemes": p} for t, p, _ in lines]
    (out / "stress_probe.json").write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
