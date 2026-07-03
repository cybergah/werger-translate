<div align="center">

<img src="brand/logo_512.png" alt="Wergêr" width="160" height="160" />

# Wergêr

**Offline + online AI translation — for Android and Windows**

_"No language left behind"_

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows-success.svg)](#-platforms)
[![Made by Cybergah](https://img.shields.io/badge/Made%20by-Cybergah%20Group-7c3aed.svg)](https://cybergah.com)
[![Free & Open Source](https://img.shields.io/badge/Free-Open%20Source-brightgreen.svg)](#-open-source--free-philosophy)

[Kurmancî](README.md) ·
**English** ·
[Türkçe](README.tr.md) ·
[سۆرانی](README.ckb.md) ·
[العربية](README.ar.md) ·
[فارسی](README.fa.md)

</div>

---

## 🌍 What is Wergêr?

**Wergêr** (Kurdish for _"translator"_) is a **free, open-source** translation app made by [**Cybergah Group**](https://cybergah.com). It works both **offline** and **online**, and is available on **Android** and **Windows**.

Our goal is simple: provide high-quality translation for the **Kurdish** language and many others — no cost, no ads, no data tracking.

---

## ✨ Features

- 📝 **Text translation** — words, sentences and paragraphs
- 📄 **Document translation** — `.txt` and `.pdf` files
- 📷 **Photo translation** — OCR (read text from images)
- 🔊 **Text-to-speech (TTS)** — read translations aloud
- 🔁 **Swap languages** — flip source and target with one tap
- 📶 **Offline** — works without internet after the model is downloaded
- 🌐 **Online** — via LibreTranslate (configurable endpoint)
- 🆓 **Free & open source** — always, for everyone

---

## 🖼️ Screenshots

> _App screenshots will be added here._

<div align="center">
<img src="docs/press/screenshot_placeholder.png" alt="Wergêr screenshot" width="280" />
</div>

---

## 💻 Platforms

| Platform | Requirements | Built with |
|----------|--------------|------------|
| 🤖 **Android** | API 26+ (Android 8.0+) | Jetpack Compose + Kotlin |
| 🪟 **Windows** | Windows 10/11 | Tauri 2 (Rust + WebView) |

---

## 🔌 Offline & Online

There are two translation engines:

### Offline engine — NLLB-200
- Model: **Meta NLLB-200 distilled 600M** (`facebook/nllb-200-distilled-600M`)
- Format: **int8 ONNX** (quantized, Xenova mirror)
- Runtime: **ONNX Runtime** on-device (CPU)
- Tokenizer: the **NLLB BPE tokenizer** fully rewritten in pure Kotlin/Rust — no native libraries, 100% offline
- The model (~**870 MB**) is downloaded once into a folder named **Wergêr** (or a user-selected folder, auto-detected). After that the app runs **without internet**.

### Online engine — LibreTranslate
- Configurable [LibreTranslate](https://libretranslate.com/) endpoint
- For users who prefer not to download the model

---

## 🗣️ Supported languages

| Language | Code (FLORES-200) | Status |
|----------|-------------------|--------|
| **Kurmancî** (Kurdish Latin) | `kmr_Latn` | ⭐ Primary |
| **Soranî** (Kurdish Arabic) | `ckb_Arab` | ✅ |
| Türkçe | `tur_Latn` | ✅ |
| English | `eng_Latn` | ✅ |
| Arabic | `arb_Arab` | ✅ |
| Farsi | `pes_Arab` | ✅ |
| ... and many more via NLLB-200 | 200+ | ✅ |

---

## 📥 Install

### Android
1. Download the latest APK from [**Releases**](../../releases).
2. Open the `.apk` and install (you may need to allow "install from unknown sources").
3. The first time you use the offline engine, the model (~870 MB) is downloaded.

### Windows
1. Download the `.msi` or `.exe` installer from [**Releases**](../../releases).
2. Run the installer and open Wergêr.

---

## 🛠️ Build from source

### Android (Gradle)
```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (unsigned)
./gradlew :app:assembleRelease
```
Requirements: **JDK 17**, Android SDK (API 34), build-tools 34/35.
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Windows (Tauri)
```bash
cd desktop
npm install
npx tauri build
```
Requirements: **Rust** (1.77+), **Node.js**, and the Tauri 2 prerequisites.

> 💡 The offline ONNX engine on desktop is opt-in:
> `npx tauri build -- --features offline`

---

## ⚖️ Model & licensing note

Two different licenses apply — please be aware:

- **App code (Wergêr):** [**AGPL-3.0**](LICENSE) — open source, free.
- **NLLB-200 model:** [**CC-BY-NC 4.0**](https://creativecommons.org/licenses/by-nc/4.0/) — **non-commercial use only**.

In other words: the Wergêr code may be used and redistributed freely, but the **NLLB-200 model may not be redistributed for commercial purposes**. See [`NOTICE.md`](NOTICE.md) for details.

---

## 💚 Open-source & free philosophy

Wergêr is **free and will always be free**. We believe in:

- 🔓 **Open source** — anyone can read, modify and improve the code.
- 🌐 **No language left behind** — we strive to advance Kurdish and other under-supported languages.
- 🛡️ **Privacy** — offline translation means your text never leaves your device.
- ❤️ **Commitment to Kurdish** — this is a project to grow and preserve the Kurdish language.

---

## 🏢 About Cybergah Group

**Wergêr** is developed by [**Cybergah Group**](https://cybergah.com) — a group committed to open technology that is accessible to everyone.

🌐 Website: [cybergah.com](https://cybergah.com)

---

## 🤝 Contributing

Contributions are welcome! Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) first.

- 🐛 Found a bug? [Open an issue](../../issues).
- 💡 Have an idea? [Request a feature](../../issues/new/choose).
- 🌍 Want to add a language? See [`CONTRIBUTING.md`](CONTRIBUTING.md).

---

## 📜 License

This project's code is licensed under [**GNU AGPL-3.0**](LICENSE).
© [Cybergah Group](https://cybergah.com). The NLLB-200 model is licensed under CC-BY-NC-4.0 (see [`NOTICE.md`](NOTICE.md)).

<div align="center">

**Made with ❤️ for the Kurdish language — [Cybergah Group](https://cybergah.com)**

</div>
