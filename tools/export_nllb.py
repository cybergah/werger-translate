#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Export a larger/better NLLB-200 model to ONNX for Wergêr's offline engine.

Meta/Xenova only publish ONNX for the 600M model, so the higher-quality
**distilled-1.3B** must be exported once. This produces the same file layout
(encoder + merged KV-cache decoder) the engine already understands; the engine
auto-detects the layer count (12 vs 24), so no recompile is needed.

Requires Optimum:  pip install "optimum[exporters]" onnx onnxruntime
Usage:
    python tools/export_nllb.py                       # 1.3B -> Documents/Wergêr
    python tools/export_nllb.py --model facebook/nllb-200-distilled-600M
    python tools/export_nllb.py --out "D:/models/Wergêr"
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def default_out():
    home = os.environ.get("USERPROFILE") or os.environ.get("HOME") or "."
    return os.path.join(home, "Documents", "Wergêr")


def main():
    ap = argparse.ArgumentParser(description="Export an NLLB-200 model to ONNX for Wergêr.")
    ap.add_argument("--model", default="facebook/nllb-200-distilled-1.3B")
    ap.add_argument("--out", default=None)
    ap.add_argument("--opset", type=int, default=14)
    args = ap.parse_args()

    out = args.out or default_out()
    os.makedirs(out, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix="nllb_onnx_")

    print(f"Exporting {args.model} to ONNX (this downloads ~5 GB and is slow)…")
    from optimum.exporters.onnx import main_export

    main_export(
        model_name_or_path=args.model,
        output=tmp,
        task="text2text-generation-with-past",
        opset=args.opset,
    )

    # Map optimum's output names to what the engine expects.
    pairs = [
        ("encoder_model.onnx", "encoder.onnx"),
        ("decoder_model_merged.onnx", "decoder.onnx"),
        ("tokenizer.json", "tokenizer.json"),
    ]
    for src, dst in pairs:
        s = os.path.join(tmp, src)
        if not os.path.isfile(s):
            sys.exit(f"Expected {src} from the export but it's missing in {tmp}. "
                     f"Files: {os.listdir(tmp)}")
        shutil.copyfile(s, os.path.join(out, dst))
        print(f"  {src} -> {dst}")

    # Mark the installed variant so the in-app downloader won't clobber it.
    with open(os.path.join(out, ".variant"), "w", encoding="utf-8") as f:
        f.write("best")

    shutil.rmtree(tmp, ignore_errors=True)
    print(f"\nDone. Models in: {out}")
    print("Restart Wergêr (offline build) — it auto-detects the 1.3B model.")


if __name__ == "__main__":
    main()
