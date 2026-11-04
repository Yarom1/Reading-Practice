#!/usr/bin/env python3
"""
Builds the app's recorded voice (assets/speech) from the speech catalog exported by
SpeechCatalogTest (SPEECH_EXPORT=utterances.json).

For every text:
  1. our hand-checked nikud is kept;
  2. the Phonikud nakdan model adds only what standard nikud cannot express: vocal shva (where we
     wrote a shva), prefix boundaries and qamats qatan (where we wrote a qamats);
  3. the stress comes from review only: milra by default, other words are listed in stress.tsv;
  4. Phonikud turns the text into IPA and the Piper voice "noa" speaks it;
  5. word start/end times come from the voice model's own duration predictor, so the reading
     screen highlights exactly the word that is heard.

Outputs (in --out):
  speech/manifest.json + speech/<hash>.mp3  -> copied to app/src/main/assets/speech
  review/words.tsv, review/utterances.tsv   -> stress review lists
With --dry only the review lists are written (no models needed, runs anywhere).
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unicodedata
from pathlib import Path

from phonikud import phonemize

HERE = Path(__file__).resolve().parent
STRESS, VSHVA, PREFIX = "֫", "ֽ", "|"
SHVA, QAMATS, QAMATS_QATAN, DAGESH, PATAH = "ְ", "ָ", "ׇ", "ּ", "ַ"
SEGOL, TSERE, HOLAM, HIRIQ = "ֶ", "ֵ", "ֹ", "ִ"
VOWELS = set("ֱֲֳִֵֶַָׇֹֺֻ")
FULL_VOWELS = VOWELS - set("ֱֲֳ")
PREFIX_LETTERS = set("והבכלמש")

VOICE_REPO, VOICE_FILE, VOICE_CONFIG = "thewh1teagle/piper-he-noa", "he_IL-noa-medium.onnx", "he_IL-noa-medium.onnx.json"
NAKDAN_REPO, NAKDAN_FILE = "thewh1teagle/phonikud-onnx", "phonikud-1.0.onnx"


# ----------------------------------------------------------------------------- text helpers

def nfc(s):
    return unicodedata.normalize("NFC", s)


def is_letter(c):
    return "א" <= c <= "ת"


def is_mark(c):
    return "֑" <= c <= "ׇ" and c not in "־׀׃׆"


def split_letters(s):
    """[(letter, marks)] for Hebrew letters, (char, None) for anything else."""
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if is_letter(c):
            j = i + 1
            while j < len(s) and (is_mark(s[j]) or s[j] == PREFIX):
                j += 1
            out.append((c, s[i + 1:j]))
            i = j
        else:
            out.append((c, None))
            i += 1
    return out


def join_items(items):
    return "".join(c + (m or "") for c, m in items)


def letters_only(s):
    return "".join(c for c in s if is_letter(c))


def is_word_core(c):
    return c.isalnum() or is_mark(c) or c == PREFIX


def trim_punct(w):
    s, e = 0, len(w)
    while s < e and not is_word_core(w[s]):
        s += 1
    while e > s and not is_word_core(w[e - 1]):
        e -= 1
    return w[:s], w[s:e], w[e:]


def line_tokens(text):
    """Same rules as the app's LineTokens: whitespace-separated runs that contain a letter or digit;
    punctuation-only runs are attached to the previous word (or the next one at the start)."""
    spans, pending, i, n = [], -1, 0, len(text)
    while i < n:
        if text[i].isspace():
            i += 1
            continue
        s = i
        while i < n and not text[i].isspace():
            i += 1
        if any(ch.isalnum() for ch in text[s:i]):
            spans.append([pending if pending >= 0 else s, i])
            pending = -1
        elif spans:
            spans[-1][1] = i
        elif pending < 0:
            pending = s
    return [text[a:b] for a, b in spans]


# ----------------------------------------------------------------------------- stress

def strip_first_dagesh(core):
    items = split_letters(core)
    for i, (c, m) in enumerate(items):
        if m is not None:
            items[i] = (c, m.replace(DAGESH, ""))
            break
    return join_items(items)


def load_lexicon(path):
    """stress.tsv: <word> TAB <syllable counted from the end: 1 milra, 2 mil'el, 3 ...> or /phonemes/.
    A word without nikud matches by its letters, also after prefix letters (ו ה ב כ ל מ ש).
    A word with nikud matches that vocalized form only (homographs), also after prefixes.
    Several words with nikud ("אָנָה בָּאָה") set the stress of the last one in that phrase only."""
    lex = {"letters": {}, "exact": {}, "phon": {}, "phrase": {}}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        key, value = [x.strip() for x in line.split("\t") if x.strip()][:2]
        key = nfc(key)
        if " " in key:
            lex["phrase"][tuple(key.split())] = int(value)
        elif value.startswith("/"):
            lex["phon"][key] = value.strip("/")
        elif any(is_mark(c) for c in key):
            lex["exact"][strip_first_dagesh(key)] = int(value)
        else:
            lex["letters"][key] = int(value)
    return lex


def lexicon_stress(core, lex):
    """(syllable from the end, source) - lexicon first, then the reliable automatic patterns, then milra."""
    plain = core.replace(STRESS, "").replace(VSHVA, "").replace(PREFIX, "")
    items = split_letters(plain)
    letter_idx = [i for i, (_, m) in enumerate(items) if m is not None]
    lw = letters_only(plain)
    for cut in range(0, 4):
        if cut and (cut > len(letter_idx) - 2 or not all(ch in PREFIX_LETTERS for ch in lw[:cut])):
            break
        rest_items = items[letter_idx[cut]:] if cut else items
        rest = join_items(rest_items)
        tag = "" if cut == 0 else f"-{lw[:cut]}"
        key = strip_first_dagesh(rest)
        if key in lex["exact"]:
            return lex["exact"][key], "exact" + tag
        if letters_only(rest) in lex["letters"]:
            return lex["letters"][letters_only(rest)], "lex" + tag
    auto = auto_stress(plain)
    if auto:
        return auto
    return 1, "default"


def auto_stress(core):
    """Patterns that are reliably not milra."""
    items = split_letters(core)
    cs = carriers(items)
    if len(cs) < 2:
        return None
    letters = [i for i, (_, m) in enumerate(items) if m is not None]
    last_c, last_m = items[cs[-1]]
    final = letters[-1]
    # segolates and present feminine (כֶּלֶב, בֹּקֶר, הוֹלֶכֶת): segol in a closed final syllable,
    # but not the milra suffixes -הֶם -הֶן -כֶם -כֶן
    if SEGOL in last_m and final != cs[-1] and items[final][0] != "ה":
        if not (items[final][0] in "םן" and last_c in "הכ"):
            return 2, "auto-segolate"
    # -ayim / -ayit (מַיִם, בַּיִת, עֵינַיִם): unvoweled-dagesh-free yod with hiriq after patah
    if last_c == "י" and HIRIQ in last_m and DAGESH not in last_m and PATAH in items[cs[-2]][1] and final != cs[-1]:
        return 2, "auto-ayim"
    if (len(letters) >= 3 and items[final][0] == "ה" and QAMATS in items[final][1]
            and items[letters[-2]][0] == "י" and not items[letters[-2]][1] and SEGOL in items[letters[-3]][1]):  # -ֶיהָ
        return 2, "auto-eha"
    if final == cs[-1] and items[final][0] == "ת" and QAMATS in items[final][1]:  # past -תָּ
        return 2, "auto-ta"
    return None


def carriers(items):
    """Indices of letters carrying a stressable vowel (vowel sign, holam/shuruk vav); a final ח/ע/הּ
    with patah after a vowel is a furtive patah and not a syllable of its own."""
    letters = [i for i, (_, m) in enumerate(items) if m is not None]
    out = []
    for i in letters:
        c, m = items[i]
        if FULL_VOWELS & set(m) or (c == "ו" and DAGESH in m):
            out.append(i)
    if len(out) >= 2 and out[-1] == letters[-1]:
        c, m = items[out[-1]]
        if c in "חעה" and PATAH in m and (c != "ה" or DAGESH in m):
            out.pop()
    return out


def place_stress(core, syllable_from_end):
    items = [(c, m.replace(STRESS, "") if m is not None else None) for c, m in split_letters(core)]
    cs = carriers(items)
    if not cs:
        return join_items(items)
    k = cs[max(0, len(cs) - syllable_from_end)]
    c, m = items[k]
    bar = PREFIX if PREFIX in m else ""
    items[k] = (c, m.replace(PREFIX, "") + STRESS + bar)
    return join_items(items)


def suspicious(core):
    """Patterns that are usually not milra - only used to point the review at likely mistakes."""
    items = split_letters(core)
    cs = carriers(items)
    if len(cs) < 2:
        return ""
    v = [next((x for x in items[i][1] if x in FULL_VOWELS), "") for i in cs]
    last2 = (v[-2], v[-1])
    flags = []
    if last2[1] == SEGOL and last2[0] in (SEGOL, TSERE, HOLAM, QAMATS, PATAH):
        flags.append("segolate")
    if last2 == (PATAH, PATAH) and any(items[i][0] in "חעה" for i in cs[-2:]):
        flags.append("segolate-guttural")
    tail = letters_only(core)
    if items[cs[-1]][0] == "י" and HIRIQ in items[cs[-1]][1] and tail.endswith(("ים", "ית")) and PATAH in items[cs[-2]][1]:
        flags.append("ayim/ayit")
    if tail.endswith(("תי", "נו")) and len(cs) >= 2 and core.endswith(("תִּי", "תִי", "נוּ")):
        flags.append("past-1st?")
    if core.endswith(("תָּ", "תָ")):
        flags.append("past-2nd?")
    if core.endswith("ֶיהָ"):
        flags.append("-eha")
    return ",".join(flags)


# ----------------------------------------------------------------------------- nakdan marks

def merge(ours, predicted):
    """Our nikud + the model's vocal shva (only on our shva), prefix bar and qamats qatan (only on our qamats)."""
    pred = [m for _, m in split_letters(predicted) if m is not None]
    out, k = [], 0
    for c, marks in split_letters(nfc(ours)):
        if marks is None:
            out.append(c)
            continue
        pm = pred[k] if k < len(pred) else ""
        k += 1
        new = marks.replace(QAMATS, QAMATS_QATAN) if (QAMATS in marks and QAMATS_QATAN in pm) else marks
        if VSHVA in pm and SHVA in marks:
            new += VSHVA
        new += PREFIX if PREFIX in pm else ""
        out.append(c + new)
    return "".join(out)


