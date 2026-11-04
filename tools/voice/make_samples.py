#!/usr/bin/env python3
"""
Hebrew voice samples with correct stress (milra / mil'el) - a comparison set, the app is not touched.

Pipeline per sentence:
  1. our hand-checked nikud is kept as is;
  2. the Phonikud nakdan model contributes only what standard nikud cannot express:
     stress (hat'ama, U+05AB), vocal shva (U+05BD), prefix boundaries ("|") and qamats qatan;
  3. a small manual list can force the stress of specific words (e.g. kadimá, not the party Kadíma);
  4. Phonikud converts the text to IPA phonemes and a Piper (VITS) Hebrew voice speaks them;
  5. word start/end times are read from the voice model's own duration predictor (for highlighting).

Runs in GitHub Actions (models are downloaded from HuggingFace there).
"""
import argparse
import json
import sys
import traceback
import unicodedata
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import soundfile as sf
from huggingface_hub import HfApi, hf_hub_download
from phonikud import phonemize
from phonikud_onnx import Phonikud
from piper_onnx import Piper

STRESS, VSHVA, PREFIX = "֫", "ֽ", "|"
QAMATS, QAMATS_QATAN = "ָ", "ׇ"
PUNCT = set(".,!?:;\"'-")

SAMPLES = {
    "01_encouragement": "עַכְשָׁיו אַתָּה! אֶצְבַּע מִתַּחַת לַמִּלִּים, וְקָדִימָה.",
    "02_praise": "אֵיזֶה יֹפִי! הִצְלַחְתָּ לִקְרֹא אֶת הַשּׁוּרָה! כָּל הַכָּבוֹד! מַמְשִׁיכִים לַשּׁוּרָה הַבָּאָה?",
    "03_girl": "בּוֹאִי נִקְרָא סִפּוּר! עַכְשָׁיו תּוֹרֵךְ! קִרְאִי אֶת הַשּׁוּרָה בְּקוֹל.",
    "04_level1": "אַבָּא בָּא. אִמָּא בָּאָה. סַבָּא וְסַבְתָּא בָּאִים. כֻּלָּם בַּבַּיִת! אֵיזֶה כֵּיף!",
    "05_level3": "דָּנִי יָצָא לְטַיֵּל בַּגִּנָּה. הוּא רָאָה פְּרָחִים צִבְעוֹנִיִּים וְעֵצִים גְּבוֹהִים.",
    "06_level5": "הַגָּמָל חַי בַּמִּדְבָּר, שָׁם חַם מְאוֹד וְיֵשׁ מְעַט מַיִם. עַל הַגַּב שֶׁלּוֹ יֵשׁ דַּבֶּשֶׁת.",
}

# Stress is decided by review, never by the model (its int8 stress output even differs between CPUs).
# Every word is milra (stress on the last syllable) unless its letters are listed here as mil'el.
MILEL = {
    "אצבע", "מתחת", "איזה", "יפי", "הצלחת", "אבא", "אמא", "סבא", "סבתא", "וסבתא",
    "בית", "בבית", "דני", "מים", "דבשת", "בואי",
}


def is_letter(c):
    return "א" <= c <= "ת"


def is_mark(c):
    return "֑" <= c <= "ׇ" or c == PREFIX


def split_letters(s):
    """[(letter, marks)] for Hebrew letters, [(char, None)] for everything else."""
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if is_letter(c):
            j = i + 1
            while j < len(s) and is_mark(s[j]):
                j += 1
            out.append((c, s[i + 1:j]))
            i = j
        else:
            out.append((c, None))
            i += 1
    return out


def letters_only(s):
    return "".join(c for c in s if is_letter(c))


SHVA = "ְ"
VOWELS = set("ֱֲֳִֵֶַָׇֹֺֻ")
WARNINGS = []


