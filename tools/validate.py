# -*- coding: utf-8 -*-
"""
End-to-end validation that mirrors the Kotlin Translator/Tokenizer exactly:
  1. Verifies the pure-Python reimplementation of the BPE tokenizer (same logic as
     NllbTokenizer.kt) matches the ground-truth `tokenizers` library.
  2. Runs encoder + merged KV-cached decoder with greedy decoding and prints
     translations, proving the I/O contract and decode loop are correct.
"""
import sys, unicodedata, re, json
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

ROOT = r"C:\Users\harun\Desktop\wergir\tools"
ONNX = ROOT + r"\onnx"

tok = Tokenizer.from_file(ROOT + r"\tokenizer.json")
vocab = tok.get_vocab()                       # token -> id
id_to_token = {i: t for t, i in vocab.items()}

# ---- load merges into a rank map, mirroring the Kotlin loader ----
tj = json.load(open(ROOT + r"\tokenizer.json", encoding="utf-8"))
merges = tj["model"]["merges"]
merge_ranks = {m: r for r, m in enumerate(merges)}

META = "▁"
UNK_ID, EOS_ID, PAD_ID, BOS_ID = 3, 2, 1, 0
LANG = {"tur_Latn":256184,"kmr_Latn":256099,"ckb_Arab":256038,"eng_Latn":256047,
        "arb_Arab":256011,"pes_Arab":256053}

def normalize(text):
    s = unicodedata.normalize("NFKC", text)
    s = re.sub(r"\s+", " ", s).strip()
    return s

def bpe(word):
    symbols = list(word)
    if len(symbols) < 2:
        return symbols
    while True:
        best_rank, best_idx = 1 << 30, -1
        for j in range(len(symbols) - 1):
            r = merge_ranks.get(symbols[j] + " " + symbols[j + 1])
            if r is not None and r < best_rank:
                best_rank, best_idx = r, j
        if best_idx < 0:
            break
        a, b = symbols[best_idx], symbols[best_idx + 1]
        merged, nxt, k = a + b, [], 0
        while k < len(symbols):
            if k < len(symbols) - 1 and symbols[k] == a and symbols[k + 1] == b:
                nxt.append(merged); k += 2
            else:
                nxt.append(symbols[k]); k += 1
        if len(nxt) == len(symbols):
            break
        symbols = nxt
        if len(symbols) < 2:
            break
    return symbols

def my_tokenize_ids(text):
    norm = normalize(text)
    if not norm:
        return []
    ids = []
    for word in norm.split(" "):
        if not word:
            continue
        for sym in bpe(META + word):
            wid = vocab.get(sym)
            if wid is not None:
                ids.append(wid)
            else:
                if not ids or ids[-1] != UNK_ID:
                    ids.append(UNK_ID)
    return ids

# ---- 1) Tokenizer parity check ----
samples = [
    "Merhaba, nasılsın?",
    "Bugün hava çok güzel ve güneşli.",
    "I would like to learn the Kurdish language.",
    "Em ê sibê biçin bajêr.",
    "Türkiye'nin başkenti Ankara'dır.",
]
print("==== TOKENIZER PARITY (mine vs tokenizers) ====")
all_ok = True
for s in samples:
    gold = tok.encode(s, add_special_tokens=False).ids
    mine = my_tokenize_ids(s)
    ok = gold == mine
    all_ok = all_ok and ok
    print(("OK " if ok else "DIFF"), repr(s))
    if not ok:
        print("   gold:", gold)
        print("   mine:", mine)
print("PARITY:", "ALL OK" if all_ok else "MISMATCH")

# ---- 2) Full translation pipeline ----
enc = ort.InferenceSession(ONNX + r"\encoder.onnx", providers=["CPUExecutionProvider"])
dec = ort.InferenceSession(ONNX + r"\decoder_merged.onnx", providers=["CPUExecutionProvider"])
NUM_LAYERS, NUM_HEADS, HEAD_DIM = 12, 16, 64

def empty_past():
    return np.zeros((1, NUM_HEADS, 0, HEAD_DIM), dtype=np.float32)

def translate(text, src, tgt, max_new=80):
    ids = [LANG[src]] + my_tokenize_ids(text) + [EOS_ID]
    input_ids = np.array([ids], dtype=np.int64)
    attn = np.ones_like(input_ids)
    enc_hidden = enc.run(["last_hidden_state"], {"input_ids": input_ids, "attention_mask": attn})[0]
    enc_mask = attn

    out_names = [o.name for o in dec.get_outputs()]

    def build_inputs(dec_ids, use_cache, past_dec, past_enc):
        d = {
            "input_ids": np.array([dec_ids], dtype=np.int64),
            "encoder_attention_mask": enc_mask,
            "encoder_hidden_states": enc_hidden,
            "use_cache_branch": np.array([use_cache], dtype=bool),
        }
        for i in range(NUM_LAYERS):
            if use_cache:
                d[f"past_key_values.{i}.decoder.key"] = past_dec[i][0]
                d[f"past_key_values.{i}.decoder.value"] = past_dec[i][1]
                d[f"past_key_values.{i}.encoder.key"] = past_enc[i][0]
                d[f"past_key_values.{i}.encoder.value"] = past_enc[i][1]
            else:
                d[f"past_key_values.{i}.decoder.key"] = empty_past()
                d[f"past_key_values.{i}.decoder.value"] = empty_past()
                d[f"past_key_values.{i}.encoder.key"] = empty_past()
                d[f"past_key_values.{i}.encoder.value"] = empty_past()
        return d

    def split_present(outs):
        m = dict(zip(out_names, outs))
        pd = [(m[f"present.{i}.decoder.key"], m[f"present.{i}.decoder.value"]) for i in range(NUM_LAYERS)]
        pe = [(m[f"present.{i}.encoder.key"], m[f"present.{i}.encoder.value"]) for i in range(NUM_LAYERS)]
        return m["logits"], pd, pe

    # prefill
    outs = dec.run(None, build_inputs([EOS_ID, LANG[tgt]], False, None, None))
    logits, past_dec, past_enc = split_present(outs)
    next_id = int(np.argmax(logits[0, -1]))
    gen = []
    steps = 0
    while next_id != EOS_ID and steps < max_new:
        gen.append(next_id)
        outs = dec.run(None, build_inputs([next_id], True, past_dec, past_enc))
        logits, past_dec, _ = split_present(outs)
        next_id = int(np.argmax(logits[0, 0]))
        steps += 1
    return tok.decode(gen, skip_special_tokens=True)

print("\n==== TRANSLATIONS ====")
tests = [
    ("Merhaba, nasılsın?", "tur_Latn", "kmr_Latn"),
    ("Bugün hava çok güzel.", "tur_Latn", "kmr_Latn"),
    ("I want to learn Kurdish.", "eng_Latn", "tur_Latn"),
    ("Em ê sibê biçin bajêr.", "kmr_Latn", "tur_Latn"),
    ("Su içmek istiyorum.", "tur_Latn", "eng_Latn"),
    ("Bijî azadî!", "kmr_Latn", "tur_Latn"),
]
for text, s, t in tests:
    print(f"[{s} -> {t}] {text!r}")
    print("   =>", repr(translate(text, s, t)))
