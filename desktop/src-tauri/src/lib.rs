//! Wergêr desktop backend (Tauri v2).
//!
//! Exposes translation, connectivity, model-management and (optionally) the
//! offline ONNX engine to the vanilla JS frontend via Tauri commands.

mod docs;
mod lang;
mod models;
mod ocr;
mod online;
mod quicksel;

#[cfg(feature = "offline")]
mod offline;
#[cfg(feature = "offline")]
mod tokenizer;
#[cfg(feature = "offline")]
mod tts;

use tauri::{Emitter, Manager, Window};
use tauri_plugin_clipboard_manager::ClipboardExt;

#[cfg(feature = "offline")]
use std::path::Path;

/// Grab the current selection (via synthetic Ctrl+C) and send it to the UI for
/// translation. Bound to a global shortcut so it works while any app is focused.
fn quick_translate<R: tauri::Runtime>(app: &tauri::AppHandle<R>) {
    quicksel::send_ctrl_c();
    std::thread::sleep(std::time::Duration::from_millis(140));
    let text = app.clipboard().read_text().unwrap_or_default();
    if text.trim().is_empty() {
        return;
    }
    if let Some(win) = app.get_webview_window("main") {
        let _ = win.unminimize();
        let _ = win.show();
        let _ = win.set_focus();
        let _ = win.emit("quick-translate", text);
    }
}
#[cfg(feature = "offline")]
use std::sync::Mutex;

// ---------------------------------------------------------------------------
// Shared app state
// ---------------------------------------------------------------------------

#[cfg(feature = "offline")]
#[derive(Default)]
struct OfflineState {
    /// Loaded engine + the folder it was loaded from (to avoid reloading).
    engine: Mutex<Option<(String, std::sync::Arc<offline::OfflineEngine>)>>,
}

// ---------------------------------------------------------------------------
// Online translation
// ---------------------------------------------------------------------------

/// Translate via LibreTranslate (online). Runs the blocking HTTP call on a
/// background thread so the UI stays responsive.
#[tauri::command]
async fn translate_online(
    text: String,
    source: String,
    target: String,
    endpoint: Option<String>,
    api_key: Option<String>,
) -> Result<String, String> {
    // No endpoint configured → key-free providers (Google → MyMemory).
    // An endpoint means the user wants their own LibreTranslate server.
    tauri::async_runtime::spawn_blocking(move || {
        online::translate(
            &text,
            &source,
            &target,
            endpoint.as_deref(),
            api_key.as_deref(),
        )
    })
    .await
    .map_err(|e| format!("Task join error: {e}"))?
}

/// Online text-to-speech (Google, key-free). Returns a `data:audio/mpeg` URL the
/// webview can play. Works in any build (no offline engine required).
#[tauri::command]
async fn online_tts(text: String, flores: String) -> Result<String, String> {
    use base64::Engine;
    let mp3 = tauri::async_runtime::spawn_blocking(move || online::tts(&text, &flores))
        .await
        .map_err(|e| format!("Task join error: {e}"))??;
    let b64 = base64::engine::general_purpose::STANDARD.encode(&mp3);
    Ok(format!("data:audio/mpeg;base64,{b64}"))
}

/// Probe connectivity (used by Auto mode).
#[tauri::command]
async fn check_online() -> Result<bool, String> {
    tauri::async_runtime::spawn_blocking(online::is_online)
        .await
        .map_err(|e| format!("Task join error: {e}"))
}

// ---------------------------------------------------------------------------
// Model management
// ---------------------------------------------------------------------------

#[tauri::command]
fn default_models_folder() -> String {
    models::default_models_dir().to_string_lossy().to_string()
}

#[tauri::command]
fn detect_models(folder: Option<String>) -> models::ModelStatus {
    models::detect(folder.as_deref())
}