def merge(ours, predicted):
    """Our nikud + the model's stress / vocal shva / prefix marks and qamats qatan (letter by letter)."""
    pred = [m for _, m in split_letters(predicted) if m is not None]
    out, k = [], 0
    for c, marks in split_letters(unicodedata.normalize("NFC", ours)):
        if marks is None:
            out.append(c)
            continue
        pm = pred[k] if k < len(pred) else ""
        k += 1
        new = marks.replace(QAMATS, QAMATS_QATAN) if (QAMATS in marks and QAMATS_QATAN in pm) else marks
        if STRESS in pm:
            new += STRESS
            if not (VOWELS & set(marks)) and not (c == "ו" and "ּ" in marks):
                WARNINGS.append(f"stress on a letter without a vowel: {c}{marks} in {ours}")
        if VSHVA in pm and SHVA in marks:
            new += VSHVA
        new += PREFIX if PREFIX in pm else ""
        out.append(c + new)
    return "".join(out)


def initial_vocal_shva(text):
    """Phonikud's own rule (it is a no-op in phonikud 0.4.1): a word-initial shva is pronounced when the letter is
    ל/מ/נ/ר/י, when the next letter is א/ע/ה, or when the letter is the prefix ו/כ/ל/ב."""
    words = []
    for w in text.split(" "):
        items = split_letters(w)
        idx = [i for i, (_, m) in enumerate(items) if m is not None]
        if idx:
            c0, m0 = items[idx[0]]
            second = items[idx[1]][0] if len(idx) > 1 else ""
            if SHVA in m0 and VSHVA not in m0 and (
                    c0 in "למנרי" or second in "אעה" or (c0 in "וכלב" and PREFIX in m0)):
                bar = PREFIX if PREFIX in m0 else ""
                items[idx[0]] = (c0, m0.replace(PREFIX, "") + VSHVA + bar)
            w = "".join(c + (m or "") for c, m in items)
        words.append(w)
    return " ".join(words)


HATAF = set("\u05b1\u05b2\u05b3")
FULL_VOWELS = VOWELS - HATAF


def carriers(items):
    """Indices (into split_letters items) of letters that carry a stressable vowel."""
    letters = [i for i, (_, m) in enumerate(items) if m is not None]
    out = []
    for i in letters:
        c, m = items[i]
        if FULL_VOWELS & set(m) or (c == "ו" and "\u05bc" in m):  # vowel sign, or shuruk
            out.append(i)
    # furtive patah (רוּחַ, גָּבֹהַּ): a final ח/ע/הּ with patah is not a syllable of its own
    if len(out) >= 2 and out[-1] == letters[-1]:
        c, m = items[out[-1]]
        if c in "חעה" and "\u05b7" in m and (c != "ה" or "\u05bc" in m):
            out.pop()
    return out


def explicit_stress(text, disagreements=None, predicted=None):
    """Put the stress mark on the stressed vowel's letter: milra by default, mil'el for MILEL words."""
    pred_words = predicted.split(" ") if predicted else []
    words = []
    for wi, w in enumerate(text.split(" ")):
        items = [(c, m.replace(STRESS, "") if m is not None else None) for c, m in split_letters(w)]
        cs = carriers(items)
        if cs:
            k = cs[-2] if (letters_only(w) in MILEL and len(cs) >= 2) else cs[-1]
            c, m = items[k]
            bar = PREFIX if PREFIX in m else ""
            items[k] = (c, m.replace(PREFIX, "") + STRESS + bar)
            if disagreements is not None and wi < len(pred_words) and len(cs) >= 2 and STRESS in pred_words[wi]:
                model_k = next(i for i, (c2, m2) in enumerate(split_letters(pred_words[wi])) if m2 and STRESS in m2)
                if model_k != k:
                    disagreements.append(f"{w}: model {'milra' if model_k == cs[-1] else 'other'}, review "
                                         f"{'milel' if k == cs[-2] else 'milra'}")
        words.append("".join(c + (m or "") for c, m in items))
    return " ".join(words)


# Hebrew Piper voices to try: (repo, model files or None = every .onnx in the repo)
VOICE_REPOS = [
    ("thewh1teagle/phonikud-tts-checkpoints", ["model.onnx", "shaul.onnx", "michael.onnx"]),
    ("thewh1teagle/piper-he-noa", None),
    ("notmax123/piper-medium-heb", None),
]


