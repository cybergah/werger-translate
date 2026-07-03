# Beşdarî / Contributing — Wergêr

[Kurmancî](#kurmancî) · [English](#english)

---

## Kurmancî

Spas ku tu dixwazî beşdarî **Wergêr** bibî! Ev proje belaş û çavkaniya wê
vekirî ye, ji aliyê [Cybergah Group](https://cybergah.com) ve. Her beşdarî —
piçûk an mezin — bi xêr e.

Berî her tiştî, ji kerema xwe [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) bixwîne.

### Çawa beşdar bibî
1. Repoyê **fork** bike û şaxek nû çêke: `git checkout -b taybetmendiya-min`
2. Guhertinên xwe bike û **commit** bike (peyamên zelal binivîse).
3. **Pull Request** veke û rave bike ka te çi guhert û çima.
4. Eger çewtiyek e, pêşî [issue](../../issues) veke ku em gotûbêj bikin.

### Cî: kodê Android li hember desktop
- **Android (Kotlin):** `app/src/main/java/dev/wergir/translate/`
  - `MainActivity.kt`, `WergirApp.kt`, `TranslateViewModel.kt`
  - `nllb/` — `Languages.kt`, `NllbTokenizer.kt`, `ModelManager.kt`, `Translator.kt`
  - `ui/` — ekranên Jetpack Compose û tema
- **Windows (Tauri / Rust + web):** `desktop/`
  - `desktop/src/` — UI (web)
  - `desktop/src-tauri/` — koda Rust, `Cargo.toml`, `tauri.conf.json`
- **Belge:** `docs/`  ·  **Brand/logo:** `brand/`
  - Têbînî: peldankên `app/`, `desktop/`, `docs/`, û `brand/` xwedîtiya wan
    ji aliyê pêvajoyên din ve tê kirin — guhertinan li wir bi baldarî bike.

### Stîla kodê
- **Kotlin:** rêbazên fermî yên Kotlin; nav bi `camelCase`, çar valahî (space).
- **Rust:** `cargo fmt` û `cargo clippy` berî PRyê bimeşîne.
- Navê brand **"Wergêr"** di her cî de neguherî bimîne.

### Çawa zimanekî nû zêde bikî
1. Koda FLORES-200 ya zimanê nû bibîne (mînak: `kmr_Latn`).
2. Li `app/src/main/java/dev/wergir/translate/nllb/Languages.kt` zimên û
   token-id zêde bike.
3. Ji bo desktopê, navnîşa zimanan a têkildar nû bike.
4. Eger zimanê nivîsê ji rastê ber bi çepê ye (RTL), piştrast be ku UI piştgirî dide.
5. Test bike û PR veke.

---

## English

Thank you for considering a contribution to **Wergêr**! This is a free,
open-source project by [Cybergah Group](https://cybergah.com). Every
contribution — big or small — is welcome.

Before anything else, please read [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

### How to contribute
1. **Fork** the repo and create a branch: `git checkout -b my-feature`
2. Make your changes and **commit** with clear messages.
3. Open a **Pull Request** describing what you changed and why.
4. For bugs, open an [issue](../../issues) first so we can discuss.

### Where the code lives: Android vs desktop
- **Android (Kotlin):** `app/src/main/java/dev/wergir/translate/`
  - `MainActivity.kt`, `WergirApp.kt`, `TranslateViewModel.kt`
  - `nllb/` — `Languages.kt`, `NllbTokenizer.kt`, `ModelManager.kt`, `Translator.kt`
  - `ui/` — Jetpack Compose screens and theme
- **Windows (Tauri / Rust + web):** `desktop/`
  - `desktop/src/` — front-end UI (web)
  - `desktop/src-tauri/` — Rust code, `Cargo.toml`, `tauri.conf.json`
- **Docs:** `docs/`  ·  **Brand/logo:** `brand/`
  - Note: the `app/`, `desktop/`, `docs/` and `brand/` folders may be owned by
    other processes — change them carefully.

### Code style
- **Kotlin:** official Kotlin conventions; `camelCase` names, 4-space indent.
- **Rust:** run `cargo fmt` and `cargo clippy` before opening a PR.
- Keep the brand name **"Wergêr"** unchanged everywhere.

### Building locally
```bash
# Android
./gradlew :app:assembleDebug

# Desktop (Windows)
cd desktop && npm install && npx tauri build
# Offline ONNX engine (opt-in): npx tauri build -- --features offline
```

### How to add a language
1. Find the FLORES-200 code for the new language (e.g. `kmr_Latn`).
2. Add the language and its token id in
   `app/src/main/java/dev/wergir/translate/nllb/Languages.kt`.
3. Update the corresponding language list for the desktop app.
4. If the script is right-to-left (RTL), make sure the UI supports it.
5. Test and open a PR.

---

Made with ❤️ by [Cybergah Group](https://cybergah.com) — *no language left behind.*
