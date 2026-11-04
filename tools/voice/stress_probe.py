#!/usr/bin/env python3
"""
Diagnostic: does a voice actually follow the stress mark (ˈ) it is given?

Every test word is synthesized twice inside the same carrier sentence - once marked milra, once
mil'el - with the duration noise switched off. For the last two vowels of the word we measure
duration (from the model's own duration predictor), pitch and loudness (from the audio). A voice
that obeys the mark makes the marked vowel longer / higher; a voice that ignores it sounds the same
in both versions.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
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
VOICES = [
    ("thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx", "he_IL-noa-medium.onnx.json", "noa"),
    ("thewh1teagle/phonikud-tts-checkpoints", "model.onnx", "model.config.json", "model"),
    ("thewh1teagle/phonikud-tts-checkpoints", "michael.onnx", "model.config.json", "michael"),
    ("thewh1teagle/phonikud-tts-checkpoints", "shaul.onnx", "model.config.json", "shaul"),
]
VOWELS = set("aeiou")
CARRIER = ("ʔanˈi ʔomˈeʁ ", " ʔaχʃˈav.")


def f0(seg, sr):
    if len(seg) < int(0.03 * sr):
        return np.nan
    seg = seg - seg.mean()
    ac = np.correlate(seg, seg, "full")[len(seg) - 1:]
    lo, hi = int(sr / 400), min(len(ac) - 1, int(sr / 70))
    if hi <= lo or ac[0] <= 0:
        return np.nan
    k = lo + int(np.argmax(ac[lo:hi]))
    return sr / k if ac[k] > 0.3 * ac[0] else np.nan


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    results = {}
    for repo, model_file, cfg_file, name in VOICES:
        model_path = hf_hub_download(repo, model_file)
        m = onnx.load(model_path)
        ceil = next(n for n in m.graph.node if n.op_type == "Ceil")
        m.graph.output.append(onnx.helper.make_tensor_value_info(ceil.output[0], onnx.TensorProto.FLOAT, None))
        path = model_path + ".probe.onnx"
        onnx.save(m, path)
        sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        cfg = json.loads(Path(hf_hub_download(repo, cfg_file)).read_text())
        idmap, sr = cfg["phoneme_id_map"], cfg["audio"]["sample_rate"]
        has_sid = "sid" in [i.name for i in sess.get_inputs()]
        rows = []
        for w in WORDS:
            per_variant = {}
            for syl in (1, 2):
                word_ph = phonemize(bs.place_stress(bs.initial_vocal_shva(w), syl))
                ph = CARRIER[0] + word_ph + CARRIER[1]
                chars = ["^"] + list(ph)
                ids, owner = [], []
                for ci, ch in enumerate(chars):
                    if ch in idmap:
                        for t in idmap[ch] + idmap["_"]:
                            ids.append(t)
                            owner.append(ci - 1)
                ids += idmap["$"]
                owner += [-1] * len(idmap["$"])
                feeds = {"input": np.array([ids], dtype=np.int64), "input_lengths": np.array([len(ids)], dtype=np.int64),
                         "scales": np.array([0.667, 1.0, 0.0], dtype=np.float32)}
                if has_sid:
                    feeds["sid"] = np.array([0], dtype=np.int64)
                audio, frames = sess.run(None, feeds)
                audio = np.asarray(audio, dtype=np.float32).reshape(-1)
                frames = np.asarray(frames, dtype=np.float64).reshape(-1)
                hop = len(audio) / frames.sum()
                ends = np.cumsum(frames) * hop
                starts = ends - frames * hop
                base = len(CARRIER[0])
                vowels = []  # (char index in phonemes, duration frames, f0, rms) for the word's vowels
                for k, ch in enumerate(word_ph):
                    if ch not in VOWELS:
                        continue
                    ci = base + k
                    toks = [i for i, o in enumerate(owner) if o == ci]
                    if not toks:
                        continue
                    a, b = int(starts[toks[0]]), int(ends[toks[-1]])
                    seg = audio[a:b]
                    vowels.append((float(frames[toks].sum()), f0(seg, sr), float(np.sqrt(np.mean(seg ** 2))) if len(seg) else 0.0))
                per_variant[syl] = {"phonemes": word_ph, "vowels": vowels}
                if w in ("אַבָּא", "גִּנָּה", "קָדִימָה", "כֶּלֶב", "שָׁלוֹם"):
                    sf.write(out / f"{name}_{w}_{'milra' if syl == 1 else 'milel'}.wav", audio, sr)
            rows.append({"word": w, **{f"v{k}": v for k, v in per_variant.items()}})
        # stress obedience: (last/penult) in the milra version divided by the same in the mil'el version
        dur_r, f0_r, rms_r = [], [], []
        for r in rows:
            a, b = r["v1"]["vowels"], r["v2"]["vowels"]
            if len(a) < 2 or len(b) < 2:
                continue
            dur_r.append((a[-1][0] / a[-2][0]) / (b[-1][0] / b[-2][0]))
            if not any(np.isnan([a[-1][1], a[-2][1], b[-1][1], b[-2][1]])):
                f0_r.append((a[-1][1] / a[-2][1]) / (b[-1][1] / b[-2][1]))
            if min(a[-2][2], b[-1][2], b[-2][2]) > 0:
                rms_r.append((a[-1][2] / a[-2][2]) / (b[-1][2] / b[-2][2]))
        results[name] = {
            "words": len(dur_r),
            "duration_ratio_median": round(float(np.median(dur_r)), 2),
            "duration_follows_mark_%": round(100 * float(np.mean(np.array(dur_r) > 1.15)), 0),
            "pitch_ratio_median": round(float(np.median(f0_r)), 2) if f0_r else None,
            "loudness_ratio_median": round(float(np.median(rms_r)), 2) if rms_r else None,
            "rows": rows,
        }
        print(name, {k: v for k, v in results[name].items() if k != "rows"}, flush=True)
    (out / "stress_probe.json").write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