def model_stress_from_end(predicted_core):
    """Which syllable (counted from the end) the model stressed; 0 if it marked none. Review hint only."""
    items = split_letters(predicted_core)
    cs = carriers(items)
    marked = [i for i, (_, m) in enumerate(items) if m and STRESS in m]
    if not marked or not cs:
        return 0
    later = [x for x in cs if x >= marked[0]]  # a mark before holam male / shuruk belongs to the vav
    return len(cs) - cs.index(later[0]) if later else 1


BASE = {"ך": "כ", "ם": "מ", "ן": "נ", "ף": "פ", "ץ": "צ"}


def add_vshva(items, i):
    c, m = items[i]
    if SHVA in m and VSHVA not in m:
        bar = PREFIX if PREFIX in m else ""
        items[i] = (c, m.replace(PREFIX, "") + VSHVA + bar)


def initial_vocal_shva(core):
    """Vocal shva that the nakdan model does not always mark:
    - word-initial shva on ל/מ/נ/ר/י, before א/ע/ה, and on the prefixes ו/כ/ל/ב (Phonikud's rule);
    - the same after the conjunction וּ (וּמְמַלֵּא = u-me-male);
    - a shva between two identical letters (מְלָפְפוֹן, לִקְּקָה) can only be pronounced."""
    items = split_letters(core)
    idx = [i for i, (_, m) in enumerate(items) if m is not None]
    if not idx:
        return core

    def initial_rule(k):
        c0, m0 = items[idx[k]]
        nxt = items[idx[k + 1]][0] if k + 1 < len(idx) else ""
        if c0 in "למנרי" or nxt in "אעה" or (c0 in "וכלב" and PREFIX in m0):
            add_vshva(items, idx[k])

    initial_rule(0)
    c0, m0 = items[idx[0]]
    if c0 == "ו" and DAGESH in m0 and not (VOWELS & set(m0)) and len(idx) > 1:
        initial_rule(1)
    for a, b in zip(idx, idx[1:]):
        if BASE.get(items[a][0], items[a][0]) == BASE.get(items[b][0], items[b][0]):
            add_vshva(items, a)
    # two shvas in a row inside a word: the second is pronounced (נִכְנְסוּ, תַּרְנְגֹלֶת), unless it ends the word (הִצְלַחְתְּ)
    for a, b in zip(idx, idx[1:]):
        if SHVA in items[a][1] and SHVA in items[b][1] and b != idx[-1]:
            add_vshva(items, b)
    return join_items(items)



