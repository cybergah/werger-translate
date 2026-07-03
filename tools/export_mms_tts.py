#!/usr/bin/env python3
"""
Export Meta's MMS-TTS (VITS) voices to ONNX for Wergêr's offline speech engine.

Meta publishes these models only as PyTorch, but the desktop app runs ONNX via
ONNX Runtime. Run this once per language to produce the layout the Rust `tts`
module loads:

    <models>/tts/<flores>/model.onnx
    <models>/tts/<flores>/vocab.json
    <models>/tts/<flores>/tokenizer_config.json
    <models>/tts/<flores>/config.json

Usage:
    pip install "torch>=2.1" "transformers>=4.33" onnx
    # Default models folder on Windows is %USERPROFILE%\\Documents\\Wergêr
    python export_mms_tts.py --lang kmr_Latn
    python export_mms_tts.py --lang kmr_Latn --out "C:/Users/me/Documents/Wergêr"
    python export_mms_tts.py --all          # every supported language

Note: Meta has NO Sorani (ckb) TTS model — only Kurmancî (kmr). The app falls
back to the OS voice for languages without an MMS model.
"""

import argparse
import os
import sys
from pathlib import Path

# Force UTF-8 stdout so torch's progress prints (which contain emoji) don't crash
# on Windows consoles using a non-UTF-8 code page (e.g. Turkish cp1254).
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

# FLORES-200 code -> HuggingFace MMS-TTS repo. Keep in sync with
# `desktop/src-tauri/src/tts.rs::mms_repo_for`.
REPOS = {
    "kmr_Latn": "facebook/mms-tts-kmr-script_latin",
    "tur_Latn": "facebook/mms-tts-tur",
    "eng_Latn": "facebook/mms-tts-eng",
    "arb_Arab": "facebook/mms-tts-ara",
    "pes_Arab": "facebook/mms-tts-fas",
    "rus_Cyrl": "facebook/mms-tts-rus",
    "deu_Latn": "facebook/mms-tts-deu",
    "fra_Latn": "facebook/mms-tts-fra",
    "spa_Latn": "facebook/mms-tts-spa",
}


def default_models_dir() -> Path:
    home = os.environ.get("USERPROFILE") or os.environ.get("HOME") or "."
    return Path(home) / "Documents" / "Wergêr"


def export_one(flores: str, out_root: Path):
    import torch
    from transformers import AutoTokenizer, VitsModel

    repo = REPOS[flores]
    out_dir = out_root / "tts" / flores
    out_dir.mkdir(parents=True, exist_ok=True)
    print(f"[{flores}] loading {repo} …")

    model = VitsModel.from_pretrained(repo)
    model.eval()
    tok = AutoTokenizer.from_pretrained(repo)

    sample = tok("test", return_tensors="pt")

    class Wrapper(torch.nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, input_ids, attention_mask):
            return self.m(input_ids=input_ids, attention_mask=attention_mask).waveform

    onnx_path = out_dir / "model.onnx"
    print(f"[{flores}] exporting ONNX -> {onnx_path}")
    # VITS uses control flow the new dynamo exporter can't lower (e.g.
    # aten._is_all_true), so use the legacy TorchScript exporter (dynamo=False),
    # which traces cleanly through it.
    export_kwargs = dict(
        input_names=["input_ids", "attention_mask"],
        output_names=["waveform"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "time"},
            "attention_mask": {0: "batch", 1: "time"},
            "waveform": {0: "batch", 1: "samples"},
        },
        opset_version=14,
    )
    try:
        torch.onnx.export(
            Wrapper(model),
            (sample["input_ids"], sample["attention_mask"]),
            str(onnx_path),
            dynamo=False,
            **export_kwargs,
        )
    except TypeError:
        # Older torch versions have no `dynamo` kwarg (legacy is the default).
        torch.onnx.export(
            Wrapper(model),
            (sample["input_ids"], sample["attention_mask"]),
            str(onnx_path),
            **export_kwargs,
        )

    # Tokenizer files (vocab.json + tokenizer_config.json + special_tokens_map.json)
    tok.save_pretrained(str(out_dir))
    # Model config (sampling_rate lives here).
    model.config.to_json_file(str(out_dir / "config.json"))

    print(f"[{flores}] done -> {out_dir}")


def main():
    ap = argparse.ArgumentParser(description="Export MMS-TTS voices to ONNX for Wergêr.")
    ap.add_argument("--lang", help="FLORES-200 code, e.g. kmr_Latn")
    ap.add_argument("--all", action="store_true", help="Export every supported language")
    ap.add_argument("--out", help="Models folder (default: Documents/Wergêr)")
    args = ap.parse_args()

    out_root = Path(args.out) if args.out else default_models_dir()

    if args.all:
        langs = list(REPOS.keys())
    elif args.lang:
        if args.lang not in REPOS:
            sys.exit(f"Unknown language {args.lang!r}. Supported: {', '.join(REPOS)}")
        langs = [args.lang]
    else:
        sys.exit("Pass --lang <flores> or --all. Supported: " + ", ".join(REPOS))

    for flores in langs:
        export_one(flores, out_root)

    print("\nAll exports complete. Restart Wergêr (offline build) and the 🔊 button")
    print("will use these voices for languages without an OS voice (e.g. Kurdish).")


if __name__ == "__main__":
    main()
