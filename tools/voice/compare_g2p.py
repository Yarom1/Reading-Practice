#!/usr/bin/env python3
"""
Diagnostic: how far are the phonemes we feed the 'noa' voice from the convention it was trained
on (renikud)? Prints renikud's symbol inventory, compares every word, and renders A/B samples.

  python tools/voice/compare_g2p.py --catalog utterances.json --words review/words.tsv --out g2p-out
"""
import argparse
import collections
import json
import sys
import unicodedata
from pathlib import Path

import numpy as np
import onnxruntime as ort
import soundfile as sf
from huggingface_hub import hf_hub_download

SAMPLES = [
    "הַגָּמָל חַי בַּמִּדְבָּר, שָׁם חַם מְאוֹד וְיֵשׁ מְעַט מַיִם.",
    "דָּנִי יָצָא לְטַיֵּל בַּגִּנָּה. הוּא רָאָה פְּרָחִים צִבְעוֹנִיִּים וְעֵצִים גְּבוֹהִים.",
    "בַּחֹרֶף יוֹרֵד גֶּשֶׁם. טִיף, טִיף, טִפּוֹת עַל הַחַלּוֹן.",
    "אֲנִי זוֹרֵק כַּדּוּר גָּדוֹל, וְדָנִי תּוֹפֵס אוֹתוֹ בַּגַּן.",
    "עַכְשָׁיו אַתָּה! אֶצְבַּע מִתַּחַת לַמִּלִּים, וְקָדִימָה.",
]


def strip_marks(s):
    return "".join(c for c in s if not ("֑" <= c <= "ׇ"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--catalog", required=True)
    ap.add_argument("--words", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    report = {}

    from renikud_onnx import G2P
    model_path = hf_hub_download("renikud/renikud", "model.onnx")
    g2p = G2P(model_path)
    meta = ort.InferenceSession(model_path).get_modelmeta().custom_metadata_map
    report["consonant_vocab"] = json.loads(meta["consonant_vocab"])
    report["vowel_vocab"] = json.loads(meta["vowel_vocab"])
    report["geresh_map"] = json.loads(meta.get("geresh_map", "{}"))

    # every distinct word: ours vs renikud (renikud reads the unvocalized sentence, like in training)
    ours = {}
    for line in Path(args.words).read_text(encoding="utf-8").splitlines()[1:]:
        c = line.split("\t")
        ours[c[0]] = c[1]
    catalog = json.loads(Path(args.catalog).read_text(encoding="utf-8"))
    theirs = {}
    for e in catalog:
        if e["kind"] not in ("line", "msg", "vocab"):
            continue
        plain = strip_marks(e["text"])
        ph = g2p.phonemize(plain).split()
        if len(ph) != len(e["cores"]):
            continue
        for core, p in zip(e["cores"], ph):
            theirs.setdefault(core, p.strip(".,!?:;\"'"))
    sym_ours = collections.Counter(ch for p in ours.values() for ch in p)
    sym_theirs = collections.Counter(ch for p in theirs.values() for ch in p)
    report["symbols_ours_only"] = {k: v for k, v in sym_ours.items() if k not in sym_theirs}
    report["symbols_renikud_only"] = {k: v for k, v in sym_theirs.items() if k not in sym_ours}

    def nostress(p):
        return p.replace("ˈ", "")

    same = diff_stress = diff_sound = 0
    rows = []
    for w, p in ours.items():
        r = theirs.get(w)
        if r is None:
            continue
        if p == r:
            same += 1
        elif nostress(p) == nostress(r):
            diff_stress += 1
        else:
            diff_sound += 1
            rows.append(f"{w}\tours={p}\trenikud={r}")
    report["compared"] = {"same": same, "stress_only": diff_stress, "sounds_differ": diff_sound}
    (out / "sound_differences.tsv").write_text("\n".join(rows), encoding="utf-8")

    # A/B: the same sentences with our phonemes, with renikud symbols, and renikud's own reading
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from piper_onnx import Piper
    voice = hf_hub_download("thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx")
    piper = Piper(voice, hf_hub_download("thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx.json"))
    by_text = {e["text"]: e for e in catalog}
    for n, text in enumerate(SAMPLES, 1):
        e = by_text.get(unicodedata.normalize("NFC", text))
        if e is None:
            report.setdefault("missing_samples", []).append(text)
            continue
        a = " ".join(ours.get(c, "") + t[len(t.rstrip(".,!?:;")):] for c, t in zip(e["cores"], e["tokens"]))
        b = a.replace("ɡ", "g")
        c = g2p.phonemize(strip_marks(text))
        report.setdefault("ab", {})[str(n)] = {"A_ours": a, "B_g": b, "C_renikud": c}
        for tag, ph in (("A_ours", a), ("B_g", b), ("C_renikud", c)):
            audio, sr = piper.create(ph, speaker_id=0, is_phonemes=True, length_scale=0.9)
            sf.write(out / f"{n}_{tag}.wav", audio, sr)
    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "ab"}, ensure_ascii=False, indent=1)[:6000])
    for k, v in report.get("ab", {}).items():
        print(k, json.dumps(v, ensure_ascii=False))


if __name__ == "__main__":
    main()