def find_voices(api, report):
    voices = []
    report["repos"] = {}
    for repo, wanted in VOICE_REPOS:
        try:
            info = api.model_info(repo, files_metadata=True)
        except Exception as e:  # noqa: BLE001
            report["repos"][repo] = f"error: {e!r}"
            continue
        files = {s.rfilename: (s.size or 0) for s in info.siblings}
        card = getattr(info, "card_data", None)
        entry = {"files": files, "license": card.get("license") if card else None, "tags": info.tags}
        if "README.md" in files:
            try:
                entry["readme"] = Path(hf_hub_download(repo, "README.md")).read_text(encoding="utf-8")[:4000]
            except Exception as e:  # noqa: BLE001
                entry["readme"] = f"error: {e!r}"
        report["repos"][repo] = entry
        configs = [f for f in files if f.endswith(".json") and ("config" in f or f.endswith(".onnx.json"))]
        models = wanted or [f for f in files if f.endswith(".onnx") and files[f] < 300_000_000]
        for f in models:
            if f not in files:
                continue
            cfg = next((c for c in (f + ".json", f[:-5] + ".config.json", f[:-5] + ".json") if c in files), None)
            if cfg is None and "model.config.json" in files:
                cfg = "model.config.json"
            if cfg is None and len(configs) == 1:
                cfg = configs[0]
            if cfg:
                voices.append((repo, f, cfg, files[f]))
            else:
                report.setdefault("no_config", []).append(f"{repo}/{f}")
    return voices


def durations_session(model_path):
    """The voice model plus one extra output: frames per input token (the VITS 'w_ceil' tensor)."""
    m = onnx.load(model_path)
    ceils = [n for n in m.graph.node if n.op_type == "Ceil"]
    if not ceils:
        return None
    m.graph.output.append(onnx.helper.make_tensor_value_info(ceils[0].output[0], onnx.TensorProto.FLOAT, None))
    path = model_path + ".dur.onnx"
    onnx.save(m, path)
    return ort.InferenceSession(path, providers=["CPUExecutionProvider"])


