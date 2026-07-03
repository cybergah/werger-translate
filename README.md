<div align="center">

<img src="brand/logo_512.png" alt="Wergêr" width="160" height="160" />

# Wergêr

**Wergerandina bi AI ya bêhinternet û serhêl — bo Android û Windows**

_"Tu ziman ne li paş tê hiştin"_

[![Lîsans: AGPL-3.0](https://img.shields.io/badge/Lîsans-AGPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows-success.svg)](#-platform)
[![Made by Cybergah](https://img.shields.io/badge/Çêkir-Cybergah%20Group-7c3aed.svg)](https://cybergah.com)
[![Belaş û Çavkaniya Vekirî](https://img.shields.io/badge/Belaş-Çavkaniya%20Vekirî-brightgreen.svg)](#-felsefeya-çavkaniya-vekirî)

**Kurmancî** ·
[English](README.en.md) ·
[Türkçe](README.tr.md) ·
[سۆرانی](README.ckb.md) ·
[العربية](README.ar.md) ·
[فارسی](README.fa.md)

</div>

---

## 🌍 Wergêr çi ye?

**Wergêr** (bi inglîzî: _translator_) sepaneke wergerandinê ya **belaş** û **çavkaniya wê vekirî** ye, ku ji aliyê [**Cybergah Group**](https://cybergah.com) ve hatiye çêkirin. Ew hem **bêinternet (offline)** û hem jî **serhêl (online)** dixebite, û li ser **Android** û **Windows**ê heye.

Armanca me sade ye: wergerandineke bi kalîte ji bo zimanê **Kurdî** û gelek zimanên din peyda bikin — bê pere, bê reklam, bê şopandina daneyan.

---

## ✨ Taybetmendî

- 📝 **Wergerandina nivîsê** — peyv, hevok û paragraf
- 📄 **Wergerandina belgeyan** — pelên `.txt` û `.pdf`
- 📷 **Wergerandina wêneyan** — OCR (xwendina nivîsê ji wêneyan)
- 🔊 **Deng (TTS)** — nivîsê bi dengî bixwîne
- 🔁 **Guhertina zimanan** — bi yek pêlekê serî û dawiyê biguherîne
- 📶 **Bêinternet** — piştî daxistina modelê bê internetê dixebite
- 🌐 **Serhêl** — bi rêya LibreTranslateê (navnîşan tê eyarkirin)
- 🆓 **Belaş û çavkaniya vekirî** — her tim, ji bo her kesî

---

## 🖼️ Dîmen

> _Dîmenên sepanê dê li vir werin zêdekirin._

<div align="center">
<img src="docs/press/screenshot_placeholder.png" alt="Dîmenê Wergêr" width="280" />
</div>

---

## 💻 Platform

| Platform | Rewş | Avakirin |
|----------|------|----------|
| 🤖 **Android** | API 26+ (Android 8.0+) | Jetpack Compose + Kotlin |
| 🪟 **Windows** | Windows 10/11 | Tauri 2 (Rust + WebView) |

---

## 🔌 Bêinternet & Serhêl

Du motorên wergerandinê hene:

### Motora bêinternet (Offline) — NLLB-200
- Model: **Meta NLLB-200 distilled 600M** (`facebook/nllb-200-distilled-600M`)
- Format: **int8 ONNX** (quantize kirî, mirror ji Xenova)
- Pêkanîn: **ONNX Runtime** li ser cîhazê (CPU)
- Tokenizer: **tokenizera NLLB BPE** bi tevahî bi Kotlin/Rust ji nû ve hatiye nivîsîn — pirtûkxaneya native tune, %100 bêinternet
- Model (~**870 MB**) yek caran tê daxistin û di peldankeke bi navê **Wergêr** de (an peldanka ku te hilbijart, bixweber tê dîtin) tê hilanîn. Piştre sepan **bê internetê** dixebite.

### Motora serhêl (Online) — LibreTranslate
- Endpointa [LibreTranslate](https://libretranslate.com/) tê eyarkirin
- Ji bo wan kesên ku naxwazin modelê daxin

---

## 🗣️ Zimanên ku tên piştgirîkirin

| Ziman | Kod (FLORES-200) | Rewş |
|-------|------------------|------|
| **Kurmancî** (Kurdî Latînî) | `kmr_Latn` | ⭐ Sereke |
| **Soranî** (Kurdî Erebî) | `ckb_Arab` | ✅ |
| Türkçe | `tur_Latn` | ✅ |
| English | `eng_Latn` | ✅ |
| العربية (Erebî) | `arb_Arab` | ✅ |
| فارسی (Farsî) | `pes_Arab` | ✅ |
| ... û gelek zimanên din bi rêya NLLB-200 | 200+ | ✅ |

---

## 📥 Sazkirin

### Android
1. Herî dawî APKyê ji [**Releases**](../../releases) daxe.
2. Pelê `.apk` veke û saz bike (dibe ku divê tu "sazkirina ji çavkaniyên nenas" çalak bikî).
3. Cara yekem ku tu motora bêinternet bi kar tînî, model (~870 MB) tê daxistin.

### Windows
1. Sazkerê `.msi` an `.exe` ji [**Releases**](../../releases) daxe.
2. Sazkerê bimeşîne û Wergêr veke.

---

## 🛠️ Ji çavkaniyê avakirin

### Android (Gradle)
```bash
# APKya debug
./gradlew :app:assembleDebug

# APKya release (bê îmze)
./gradlew :app:assembleRelease
```
Pêdivî: **JDK 17**, Android SDK (API 34), build-tools 34/35.
Encam: `app/build/outputs/apk/debug/app-debug.apk`

### Windows (Tauri)
```bash
cd desktop
npm install
npx tauri build
```
Pêdivî: **Rust** (1.77+), **Node.js**, û pêdiviyên Tauri 2.

> 💡 Motora bêinternet ya ONNX li ser maseyê (desktop) bi taybetî tê vekirin:
> `npx tauri build -- --features offline`

---

## ⚖️ Têbîniya model û lîsansê

Du lîsansên cuda hene — ji kerema xwe haya te jê hebe:

- **Koda sepanê (Wergêr):** [**AGPL-3.0**](LICENSE) — çavkaniya vekirî, belaş.
- **Modela NLLB-200:** [**CC-BY-NC 4.0**](https://creativecommons.org/licenses/by-nc/4.0/) — **ne bo karanîna bazirganî (non-commercial)**.

Ango: koda Wergêr bi azadî tê bikaranîn û belavkirin, lê **modela NLLB-200 nikare ji bo armancên bazirganî were belavkirin**. Ji bo hûragahiyan binêre [`NOTICE.md`](NOTICE.md).

---

## 💚 Felsefeya çavkaniya vekirî

Wergêr **belaş e û dê her tim belaş bimîne**. Em bawer dikin:

- 🔓 **Çavkaniya vekirî** — her kes dikare kodê bibîne, biguherîne û baştir bike.
- 🌐 **Tu ziman ne li paş tê hiştin** — em hewl didin zimanê Kurdî û zimanên din ên kêmtir-piştgirîkirî bi pêş bixin.
- 🛡️ **Nehêştina daneyan** — wergerandina bêinternet tê wateya ku nivîsa te qet ji cîhazê dernakeve.
- ❤️ **Pabendbûna bi zimanê Kurdî** — ev projeyek e bo geşkirin û parastina zimanê Kurdî.

---

## 🏢 Der barê Cybergah Group de

**Wergêr** ji aliyê [**Cybergah Group**](https://cybergah.com) ve tê pêşxistin — komeke ku xwe daye ber teknolojiya vekirî û berdest ji bo her kesî.

🌐 Malper: [cybergah.com](https://cybergah.com)

---

## 🤝 Beşdarî

Beşdariyên te bi xêr hatin! Ji kerema xwe pêşî [`CONTRIBUTING.md`](CONTRIBUTING.md) û [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) bixwîne.

- 🐛 Çewtiyek dîtî? [Issue veke](../../issues).
- 💡 Fikrek heye? [Daxwaza taybetmendiyê](../../issues/new/choose).
- 🌍 Dixwazî zimanekî nû zêde bikî? Binêre [`CONTRIBUTING.md`](CONTRIBUTING.md).

---

## 📜 Lîsans

Koda vê projeyê di bin [**GNU AGPL-3.0**](LICENSE) de ye.
© [Cybergah Group](https://cybergah.com). Modela NLLB-200 di bin CC-BY-NC-4.0 de ye (binêre [`NOTICE.md`](NOTICE.md)).

<div align="center">

**Bi ❤️ ji bo zimanê Kurdî hatiye çêkirin — [Cybergah Group](https://cybergah.com)**

</div>
