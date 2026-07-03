set -e
cd /c/Users/harun/Desktop/wergir
mkdir -p tools/nllb13_src
base="https://huggingface.co/facebook/nllb-200-distilled-1.3B/resolve/main"
for f in config.json generation_config.json sentencepiece.bpe.model tokenizer.json tokenizer_config.json pytorch_model.bin; do
  echo ">>> downloading $f"
  curl -L -C - --retry 40 --retry-delay 6 --retry-all-errors -A "Mozilla/5.0" -o "tools/nllb13_src/$f" "$base/$f"
done
echo ">>> download complete, exporting"
export PYTHONUTF8=1 PYTHONIOENCODING=utf-8
python tools/export_nllb.py --model "C:/Users/harun/Desktop/wergir/tools/nllb13_src"
echo ">>> ALL DONE"
