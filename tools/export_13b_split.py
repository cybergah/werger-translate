# -*- coding: utf-8 -*-
"""One-off: export NLLB-1.3B to SPLIT (un-merged) ONNX decoders + inspect I/O."""
import glob
import os
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SRC = r"C:\Users\harun\Desktop\wergir\tools\nllb13_src"
OUT = r"C:\Users\harun\Desktop\wergir\tools\nllb13_onnx"

from optimum.exporters.onnx import main_export

main_export(
    model_name_or_path=SRC,
    output=OUT,
    task="text2text-generation-with-past",
    opset=14,
    no_post_process=True,  # skip the >2GB merge; keep decoders separate
)
print(">>> EXPORT DONE")

import onnx

for f in sorted(glob.glob(os.path.join(OUT, "*.onnx"))):
    m = onnx.load(f, load_external_data=False)
    ins = [i.name for i in m.graph.input]
    outs = [o.name for o in m.graph.output]
    print("\n===", os.path.basename(f), "===")
    print("INPUTS :", ins)
    print("OUTPUTS:", outs[:6], ("... (+%d)" % (len(outs) - 6)) if len(outs) > 6 else "")