def word_timings(piper, sess, phonemes, speaker):
    idmap = piper.phoneme_id_map
    chars = ["^"] + list(phonemes)
    ids, owner = [], []  # owner[i] = index in `phonemes` of the char that produced token i (-1 = BOS/EOS)
    for ci, ch in enumerate(chars):
        if ch not in idmap:
            continue
        for t in idmap[ch] + idmap["_"]:
            ids.append(t)
            owner.append(ci - 1)
    for t in idmap["$"]:
        ids.append(t)
        owner.append(-1)
    inf = piper.config["inference"]
    outs = sess.run(None, piper._create_input(ids, inf["length_scale"], inf["noise_w"], inf["noise_scale"], speaker))
    audio = np.asarray(outs[0]).squeeze()
    frames = np.asarray(outs[-1], dtype=np.float64).reshape(-1)
    if len(frames) != len(ids):
        raise RuntimeError(f"duration tensor has {len(frames)} values for {len(ids)} tokens")
    sr = piper.sample_rate
    hop = len(audio) / frames.sum()
    ends = np.cumsum(frames) * hop / sr
    starts = ends - frames * hop / sr
    c_start, c_end = {}, {}
    for i, ci in enumerate(owner):
        if ci >= 0:
            c_start.setdefault(ci, starts[i])
            c_end[ci] = ends[i]
    pad = set(idmap["_"])
    stats = {"pad_frames_mean": round(float(np.mean([fr for t, fr in zip(ids, frames) if t in pad])), 2),
             "phoneme_frames_mean": round(float(np.mean([fr for t, fr in zip(ids, frames) if t not in pad])), 2)}
    words, pos = [], 0
    for w in phonemes.split(" "):
        idx = [pos + k for k, ch in enumerate(w) if ch not in PUNCT and (pos + k) in c_start]
        if idx:
            words.append({"phonemes": w, "start": round(float(c_start[idx[0]]), 3), "end": round(float(c_end[idx[-1]]), 3)})
        pos += len(w) + 1
    return audio, words, {"hop": hop, **stats}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="voice-out")
    ap.add_argument("--target", type=float, default=4.6,
                    help="seconds for the 10-word reference sentence (same, child-friendly pace for every voice)")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    report = {}
    api = HfApi()

    nakdan = Phonikud(hf_hub_download("thewh1teagle/phonikud-onnx", "phonikud-1.0.onnx"))

    prepared = {}
    for key, text in SAMPLES.items():
        predicted = nakdan.add_diacritics(text)
        merged_raw = initial_vocal_shva(merge(text, predicted))
        applied = []
        merged = explicit_stress(merged_raw, applied, predicted)
        prepared[key] = {
            "text": text,
            "model_nikud": predicted,
            "merged": merged,
            "phonemes": phonemize(merged),
            "phonemes_model_stress": phonemize(merged_raw),
            "phonemes_model_only": phonemize(predicted),
            "model_disagrees": applied,
            "words": [{"word": w, "phonemes": phonemize(w)} for w in merged.split(" ")],
        }
    prepared["_warnings"] = WARNINGS
    (out / "phonemes.json").write_text(json.dumps(prepared, ensure_ascii=False, indent=1), encoding="utf-8")
    del prepared["_warnings"]

    voices = find_voices(api, report)
    report["voices"] = [f"{r}/{f} ({s // 1_000_000} MB)" for r, f, _, s in voices]
    used = set()
    for vi, (repo, f, cfg, _) in enumerate(voices[:8]):
        try:
            model_path = hf_hub_download(repo, f)
            digest = Path(model_path).stat().st_size, Path(model_path).read_bytes()[-4096:]
            if digest in used:
                report.setdefault("duplicates", []).append(f"{repo}/{f}")
                continue
            used.add(digest)
            piper = Piper(model_path, hf_hub_download(repo, cfg))
            inf = piper.config["inference"]
            report.setdefault("configs", {})[f"{repo}/{f}"] = {
                "audio": piper.config.get("audio"), "inference": dict(inf),
                "phoneme_type": piper.config.get("phoneme_type"), "espeak": piper.config.get("espeak")}
            # same pace for every voice: the reference sentence should last about --target seconds
            ref, _ = piper.create(prepared["05_level3"]["phonemes"], speaker_id=0, is_phonemes=True,
                                  length_scale=inf.get("length_scale", 1.0))
            natural = len(ref) / piper.sample_rate
            scale = min(2.0, max(0.3, args.target / natural))
            inf["length_scale"] = round(inf.get("length_scale", 1.0) * scale, 3)
            report.setdefault("pace", {})[f"{repo}/{f}"] = {"natural_s": round(natural, 2), "scale": round(scale, 2),
                                                            "length_scale": inf["length_scale"]}
            idmap = piper.phoneme_id_map
            missing = sorted({ch for p in prepared.values() for ch in p["phonemes"] if ch not in idmap})
            report.setdefault("missing_phonemes", {})[f"{repo}/{f}"] = missing
            speakers = piper.get_voices() or {"default": 0}
            report.setdefault("speakers", {})[f"{repo}/{f}"] = speakers
            sess = None
            try:
                sess = durations_session(model_path)
            except Exception:  # noqa: BLE001
                report.setdefault("timing_errors", {})[f] = traceback.format_exc()
            for sname, sid in list(speakers.items())[:4]:
                if str(sname).lower() in ("ljspeech", "english", "en"):
                    continue
                sid = sid if isinstance(sid, int) else 0
                name = Path(f).stem if Path(f).stem != "model" else repo.split("/")[-1]
                tag = f"v{vi}_{name}" + ("" if sname == "default" else f"_{sname}")
                tag = tag.replace("/", "_").replace(" ", "_")
                for key, p in prepared.items():
                    samples, sr = piper.create(p["phonemes"], speaker_id=sid, is_phonemes=True,
                                               length_scale=inf["length_scale"])
                    sf.write(out / f"{tag}__{key}.wav", samples, sr)
                if sess is not None:
                    try:
                        audio, words, info = word_timings(piper, sess, prepared["05_level3"]["phonemes"], sid)
                        sf.write(out / f"{tag}__05_level3_timed.wav", audio, piper.sample_rate)
                        (out / f"{tag}__timings.json").write_text(
                            json.dumps({**info, "words": words}, ensure_ascii=False, indent=1), encoding="utf-8")
                    except Exception:  # noqa: BLE001
                        report.setdefault("timing_errors", {})[tag] = traceback.format_exc()
        except Exception:  # noqa: BLE001
            report.setdefault("voice_errors", {})[f"{repo}/{f}"] = traceback.format_exc()

    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1, default=str), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=1, default=str)[:8000])
    if not any(p.suffix == ".wav" for p in out.iterdir()):
        sys.exit("no audio produced")


if __name__ == "__main__":
    main()
