# Wergêr — Desktop (Windows)

**Wergêr** (Kurdish for "translator") is a free, open-source online/offline
translation app by **Cybergah Group** ([cybergah.com](https://cybergah.com)).
It specializes in **Kurdish (Kurmancî & Soranî)** alongside Turkish, English,
Arabic, Farsi, and more.

Built with **Tauri v2 + Rust + vanilla HTML/CSS/JS**.

---

## Features

- **Hybrid online/offline** with auto-detection (Mode: Auto / Online / Offline).
- **Online (no API key needed):** works out of the box via key-free providers
  (Google's free endpoint → MyMemory fallback). Optionally point it at your own
  [LibreTranslate](https://libretranslate.com) server (endpoint + optional key)
  in Settings.
- **Offline engine:** NLLB-200 (distilled 600M) via ONNX Runtime — *opt-in*
  behind the `offline` Cargo feature (see below).
- **Text-to-Speech** for source & target: uses the OS (Web Speech) voice when
  one exists, and falls back to an **offline Meta MMS-TTS** voice for languages
  Windows can't speak — most importantly **Kurdish (Kurmancî)**. See
  *Offline Kurdish speech* below.
- **Photo translation (OCR):** pick an image and extract its text offline with
  the built-in **Windows.Media.Ocr** engine, then translate it.
- **Document translation:** pick a `.txt` or `.pdf` and translate its text.
- **6 UI languages:** Kurmancî (default), Soranî, Farsça, Arapça, Türkçe,
  İngilizce — with RTL layout for Soranî/Arabic/Farsi.
- **~50 translation languages** (FLORES-200 codes), source/target pickers, swap.
  The downloaded NLLB-200 model actually supports ~200; the picker exposes a
  curated subset (extend it in `src/i18n.js` + `src-tauri/src/lang.rs`).
- **Model manager:** download button, folder picker, auto-detection of existing
  model files.

---

## Prerequisites

- **Rust** 1.77+ (`cargo`)
- **Node** 18+ (`npm`)
- Windows 11 (MSVC toolchain + WebView2, which ships with Windows 11).

The Tauri CLI is installed locally as a dev dependency — use `npx tauri …`.

---

## Quick start

```bash
cd desktop
npm install          # installs @tauri-apps/cli + api
npx tauri dev        # run in development (hot-reloads the frontend)
```

## Build the installer (default, online-only)

```bash
npx tauri build
```

This produces, under `src-tauri/target/release/bundle/`:

- `msi/Wergêr_1.0.0_x64_en-US.msi`  (WiX MSI installer)
- `nsis/Wergêr_1.0.0_x64-setup.exe` (NSIS setup)

The standalone executable is at `src-tauri/target/release/werger.exe`.

> The **default build is online-only** so it always compiles and runs without
> needing the (large) ONNX Runtime binaries. The Kurdish-strong offline engine
> is opt-in.

---

## Enabling the offline engine (`offline` feature)

The offline NLLB-200 engine uses the [`ort`](https://crates.io/crates/ort) crate
(ONNX Runtime 2.x). It is **off by default** to guarantee the out-of-the-box
build succeeds. To build with it:

```bash
# Pass the feature through to cargo:
npx tauri build -- --features offline

# or for dev:
npx tauri dev -- --features offline
```

`ort` downloads the ONNX Runtime native libraries at build time
(`download-binaries` + `tls-rustls`, already configured in `Cargo.toml`). The
first build with `offline` therefore needs network access and takes longer. No
system OpenSSL is required — `ort` uses `rustls`/`ring`.

### Getting the models

The app downloads three files from
`huggingface.co/Xenova/nllb-200-distilled-600M` into a folder named **`Wergêr`**
(default `%USERPROFILE%\Documents\Wergêr`):

| Remote file                                   | Saved as        |
| --------------------------------------------- | --------------- |
| `onnx/encoder_model_quantized.onnx`           | `encoder.onnx`  |
| `onnx/decoder_model_merged_quantized.onnx`    | `decoder.onnx`  |
| `tokenizer.json`                              | `tokenizer.json`|

Use **Settings → Offline models → Download models**, or point the folder picker
at a directory that already contains those three files (it auto-detects them).

> **Why offline?** LibreTranslate's Kurdish coverage is limited; the offline
> NLLB engine is the real Kurdish workhorse for both Kurmancî and Soranî.

---

## Offline Kurdish speech (MMS-TTS)

Windows ships no Kurdish TTS voice, so the 🔊 button uses Meta's **MMS-TTS**
(VITS) models offline via ONNX Runtime. This needs the **`offline` feature**
(it reuses `ort`).

Meta publishes these models only as PyTorch, so convert them to ONNX **once**
with the included tool:

```bash
pip install "torch>=2.1" "transformers>=4.33" onnx onnxscript
python tools/export_mms_tts.py --lang kmr_Latn      # Kurmancî
python tools/export_mms_tts.py --all                # every supported language
```

> The tool uses the legacy TorchScript ONNX exporter (`dynamo=False`) because the
> newer dynamo exporter can't lower some VITS ops. Verified: Kurmancî export
> produces ~3 s of speech for a test sentence at 16 kHz.

This writes, under the models folder (`%USERPROFILE%\Documents\Wergêr`):

```
tts/<flores>/model.onnx
tts/<flores>/vocab.json
tts/<flores>/tokenizer_config.json
tts/<flores>/config.json
```

The app auto-detects these. When a voice is missing, the OS voice is used; if
neither exists, the UI says so.

> **Sorani (ckb):** Meta has **no** Central-Kurdish TTS model — only Kurmancî
> (`mms-tts-kmr-script_latin`). So offline speech is available for Kurmancî but
> not Soranî. Supported languages are listed in
> `src-tauri/src/tts.rs::mms_repo_for`.

> **OCR language packs:** Windows OCR recognizes only the scripts whose packs
> are installed (Settings → Time & Language → Language → Optional features).
> Latin Kurmancî works with the English/Turkish pack; Arabic-script text needs
> the Arabic OCR pack. There is no Kurdish OCR pack.

---

## Project layout

```
desktop/
├─ package.json            # npm scripts + Tauri CLI
├─ src/                    # frontend (vanilla HTML/CSS/JS)
│  ├─ index.html
│  ├─ styles.css
│  ├─ main.js              # app logic, Tauri command calls, TTS
│  ├─ i18n.js              # 6-language UI dictionary + lang tables
│  └─ assets/logo.png      # logo for UI / About page
└─ src-tauri/
   ├─ Cargo.toml           # `offline` feature gates the ONNX engine
   ├─ tauri.conf.json
   ├─ icons/               # generated from brand/logo_1024.png
   └─ src/
      ├─ main.rs           # entry
      ├─ lib.rs            # Tauri commands + state
      ├─ lang.rs           # FLORES-200 ↔ LibreTranslate ↔ NLLB id maps
      ├─ online.rs         # LibreTranslate client + connectivity probe
      ├─ models.rs         # detect + download model files (with progress)
      ├─ tokenizer.rs      # NLLB BPE tokenizer  (offline feature)
      ├─ offline.rs        # encoder + KV-cached decoder  (offline feature)
      ├─ tts.rs            # Meta MMS-TTS (VITS) speech  (offline feature)
      ├─ ocr.rs            # Windows.Media.Ocr photo OCR (Windows only)
      └─ docs.rs           # .txt / .pdf text extraction
```

The MMS-TTS export tool lives at `tools/export_mms_tts.py`.

---

## License & credits

Made with love by **Cybergah Group** — [cybergah.com](https://cybergah.com).
Open source and free, forever.