# ----------------------------------------------------------------------------- voice with stress control

STRESS_RATIO = 1.4  # the stressed vowel lasts at least this much longer than the longest other vowel of the word
STRESS_MAX_STRETCH = 1.8


class Voice:
    """A Piper (VITS) voice run in two passes: the model first predicts how long every sound is; in
    each word the stressed vowel is then made clearly the longest, and the audio is rendered with
    exactly those durations. ('noa' marks final stress only weakly on its own.)"""

    def __init__(self, model_path, config_path, speaker=0, length_scale=0.9, enforce_stress=True, pitch_accent=0.0):
        import numpy as np
        import onnx
        import onnxruntime as ort

        self.np = np
        cfg = json.loads(Path(config_path).read_text(encoding="utf-8"))
        self.idmap = cfg["phoneme_id_map"]
        self.sr = cfg["audio"]["sample_rate"]
        inf = cfg.get("inference", {})
        self.noise_scale = inf.get("noise_scale", 0.667)
        self.noise_w = inf.get("noise_w", 0.8)
        self.length_scale = length_scale
        self.speaker = speaker
        self.enforce_stress = enforce_stress
        self.pitch_accent = pitch_accent
        model = onnx.load(model_path)
        ceil = next(n for n in model.graph.node if n.op_type == "Ceil")
        self.w_name, frames_name = ceil.input[0], ceil.output[0]
        # pass 1: the original model, also returning the predicted durations
        m1 = onnx.ModelProto()
        m1.CopyFrom(model)
        m1.graph.output.append(onnx.helper.make_tensor_value_info(self.w_name, onnx.TensorProto.FLOAT, None))
        # pass 2: durations come from an input
        m2 = onnx.ModelProto()
        m2.CopyFrom(model)
        next(n for n in m2.graph.node if n.op_type == "Ceil").input[0] = "w_override"
        m2.graph.input.append(onnx.helper.make_tensor_value_info("w_override", onnx.TensorProto.FLOAT, [1, 1, None]))
        m2.graph.output.append(onnx.helper.make_tensor_value_info(frames_name, onnx.TensorProto.FLOAT, None))
        opts = ort.SessionOptions()
        self.sess1 = ort.InferenceSession(m1.SerializeToString(), opts, providers=["CPUExecutionProvider"])
        self.sess2 = ort.InferenceSession(m2.SerializeToString(), opts, providers=["CPUExecutionProvider"])
        self.has_sid = "sid" in [i.name for i in self.sess1.get_inputs()]

    def _feeds(self, ids):
        np = self.np
        f = {
            "input": np.array([ids], dtype=np.int64),
            "input_lengths": np.array([len(ids)], dtype=np.int64),
            "scales": np.array([self.noise_scale, self.length_scale, self.noise_w], dtype=np.float32),
        }
        if self.has_sid:
            f["sid"] = np.array([self.speaker], dtype=np.int64)
        return f

    def synth(self, phon, ranges):
        """Audio for `phon`; `ranges` are the [start, end) character ranges of the words in it.
        Returns (audio, per-token frames, owner) where owner[i] is the character of token i (-1 = BOS/EOS)."""
        np = self.np
        chars = ["^"] + list(phon)
        ids, owner = [], []
        for ci, ch in enumerate(chars):
            if ch in self.idmap:
                for t in self.idmap[ch] + self.idmap["_"]:
                    ids.append(t)
                    owner.append(ci - 1)
        for t in self.idmap["$"]:
            ids.append(t)
            owner.append(-1)
        feeds = self._feeds(ids)
        (w,) = self.sess1.run([self.w_name], feeds)
        w = np.array(w, dtype=np.float32).reshape(1, 1, -1)
        if self.enforce_stress:
            self.stress_durations(w[0, 0], phon, owner, ranges)
        feeds["w_override"] = w
        audio, frames = self.sess2.run(None, feeds)
        audio = np.asarray(audio, dtype=np.float32).reshape(-1)
        frames = np.asarray(frames, dtype=np.float64).reshape(-1)
        if self.pitch_accent:
            audio = self.accent(audio, frames, phon, owner, ranges)
        return audio, frames, owner

    def accent(self, audio, frames, phon, owner, ranges):
        """Raise the pitch of every stressed vowel a little (PSOLA, durations unchanged) and lower the
        vowels after it in the same word, so the stress is heard on the right syllable."""
        np = self.np
        import parselmouth
        from parselmouth.praat import call

        hop = len(audio) / frames.sum()
        ends = np.cumsum(frames) * hop / self.sr
        starts = ends - frames * hop / self.sr
        span = {}
        for i, ci in enumerate(owner):
            if ci >= 0:
                a, b = span.get(ci, (starts[i], ends[i]))
                span[ci] = (min(a, starts[i]), max(b, ends[i]))
        snd = parselmouth.Sound(audio.astype(np.float64), sampling_frequency=self.sr)
        manip = call(snd, "To Manipulation", 0.01, 75, 600)
        tier = call(manip, "Extract pitch tier")
        changed = False
        for a, b in ranges:
            marks = [k for k in range(a, b) if phon[k] == "ˈ"]
            vowels = [k for k in range(a, b) if phon[k] in "aeiou"]
            if not marks or len(vowels) < 2:
                continue
            v = next((k for k in vowels if k > marks[0]), None)
            if v is None or marks[0] not in span or v not in span:
                continue
            final = b < len(phon) and phon[b] in ".!?"
            up = 1 + self.pitch_accent * (0.5 if final else 1.0)
            call(tier, "Multiply frequencies", span[marks[0]][0], span[v][1], up)
            for k in vowels:
                if k > v and k in span:
                    call(tier, "Multiply frequencies", span[k][0], span[k][1], 1 - self.pitch_accent / 3)
            changed = True
        if not changed:
            return audio
        call([tier, manip], "Replace pitch tier")
        out = call(manip, "Get resynthesis (overlap-add)")
        res = np.asarray(out.values[0], dtype=np.float32)
        return res[: len(audio)] if len(res) >= len(audio) else np.pad(res, (0, len(audio) - len(res)))

    @staticmethod
    def stress_durations(w, phon, owner, ranges):
        """In every word, stretch the stressed vowel (with its stress-mark token) to at least
        STRESS_RATIO times the longest other vowel."""
        tokens_of = {}
        for i, ci in enumerate(owner):
            if ci >= 0:
                tokens_of.setdefault(ci, []).append(i)

        def dur(ci):
            return float(sum(w[i] for i in tokens_of.get(ci, [])))

        for a, b in ranges:
            marks = [k for k in range(a, b) if phon[k] == "ˈ"]
            if not marks:
                continue
            m = marks[0]
            v = next((k for k in range(m + 1, b) if phon[k] in "aeiou"), None)
            if v is None:
                continue
            others = [k for k in range(a, b) if phon[k] in "aeiou" and k != v]
            if not others:
                continue
            stressed = dur(m) + dur(v)
            longest = max(dur(k) for k in others)
            if stressed <= 0 or stressed >= STRESS_RATIO * longest:
                continue
            f = min(STRESS_RATIO * longest / stressed, STRESS_MAX_STRETCH)
            for ci in (m, v):
                for i in tokens_of.get(ci, []):
                    w[i] *= f

