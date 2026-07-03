<div align="center">

<img src="brand/logo_512.png" alt="Wergêr" width="160" height="160" />

# Wergêr

**Çevrimdışı + çevrimiçi yapay zekâ çevirisi — Android ve Windows için**

_"Hiçbir dil geride kalmasın"_

[![Lisans: AGPL-3.0](https://img.shields.io/badge/Lisans-AGPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows-success.svg)](#-platformlar)
[![Cybergah tarafından](https://img.shields.io/badge/Yapan-Cybergah%20Group-7c3aed.svg)](https://cybergah.com)
[![Ücretsiz ve Açık Kaynak](https://img.shields.io/badge/Ücretsiz-Açık%20Kaynak-brightgreen.svg)](#-açık-kaynak--ücretsiz-felsefesi)

[Kurmancî](README.md) ·
[English](README.en.md) ·
**Türkçe** ·
[سۆرانی](README.ckb.md) ·
[العربية](README.ar.md) ·
[فارسی](README.fa.md)

</div>

---

## 🌍 Wergêr nedir?

**Wergêr** (Kürtçe: _"çevirmen"_), [**Cybergah Group**](https://cybergah.com) tarafından geliştirilen **ücretsiz** ve **açık kaynaklı** bir çeviri uygulamasıdır. Hem **çevrimdışı** hem de **çevrimiçi** çalışır ve **Android** ile **Windows** üzerinde kullanılabilir.

Amacımız basit: **Kürtçe** ve diğer birçok dil için kaliteli çeviri sunmak — ücretsiz, reklamsız ve veri takibi olmadan.

---

## ✨ Özellikler

- 📝 **Metin çevirisi** — kelimeler, cümleler ve paragraflar
- 📄 **Belge çevirisi** — `.txt` ve `.pdf` dosyaları
- 📷 **Fotoğraf çevirisi** — OCR (görüntülerden metin okuma)
- 🔊 **Sesli okuma (TTS)** — çevirileri sesli oku
- 🔁 **Dilleri değiştir** — kaynak ve hedefi tek dokunuşla çevir
- 📶 **Çevrimdışı** — model indirildikten sonra internetsiz çalışır
- 🌐 **Çevrimiçi** — LibreTranslate ile (yapılandırılabilir adres)
- 🆓 **Ücretsiz ve açık kaynak** — her zaman, herkes için

---

## 🖼️ Ekran görüntüleri

> _Uygulama ekran görüntüleri buraya eklenecek._

<div align="center">
<img src="docs/press/screenshot_placeholder.png" alt="Wergêr ekran görüntüsü" width="280" />
</div>

---

## 💻 Platformlar

| Platform | Gereksinimler | Teknoloji |
|----------|---------------|-----------|
| 🤖 **Android** | API 26+ (Android 8.0+) | Jetpack Compose + Kotlin |
| 🪟 **Windows** | Windows 10/11 | Tauri 2 (Rust + WebView) |

---

## 🔌 Çevrimdışı & Çevrimiçi

İki çeviri motoru vardır:

### Çevrimdışı motor — NLLB-200
- Model: **Meta NLLB-200 distilled 600M** (`facebook/nllb-200-distilled-600M`)
- Format: **int8 ONNX** (quantize edilmiş, Xenova mirror'ı)
- Çalışma zamanı: cihaz üzerinde **ONNX Runtime** (CPU)
- Tokenizer: **NLLB BPE tokenizer** tamamen saf Kotlin/Rust ile yeniden yazıldı — yerel kütüphane yok, %100 çevrimdışı
- Model (~**870 MB**) bir kez **Wergêr** adlı bir klasöre (veya seçtiğiniz klasöre, otomatik algılanır) indirilir. Sonrasında uygulama **internetsiz** çalışır.

### Çevrimiçi motor — LibreTranslate
- Yapılandırılabilir [LibreTranslate](https://libretranslate.com/) adresi
- Modeli indirmek istemeyenler için

---

## 🗣️ Desteklenen diller

| Dil | Kod (FLORES-200) | Durum |
|-----|------------------|-------|
| **Kurmancî** (Kürtçe Latin) | `kmr_Latn` | ⭐ Birincil |
| **Soranî** (Kürtçe Arap) | `ckb_Arab` | ✅ |
| Türkçe | `tur_Latn` | ✅ |
| English | `eng_Latn` | ✅ |
| Arapça | `arb_Arab` | ✅ |
| Farsça | `pes_Arab` | ✅ |
| ... ve NLLB-200 ile çok daha fazlası | 200+ | ✅ |

---

## 📥 Kurulum

### Android
1. En son APK'yı [**Releases**](../../releases) sayfasından indirin.
2. `.apk` dosyasını açıp kurun ("bilinmeyen kaynaklardan kuruluma" izin vermeniz gerekebilir).
3. Çevrimdışı motoru ilk kullandığınızda model (~870 MB) indirilir.

### Windows
1. `.msi` veya `.exe` yükleyiciyi [**Releases**](../../releases) sayfasından indirin.
2. Yükleyiciyi çalıştırın ve Wergêr'i açın.

---

## 🛠️ Kaynaktan derleme

### Android (Gradle)
```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (imzasız)
./gradlew :app:assembleRelease
```
Gereksinimler: **JDK 17**, Android SDK (API 34), build-tools 34/35.
Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

### Windows (Tauri)
```bash
cd desktop
npm install
npx tauri build
```
Gereksinimler: **Rust** (1.77+), **Node.js** ve Tauri 2 önkoşulları.

> 💡 Masaüstündeki çevrimdışı ONNX motoru isteğe bağlıdır:
> `npx tauri build -- --features offline`

---

## ⚖️ Model ve lisans notu

İki farklı lisans geçerlidir — lütfen dikkat edin:

- **Uygulama kodu (Wergêr):** [**AGPL-3.0**](LICENSE) — açık kaynak, ücretsiz.
- **NLLB-200 modeli:** [**CC-BY-NC 4.0**](https://creativecommons.org/licenses/by-nc/4.0/) — **yalnızca ticari olmayan kullanım**.

Yani: Wergêr kodu serbestçe kullanılabilir ve dağıtılabilir, ancak **NLLB-200 modeli ticari amaçlarla yeniden dağıtılamaz**. Ayrıntılar için [`NOTICE.md`](NOTICE.md) dosyasına bakın.

---

## 💚 Açık kaynak & ücretsiz felsefesi

Wergêr **ücretsizdir ve her zaman ücretsiz kalacaktır**. Şunlara inanıyoruz:

- 🔓 **Açık kaynak** — herkes kodu görebilir, değiştirebilir ve geliştirebilir.
- 🌐 **Hiçbir dil geride kalmasın** — Kürtçe ve diğer az desteklenen dilleri geliştirmeye çalışıyoruz.
- 🛡️ **Gizlilik** — çevrimdışı çeviri, metninizin cihazınızdan hiç çıkmadığı anlamına gelir.
- ❤️ **Kürtçeye bağlılık** — bu, Kürt dilini büyütmek ve korumak için bir projedir.

---

## 🏢 Cybergah Group hakkında

**Wergêr**, herkes için erişilebilir açık teknolojiye adanmış bir grup olan [**Cybergah Group**](https://cybergah.com) tarafından geliştirilmektedir.

🌐 Web sitesi: [cybergah.com](https://cybergah.com)

---

## 🤝 Katkıda bulunma

Katkılarınızı bekliyoruz! Lütfen önce [`CONTRIBUTING.md`](CONTRIBUTING.md) ve [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) dosyalarını okuyun.

- 🐛 Hata mı buldunuz? [Issue açın](../../issues).
- 💡 Bir fikriniz mi var? [Özellik isteği](../../issues/new/choose).
- 🌍 Dil mi eklemek istiyorsunuz? [`CONTRIBUTING.md`](CONTRIBUTING.md) dosyasına bakın.

---

## 📜 Lisans

Bu projenin kodu [**GNU AGPL-3.0**](LICENSE) altında lisanslanmıştır.
© [Cybergah Group](https://cybergah.com). NLLB-200 modeli CC-BY-NC-4.0 altındadır (bkz. [`NOTICE.md`](NOTICE.md)).

<div align="center">

**Kürt dili için ❤️ ile yapıldı — [Cybergah Group](https://cybergah.com)**

</div>
