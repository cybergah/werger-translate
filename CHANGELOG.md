# Changelog

All notable changes to **Wergêr** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
- Planned: beam search decoding for higher-quality offline translation.
- Planned: smaller/faster model variants (`q4` / `uint8`).
- Planned: NNAPI / XNNPACK execution provider acceleration on Android.

## [1.0.0] — 2026-06-23

First public release of Wergêr by **Cybergah Group**. 🎉

### Added
- **Offline AI translation** using **Meta NLLB-200 distilled 600M** (int8 ONNX)
  running on-device via **ONNX Runtime**.
- Pure-Kotlin/Rust **NLLB BPE tokenizer** — no native libraries, 100% offline.
- Runtime **model download** (~870 MB) into a folder named `Wergêr`
  (or a user-selected, auto-detected folder).
- **Online translation** via a configurable **LibreTranslate** endpoint.
- **Text translation** (words, sentences, paragraphs).
- **Document translation** for `.txt` and `.pdf` files.
- **Photo translation** with OCR.
- **Text-to-speech (TTS)** for translations.
- **Swap languages** with a single tap.
- **Android app** (Jetpack Compose + Kotlin, API 26+).
- **Windows desktop app** (Tauri 2, Rust + WebView) with an opt-in offline
  ONNX engine (`--features offline`).
- Primary language **Kurmancî (Kurdish Latin)**, plus **Soranî**, Türkçe,
  English, Arabic, Farsi, and 200+ languages via NLLB-200.
- Open-source project documentation: multilingual READMEs (Kurmancî, English,
  Türkçe, Soranî, Arabic, Farsi), AGPL-3.0 license, third-party notices,
  contributing guide, code of conduct, and security policy.

### Licensing
- App code released under **GNU AGPL-3.0**.
- NLLB-200 model under **CC-BY-NC-4.0** (non-commercial); see `NOTICE.md`.

[Unreleased]: https://github.com/cybergah/werger/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/cybergah/werger/releases/tag/v1.0.0