/// Download the 3 model files into `folder`, emitting progress events.
#[tauri::command]
async fn download_models(
    window: Window,
    folder: Option<String>,
    high_quality: Option<bool>,
) -> Result<models::ModelStatus, String> {
    let hq = high_quality.unwrap_or(false);
    tauri::async_runtime::spawn_blocking(move || models::download(&window, folder.as_deref(), hq))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

// ---------------------------------------------------------------------------
// Offline translation
// ---------------------------------------------------------------------------

/// Whether this build was compiled with the `offline` feature.
#[tauri::command]
fn offline_supported() -> bool {
    cfg!(feature = "offline")
}

#[cfg(feature = "offline")]
#[tauri::command]
async fn translate_offline(
    state: tauri::State<'_, OfflineState>,
    text: String,
    source: String,
    target: String,
    folder: Option<String>,
) -> Result<String, String> {
    let dir = folder
        .filter(|f| !f.trim().is_empty())
        .unwrap_or_else(|| models::default_models_dir().to_string_lossy().to_string());

    // Load (or reuse) the engine for this folder.
    let engine = {
        let mut guard = state.engine.lock().unwrap();
        let reload = match guard.as_ref() {
            Some((loaded_dir, _)) => loaded_dir != &dir,
            None => true,
        };
        if reload {
            let eng = offline::OfflineEngine::from_dir(Path::new(&dir))?;
            *guard = Some((dir.clone(), std::sync::Arc::new(eng)));
        }
        guard.as_ref().unwrap().1.clone()
    };

    tauri::async_runtime::spawn_blocking(move || engine.translate(&text, &source, &target))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

/// Stub used when the `offline` feature is disabled, so the JS side gets a
/// clear message instead of an "unknown command" error.
#[cfg(not(feature = "offline"))]
#[tauri::command]
async fn translate_offline(
    _text: String,
    _source: String,
    _target: String,
    _folder: Option<String>,
) -> Result<String, String> {
    Err("This build was compiled without the offline engine. Rebuild with `--features offline` to enable on-device translation.".to_string())
}

/// Batched offline translation (many strings in one call). Used by the photo
/// overlay so all recognized lines are translated together — far faster than
/// one call per line.
#[cfg(feature = "offline")]
#[tauri::command]
async fn translate_offline_batch(
    state: tauri::State<'_, OfflineState>,
    texts: Vec<String>,
    source: String,
    target: String,
    folder: Option<String>,
) -> Result<Vec<String>, String> {
    let dir = folder
        .filter(|f| !f.trim().is_empty())
        .unwrap_or_else(|| models::default_models_dir().to_string_lossy().to_string());

    let engine = {
        let mut guard = state.engine.lock().unwrap();
        let reload = match guard.as_ref() {
            Some((loaded_dir, _)) => loaded_dir != &dir,
            None => true,
        };
        if reload {
            let eng = offline::OfflineEngine::from_dir(Path::new(&dir))?;
            *guard = Some((dir.clone(), std::sync::Arc::new(eng)));
        }
        guard.as_ref().unwrap().1.clone()
    };

    tauri::async_runtime::spawn_blocking(move || engine.translate_batch(&texts, &source, &target))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

#[cfg(not(feature = "offline"))]
#[tauri::command]
async fn translate_offline_batch(
    _texts: Vec<String>,
    _source: String,
    _target: String,
    _folder: Option<String>,
) -> Result<Vec<String>, String> {
    Err("This build was compiled without the offline engine. Rebuild with `--features offline` to enable on-device translation.".to_string())
}

// ---------------------------------------------------------------------------
// Photo OCR (Windows.Media.Ocr) and Document extraction (.txt / .pdf)
// ---------------------------------------------------------------------------

/// Recognize text in the image at `path`, hinting the OCR engine with `flores`.
#[tauri::command]
async fn ocr_image(path: String, flores: String) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || ocr::recognize(&path, &flores))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

/// Recognize text with per-line bounding boxes (for the in-place photo overlay).
#[tauri::command]
async fn ocr_image_layout(path: String, flores: String) -> Result<ocr::OcrLayout, String> {
    tauri::async_runtime::spawn_blocking(move || ocr::recognize_layout(&path, &flores))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

/// OCR from in-memory image bytes (base64) — used by the PDF overlay, which
/// renders each page to a PNG in the webview.
#[tauri::command]
async fn ocr_image_layout_bytes(
    data_base64: String,
    flores: String,
) -> Result<ocr::OcrLayout, String> {
    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(data_base64.as_bytes())
        .map_err(|e| e.to_string())?;
    tauri::async_runtime::spawn_blocking(move || ocr::recognize_layout_bytes(&bytes, &flores))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

/// Read a file as base64, rejecting anything larger than `max_bytes`.
/// Used to hand a PDF to the webview's pdf.js renderer under the 2 MB cap.
#[tauri::command]
async fn read_file_capped(path: String, max_bytes: u64) -> Result<String, String> {
    use base64::Engine;
    let meta = std::fs::metadata(&path).map_err(|e| e.to_string())?;
    if meta.len() > max_bytes {
        return Err(format!(
            "Belge {} MB sınırını aşıyor — kabul edilmiyor.",
            max_bytes / (1024 * 1024)
        ));
    }
    let bytes = std::fs::read(&path).map_err(|e| e.to_string())?;
    Ok(base64::engine::general_purpose::STANDARD.encode(&bytes))
}

/// Write a base64-encoded PNG (from the overlay canvas) to `path`.
#[tauri::command]
async fn save_png(path: String, data_base64: String) -> Result<(), String> {
    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(data_base64.as_bytes())
        .map_err(|e| e.to_string())?;
    std::fs::write(&path, bytes).map_err(|e| e.to_string())
}

/// Documents larger than 2 MB are rejected across the app.
const DOC_MAX_BYTES: u64 = 2 * 1024 * 1024;

fn reject_large_document(path: &str) -> Result<(), String> {
    let len = std::fs::metadata(path).map_err(|e| e.to_string())?.len();
    if len > DOC_MAX_BYTES {
        return Err("Belge 2 MB'den büyük — kabul edilmiyor.".to_string());
    }
    Ok(())
}

/// Extract plain text from a .txt or .pdf document at `path`.
#[tauri::command]
async fn extract_document(path: String) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || docs::extract(&path))
        .await
        .map_err(|e| format!("Task join error: {e}"))?
}

/// Translate a document and write a same-format translated copy next to it,
/// preserving formatting (.txt line by line, .docx text nodes in place).
/// Returns the output file path. `engine` is "online" or "offline".
#[cfg(feature = "offline")]
#[tauri::command]
async fn translate_document(
    state: tauri::State<'_, OfflineState>,
    path: String,
    source: String,
    target: String,
    engine: String,
    folder: Option<String>,
    endpoint: Option<String>,
    api_key: Option<String>,
) -> Result<String, String> {
    reject_large_document(&path)?;
    // Acquire the offline engine up front (it isn't Send across the await).
    let eng = if engine == "offline" {
        let dir = folder
            .clone()
            .filter(|f| !f.trim().is_empty())
            .unwrap_or_else(|| models::default_models_dir().to_string_lossy().to_string());
        let mut guard = state.engine.lock().unwrap();
        let reload = match guard.as_ref() {
            Some((d, _)) => d != &dir,
            None => true,
        };
        if reload {
            let e = offline::OfflineEngine::from_dir(Path::new(&dir))?;
            *guard = Some((dir.clone(), std::sync::Arc::new(e)));
        }
        Some(guard.as_ref().unwrap().1.clone())
    } else {
        None
    };

    tauri::async_runtime::spawn_blocking(move || {
        let translate = |s: &str| -> Result<String, String> {
            match &eng {
                Some(e) => e.translate(s, &source, &target),
                None => online::translate(s, &source, &target, endpoint.as_deref(), api_key.as_deref()),
            }
        };
        docs::translate_file(&path, translate)
    })
    .await
    .map_err(|e| format!("Task join error: {e}"))?
}

#[cfg(not(feature = "offline"))]
#[tauri::command]
async fn translate_document(
    path: String,
    source: String,
    target: String,
    engine: String,
    _folder: Option<String>,
    endpoint: Option<String>,
    api_key: Option<String>,
) -> Result<String, String> {
    reject_large_document(&path)?;
    if engine == "offline" {
        return Err("This build has no offline engine. Use online mode or rebuild with `--features offline`.".to_string());
    }
    tauri::async_runtime::spawn_blocking(move || {
        let translate =
            |s: &str| online::translate(s, &source, &target, endpoint.as_deref(), api_key.as_deref());
        docs::translate_file(&path, translate)
    })
    .await
    .map_err(|e| format!("Task join error: {e}"))?
}

// ---------------------------------------------------------------------------
// Offline text-to-speech (Meta MMS-TTS / VITS)
// ---------------------------------------------------------------------------

/// Whether an offline MMS-TTS voice is installed for `flores` in `folder`.
/// The frontend uses this to decide between OS speech and offline speech.
#[cfg(feature = "offline")]
#[tauri::command]
fn mms_voice_available(flores: String, folder: Option<String>) -> bool {
    let dir = folder
        .filter(|f| !f.trim().is_empty())
        .unwrap_or_else(|| models::default_models_dir().to_string_lossy().to_string());
    tts::voice_available(Path::new(&dir), &flores)
}

#[cfg(not(feature = "offline"))]
#[tauri::command]
fn mms_voice_available(_flores: String, _folder: Option<String>) -> bool {
    false
}

/// Synthesize `text` in `flores` offline and return it as a `data:audio/wav`
/// URL the webview can play directly.
#[cfg(feature = "offline")]
#[tauri::command]
async fn mms_speak(
    state: tauri::State<'_, tts::TtsState>,
    text: String,
    flores: String,
    folder: Option<String>,
) -> Result<String, String> {
    use base64::Engine;

    let dir = folder
        .filter(|f| !f.trim().is_empty())
        .unwrap_or_else(|| models::default_models_dir().to_string_lossy().to_string());

    // `state` is not Send across the await, so do the work synchronously here;
    // the synthesis itself is CPU-bound but short for typical UI text.
    let wav = tts::synthesize(state.inner(), Path::new(&dir), &flores, &text)?;
    let b64 = base64::engine::general_purpose::STANDARD.encode(&wav);
    Ok(format!("data:audio/wav;base64,{b64}"))
}

#[cfg(not(feature = "offline"))]
#[tauri::command]
async fn mms_speak(
    _text: String,
    _flores: String,
    _folder: Option<String>,
) -> Result<String, String> {
    Err("This build has no offline speech engine. Rebuild with `--features offline`.".to_string())
}

// ---------------------------------------------------------------------------
// App entry
// ---------------------------------------------------------------------------

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    use tauri_plugin_global_shortcut::{Code, Modifiers, Shortcut, ShortcutState};

    // Ctrl+Shift+T → translate the current selection from any app.
    let quick = Shortcut::new(Some(Modifiers::CONTROL | Modifiers::SHIFT), Code::KeyT);
    let quick_handler = quick;

    let builder = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(
            tauri_plugin_global_shortcut::Builder::new()
                .with_handler(move |app, sc, event| {
                    if event.state() == ShortcutState::Pressed && sc == &quick_handler {
                        quick_translate(app);
                    }
                })
                .build(),
        )
        .setup(move |app| {
            use tauri_plugin_global_shortcut::GlobalShortcutExt;
            // Best-effort: ignore if another app already owns the shortcut.
            let _ = app.global_shortcut().register(quick);
            Ok(())
        });

    #[cfg(feature = "offline")]
    let builder = builder
        .manage(OfflineState::default())
        .manage(tts::TtsState::new());

    builder
        .invoke_handler(tauri::generate_handler![
            translate_online,
            online_tts,
            check_online,
            default_models_folder,
            detect_models,
            download_models,
            offline_supported,
            translate_offline,
            translate_offline_batch,
            mms_voice_available,
            mms_speak,
            ocr_image,
            ocr_image_layout,
            ocr_image_layout_bytes,
            read_file_capped,
            save_png,
            extract_document,
            translate_document,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Wergêr");
}