# ----------------------------------------------------------------------------- main pipeline

class Words:
    """One pronunciation per vocalized word, shared by every sentence and the single-word recording."""

    def __init__(self, lex):
        self.lex = lex
        self.table = {}

    def add(self, core, marked_core, predicted_core, example):
        if core in self.table:
            self.table[core]["count"] += 1
            return
        phon = self.lex["phon"]
        syl, source = lexicon_stress(core, self.lex)
        base = initial_vocal_shva(marked_core)
        stressed = place_stress(base, syl)
        fixed = phon.get(core) or phon.get(letters_only(core))
        model = model_stress_from_end(predicted_core) if predicted_core else 0
        self.table[core] = {
            "phonemes": fixed or phonemize(stressed), "stress": syl, "source": "phonemes" if fixed else source,
            "model": model, "flags": suspicious(core), "count": 1, "example": example, "marked": stressed, "base": base,
        }

    def phonemes(self, core, previous=()):
        """The word's phonemes; a phrase entry in the lexicon may change the stress in one context."""
        for phrase, syl in self.lex["phrase"].items():
            before = phrase[:-1]
            if phrase[-1] == core and len(previous) >= len(before) and tuple(previous[len(previous) - len(before):]) == before:
                return phonemize(place_stress(self.table[core]["base"], syl))
        return self.table[core]["phonemes"]


