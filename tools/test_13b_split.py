# -*- coding: utf-8 -*-
"""Validate the split 1.3B ONNX by translating a sentence (en->fr, en->tr)."""
import sys
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

D = r"C:\Users\harun\Desktop\wergir\tools\nllb13_onnx"
SRC = r"C:\Users\harun\Desktop\wergir\tools\nllb13_src"

tok = AutoTokenizer.from_pretrained(SRC)
enc = ort.InferenceSession(D + r"\encoder_model.onnx", providers=["CPUExecutionProvider"])
dec0 = ort.InferenceSession(D + r"\decoder_model.onnx", providers=["CPUExecutionProvider"])
decp = ort.InferenceSession(D + r"\decoder_with_past_model.onnx", providers=["CPUExecutionProvider"])

dec0_out = [o.name for o in dec0.get_outputs()]
decp_out = [o.name for o in decp.get_outputs()]
NL = sum(1 for o in dec0_out if o.endswith(".decoder.key"))
EOS = 2

def translate(text, src_lang, tgt_lang, max_new=60):
    tok.src_lang = src_lang
    enc_in = tok(text, return_tensors="np")
    ids = enc_in["input_ids"].astype(np.int64)
    mask = enc_in["attention_mask"].astype(np.int64)
    hidden = enc.run(["last_hidden_state"], {"input_ids": ids, "attention_mask": mask})[0]
    tgt_id = tok.convert_tokens_to_ids(tgt_lang)

    # prefill: [</s>, tgt_lang]
    feed = {"encoder_attention_mask": mask, "input_ids": np.array([[EOS, tgt_id]], np.int64),
            "encoder_hidden_states": hidden}
    out = dec0.run(dec0_out, feed)
    o = dict(zip(dec0_out, out))
    dec_kv = {i: (o[f"present.{i}.decoder.key"], o[f"present.{i}.decoder.value"]) for i in range(NL)}
    enc_kv = {i: (o[f"present.{i}.encoder.key"], o[f"present.{i}.encoder.value"]) for i in range(NL)}
    nxt = int(np.argmax(o["logits"][0, -1]))

    gen = []
    for _ in range(max_new):
        if nxt == EOS:
            break
        gen.append(nxt)
        feed = {"encoder_attention_mask": mask, "input_ids": np.array([[nxt]], np.int64)}
        for i in range(NL):
            feed[f"past_key_values.{i}.decoder.key"] = dec_kv[i][0]
            feed[f"past_key_values.{i}.decoder.value"] = dec_kv[i][1]
            feed[f"past_key_values.{i}.encoder.key"] = enc_kv[i][0]
            feed[f"past_key_values.{i}.encoder.value"] = enc_kv[i][1]
        out = decp.run(decp_out, feed)
        o = dict(zip(decp_out, out))
        for i in range(NL):
            dec_kv[i] = (o[f"present.{i}.decoder.key"], o[f"present.{i}.decoder.value"])
        nxt = int(np.argmax(o["logits"][0, -1]))
    return tok.decode(gen, skip_special_tokens=True)

print("layers:", NL)
print("en->fr:", translate("Hello, how are you today?", "eng_Latn", "fra_Latn"))
print("en->tr:", translate("Hello, how are you today?", "eng_Latn", "tur_Latn"))
print("en->kmr:", translate("Good morning, my friend.", "eng_Latn", "kmr_Latn"))
