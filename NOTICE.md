# Third-Party Notices — Wergêr

Wergêr is developed by **Cybergah Group** (https://cybergah.com) and the Wergêr
application **source code** is licensed under the **GNU AGPL-3.0** (see
[`LICENSE`](LICENSE)).

This product, however, includes and/or depends on third-party components and a
machine-translation **model** that are distributed under their own, separate
licenses. Those licenses are listed below. **Some of these licenses impose
restrictions that differ from AGPL-3.0** — most importantly, the NLLB-200 model
is licensed for **non-commercial use only**.

---

## ⚠️ Important: model license affects redistribution

The offline translation engine uses the **Meta NLLB-200** model. The model is
licensed under **CC-BY-NC-4.0 (Creative Commons Attribution-NonCommercial 4.0)**.

- The Wergêr **application code** (AGPL-3.0) may be used, modified and
  redistributed freely, including commercially.
- The **NLLB-200 model weights** may **NOT** be used or redistributed for
  **commercial purposes**. This is a restriction of the model license, not of
  the Wergêr code.
- For this reason the model is **not bundled** in this repository. The app
  **downloads the model at runtime** from its upstream source into a local
  folder named `Wergêr`.

If you fork, repackage or distribute Wergêr, you are responsible for complying
with **both** the AGPL-3.0 (for the code) **and** CC-BY-NC-4.0 (for the model).

---

## Components and licenses

### 1. NLLB-200 model (Meta)
- **Component:** `facebook/nllb-200-distilled-600M` (No Language Left Behind)
- **Copyright:** © Meta Platforms, Inc. and affiliates
- **License:** **CC-BY-NC-4.0** (non-commercial) — https://creativecommons.org/licenses/by-nc/4.0/
- **Use in Wergêr:** offline translation engine. Downloaded at runtime; not
  redistributed in this repository.

### 2. Xenova ONNX conversion
- **Component:** int8 / quantized ONNX export of NLLB-200 (Transformers.js / Xenova mirror)
- **License:** the conversion artifacts derive from the NLLB-200 model and
  therefore inherit the model's **CC-BY-NC-4.0** terms. Conversion tooling is
  Apache-2.0.
- **Use in Wergêr:** provides the ONNX encoder + merged/KV-cache decoder weights
  used by the offline engine.

### 3. ONNX Runtime (Microsoft)
- **Component:** `com.microsoft.onnxruntime:onnxruntime-android` (Android) and
  the `ort` crate / ONNX Runtime binaries (desktop, opt-in `offline` feature)
- **Copyright:** © Microsoft Corporation
- **License:** **MIT** — https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- **Use in Wergêr:** on-device inference of the NLLB-200 ONNX model.

### 4. LibreTranslate
- **Component:** LibreTranslate (online translation engine, configurable endpoint)
- **License:** **AGPL-3.0** — https://github.com/LibreTranslate/LibreTranslate
- **Use in Wergêr:** optional online translation backend. Wergêr communicates
  with a LibreTranslate server over HTTP; the server software itself is not
  bundled.

### 5. Tesseract OCR (if used for photo translation)
- **Component:** Tesseract OCR
- **Copyright:** © Google Inc. and contributors
- **License:** **Apache-2.0** — https://github.com/tesseract-ocr/tesseract/blob/main/LICENSE
- **Use in Wergêr:** optical character recognition for the photo-translation
  feature. (Where platform-native OCR is used instead, that component's platform
  license applies.)

### 6. Tauri
- **Component:** Tauri 2 framework, `@tauri-apps/api`, `@tauri-apps/cli`,
  `tauri-plugin-dialog`, `tauri-plugin-opener`
- **Copyright:** © Tauri Programme within The Commons Conservancy
- **License:** **MIT / Apache-2.0 (dual)** — https://github.com/tauri-apps/tauri
- **Use in Wergêr:** Windows desktop application shell (Rust + WebView).

### 7. Jetpack Compose, AndroidX, Kotlin
- **Components:** AndroidX Core, Lifecycle, Activity Compose, Compose UI,
  Material3, Material Icons Extended, KotlinX Coroutines
- **Copyright:** © Google LLC, © JetBrains s.r.o.
- **License:** **Apache-2.0** — https://www.apache.org/licenses/LICENSE-2.0
- **Use in Wergêr:** Android application UI and runtime.

### 8. Rust crates (desktop)
- **Components:** `serde`, `serde_json`, `reqwest`, `rustls`, `ndarray`, `ort`
- **License:** **MIT / Apache-2.0 (dual)** for most; see each crate's repository.
- **Use in Wergêr:** HTTP, serialization and (optional) offline inference on the
  desktop build.

### 9. Fonts
- Any bundled UI fonts are distributed under the **SIL Open Font License (OFL-1.1)**
  or the platform's default system fonts. Where Wergêr relies on system fonts,
  no font is redistributed and the platform license applies.

---

## Attribution

> "No Language Left Behind" and NLLB-200 are research outputs of Meta AI.
> Wergêr is an independent, community project by Cybergah Group and is **not**
> affiliated with or endorsed by Meta.

If you believe a component is missing from this notice, please open an issue or
contact us at **security@cybergah.com**.