def utterance_phonemes(tokens, words):
    """Phoneme string for a list of app tokens plus the [start, end) range of each token's core in it."""
    parts, ranges, pos, seen = [], [], 0, []
    for tok in tokens:
        lead, core, trail = trim_punct(tok)
        ph = words.phonemes(core, tuple(seen)) if core else ""
        seen.append(core)
        lead_p = "".join(ch for ch in lead if ch in ",.!?:;")
        trail_p = "".join(ch for ch in trail if ch in ",.!?:;")
        piece = lead_p + ph + trail_p
        start = pos + len(lead_p)
        ranges.append((start, start + len(ph)))
        parts.append(piece)
        pos += len(piece) + 1
    return " ".join(parts), ranges


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--catalog", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--lexicon", default=str(HERE / "stress.tsv"))
    ap.add_argument("--dry", action="store_true", help="review lists only: no nakdan, no audio")
    ap.add_argument("--length-scale", type=float, default=0.9)
    ap.add_argument("--bitrate", default="48k")
    ap.add_argument("--raw-stress", action="store_true", help="do not enforce the stressed vowel's length")
    ap.add_argument("--pitch-accent", type=float, default=0.0, help="raise stressed vowels' pitch by this fraction")
    args = ap.parse_args()

    out = Path(args.out)
    (out / "review").mkdir(parents=True, exist_ok=True)
    catalog = json.loads(Path(args.catalog).read_text(encoding="utf-8"))
    lex = load_lexicon(args.lexicon)
    words = Words(lex)

    nakdan = None
    if not args.dry:
        from huggingface_hub import hf_hub_download
        from phonikud_onnx import Phonikud
        nakdan = Phonikud(hf_hub_download(NAKDAN_REPO, NAKDAN_FILE))

    # 1) pronunciation of every word, taken from its first sentence (lines come before single words)
    errors = []
    for e in catalog:
        text, tokens, cores = e["text"], e["tokens"], e["cores"]
        if line_tokens(text) != tokens:
            errors.append(f"tokenization differs from the app: {text}")
            continue
        if all(c in words.table for c in cores):
            for c in cores:
                words.table[c]["count"] += 1
            continue
        predicted = nakdan.add_diacritics(text) if nakdan else ""
        merged = merge(text, predicted) if nakdan else nfc(text)
        m_tokens = line_tokens(merged)
        p_tokens = line_tokens(predicted) if predicted else [""] * len(tokens)
        if len(m_tokens) != len(tokens) or len(p_tokens) != len(tokens):
            errors.append(f"marks changed the word count: {text}")
            m_tokens, p_tokens = tokens, [""] * len(tokens)
        for tok, mt, pt in zip(tokens, m_tokens, p_tokens):
            core = trim_punct(tok)[1]
            if core:
                words.add(core, trim_punct(mt)[1], trim_punct(pt)[1] if pt else "", text)

    # 2) review lists
    with open(out / "review" / "words.tsv", "w", encoding="utf-8") as f:
        f.write("word\tphonemes\tstress\tsource\tmodel\tflags\tcount\texample\n")
        for core, w in words.table.items():
            model = "" if not w["model"] else ("agrees" if w["model"] == w["stress"] else f"says {w['model']}")
            f.write(f"{core}\t{w['phonemes']}\t{w['stress']}\t{w['source']}\t{model}\t{w['flags']}\t{w['count']}\t{w['example']}\n")
    with open(out / "review" / "utterances.tsv", "w", encoding="utf-8") as f:
        for e in catalog:
            if all(trim_punct(t)[1] in words.table for t in e["tokens"]):
                f.write(f"{e['kind']}\t{e['text']}\t{utterance_phonemes(e['tokens'], words)[0]}\n")
    print(f"{len(catalog)} texts, {len(words.table)} distinct words, {len(errors)} problems")
    for msg in errors[:30]:
        print("  !", msg)
    if args.dry:
        return 1 if errors else 0

    # 3) audio
    import numpy as np
    import soundfile as sf
    from huggingface_hub import hf_hub_download
    voice = Voice(hf_hub_download(VOICE_REPO, VOICE_FILE), hf_hub_download(VOICE_REPO, VOICE_CONFIG),
                  speaker=0, length_scale=args.length_scale, enforce_stress=not args.raw_stress,
                  pitch_accent=args.pitch_accent)
    sr = voice.sr

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    speech = out / "speech"
    speech.mkdir(parents=True, exist_ok=True)
    manifest = {"version": 1, "voice": "noa", "items": {}}
    tmp = Path(tempfile.mkdtemp())
    for n, e in enumerate(catalog, 1):
        text, tokens = e["text"], e["tokens"]
        phon, ranges = utterance_phonemes(tokens, words)
        audio, frames, owner = voice.synth(phon, ranges)
        hop = len(audio) / frames.sum()
        ends = np.cumsum(frames) * hop
        starts = ends - frames * hop
        c_start, c_end = {}, {}
        for i, ci in enumerate(owner):
            if ci >= 0:
                c_start.setdefault(ci, starts[i])
                c_end[ci] = ends[i]
        spans = []
        for a, b in ranges:
            idx = [k for k in range(a, b) if k in c_start]
            spans.append((c_start[idx[0]], c_end[idx[-1]]) if idx else None)
        known = [s for s in spans if s]
        # trim silence: keep 80 ms before the first word and 180 ms after the last one
        cut0 = max(0, int(known[0][0] - 0.08 * sr)) if known else 0
        cut1 = min(len(audio), int(known[-1][1] + 0.18 * sr)) if known else len(audio)
        audio = audio[cut0:cut1]
        # same loudness everywhere (RMS of the louder frames), peaks kept below -1 dBFS
        frame = max(1, int(0.02 * sr))
        rms = np.sqrt(np.mean(audio[: len(audio) // frame * frame].reshape(-1, frame) ** 2, axis=1)) if len(audio) >= frame else np.array([0.0])
        loud = rms[rms >= np.percentile(rms, 50)] if rms.size else rms
        level = float(np.sqrt(np.mean(loud ** 2))) if loud.size else 0.0
        if level > 1e-4:
            audio = audio * (0.1 / level)
        peak = float(np.max(np.abs(audio))) if audio.size else 0.0
        if peak > 0.89:
            audio = audio * (0.89 / peak)
        words_ms, last_end = [], 0
        for s in spans:
            if s is None:
                words_ms.append([last_end, last_end])
                continue
            a = int(round((s[0] - cut0) * 1000 / sr))
            b = int(round((s[1] - cut0) * 1000 / sr))
            words_ms.append([max(0, a), max(a, b)])
            last_end = max(a, b)
        name = hashlib.sha1(text.encode("utf-8")).hexdigest()[:16] + ".mp3"
        wav = tmp / "x.wav"
        sf.write(wav, audio, sr, subtype="PCM_16")
        subprocess.run([ffmpeg, "-loglevel", "error", "-y", "-i", str(wav), "-codec:a", "libmp3lame",
                        "-b:a", args.bitrate, "-ac", "1", "-ar", str(sr), str(speech / name)], check=True)
        manifest["items"][text] = {"f": name, "d": int(round(len(audio) * 1000 / sr)), "w": words_ms}
        if n % 100 == 0:
            print(f"  {n}/{len(catalog)}", flush=True)
    (speech / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    total = sum(v["d"] for v in manifest["items"].values()) / 1000
    size = sum(p.stat().st_size for p in speech.iterdir())
    print(f"done: {len(manifest['items'])} recordings, {total / 60:.1f} minutes, {size / 1e6:.1f} MB")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
