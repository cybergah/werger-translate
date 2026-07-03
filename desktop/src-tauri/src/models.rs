//! Offline-model management: detect & download the three NLLB-200 files.
//!
//! Files (HuggingFace `Xenova/nllb-200-distilled-600M`):
//!   onnx/encoder_model_quantized.onnx          -> encoder.onnx
//!   onnx/decoder_model_merged_quantized.onnx   -> decoder.onnx
//!   tokenizer.json                             -> tokenizer.json
//!
//! Default storage folder: %USERPROFILE%\Documents\Wergêr

use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::Serialize;
use tauri::{Emitter, Window};

const BASE_URL: &str = "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main";

/// The three files to download, by quality. The local filenames are identical so
/// the engine doesn't care which variant was fetched.
///   - quantized (int8): ~小, fast, lower quality (default)
///   - high quality (fp32): ~4x larger, noticeably better translations
fn files(high_quality: bool) -> [(&'static str, &'static str); 3] {
    if high_quality {
        [
            ("onnx/encoder_model.onnx", "encoder.onnx"),
            ("onnx/decoder_model_merged.onnx", "decoder.onnx"),
            ("tokenizer.json", "tokenizer.json"),
        ]
    } else {
        [
            ("onnx/encoder_model_quantized.onnx", "encoder.onnx"),
            ("onnx/decoder_model_merged_quantized.onnx", "decoder.onnx"),
            ("tokenizer.json", "tokenizer.json"),
        ]
    }
}

/// Default models folder: %USERPROFILE%\Documents\Wergêr
pub fn default_models_dir() -> PathBuf {
    let home = std::env::var("USERPROFILE")
        .or_else(|_| std::env::var("HOME"))
        .unwrap_or_else(|_| ".".to_string());
    Path::new(&home).join("Documents").join("Wergêr")
}

#[derive(Serialize, Clone)]
pub struct ModelStatus {
    pub folder: String,
    pub encoder: bool,
    pub decoder: bool,
    pub tokenizer: bool,
    pub complete: bool,
}

/// Detect which model files already exist in `folder` (or the default folder).
pub fn detect(folder: Option<&str>) -> ModelStatus {
    let dir = folder
        .filter(|f| !f.trim().is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(default_models_dir);

    let exists = |name: &str| {
        let p = dir.join(name);
        p.is_file() && fs::metadata(&p).map(|m| m.len() > 0).unwrap_or(false)
    };

    // Two valid layouts: merged (600M) or split (1.3B, with -with-past decoder).
    let merged = exists("encoder.onnx") && exists("decoder.onnx");
    let split = exists("encoder_model.onnx")
        && exists("decoder_model.onnx")
        && exists("decoder_with_past_model.onnx");
    let tokenizer = exists("tokenizer.json");

    ModelStatus {
        folder: dir.to_string_lossy().to_string(),
        encoder: exists("encoder.onnx") || exists("encoder_model.onnx"),
        decoder: exists("decoder.onnx") || exists("decoder_with_past_model.onnx"),
        tokenizer,
        complete: (merged || split) && tokenizer,
    }
}

#[derive(Serialize, Clone)]
struct ProgressEvent {
    file: String,
    index: usize,
    total: usize,
    downloaded: u64,
    size: u64,
    percent: f64,
}

/// Download all three model files into `folder`, emitting `model-download-progress`
/// events on `window`. Skips files that already exist with non-zero size.
pub fn download(
    window: &Window,
    folder: Option<&str>,
    high_quality: bool,
) -> Result<ModelStatus, String> {
    let dir = folder
        .filter(|f| !f.trim().is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(default_models_dir);

    fs::create_dir_all(&dir).map_err(|e| format!("Cannot create folder {dir:?}: {e}"))?;

    // If the requested quality differs from what's installed, drop the existing
    // model files so they get re-fetched in the new variant.
    let marker = dir.join(".variant");
    let want = if high_quality { "hq" } else { "q" };
    if fs::read_to_string(&marker).ok().as_deref() != Some(want) {
        let _ = fs::remove_file(dir.join("encoder.onnx"));
        let _ = fs::remove_file(dir.join("decoder.onnx"));
    }

    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(60 * 60))
        .user_agent("Werger/1.0 (+https://cybergah.com)")
        .build()
        .map_err(|e| format!("HTTP client error: {e}"))?;

    let files = files(high_quality);
    let total = files.len();
    for (index, (remote, local)) in files.iter().enumerate() {
        let dest = dir.join(local);
        if dest.is_file() && fs::metadata(&dest).map(|m| m.len() > 0).unwrap_or(false) {
            // Already present; report as complete and continue.
            let _ = window.emit(
                "model-download-progress",
                ProgressEvent {
                    file: (*local).to_string(),
                    index,
                    total,
                    downloaded: 1,
                    size: 1,
                    percent: 100.0,
                },
            );
            continue;
        }

        let url = format!("{BASE_URL}/{remote}");
        let mut resp = client
            .get(&url)
            .send()
            .map_err(|e| format!("Download {local} failed: {e}"))?;

        if !resp.status().is_success() {
            return Err(format!("Download {local} HTTP {}", resp.status()));
        }

        let size = resp.content_length().unwrap_or(0);
        // Write to a temp file, then rename to avoid leaving partial files.
        let tmp = dir.join(format!("{local}.part"));
        let mut out = fs::File::create(&tmp).map_err(|e| format!("Create {tmp:?}: {e}"))?;

        let mut buf = [0u8; 64 * 1024];
        let mut downloaded: u64 = 0;
        let mut last_emit = std::time::Instant::now();
        loop {
            let n = resp
                .read(&mut buf)
                .map_err(|e| format!("Read {local} stream: {e}"))?;
            if n == 0 {
                break;
            }
            out.write_all(&buf[..n])
                .map_err(|e| format!("Write {local}: {e}"))?;
            downloaded += n as u64;

            // Throttle UI events to ~10/sec.
            if last_emit.elapsed() >= Duration::from_millis(100) {
                let percent = if size > 0 {
                    (downloaded as f64 / size as f64) * 100.0
                } else {
                    0.0
                };
                let _ = window.emit(
                    "model-download-progress",
                    ProgressEvent {
                        file: (*local).to_string(),
                        index,
                        total,
                        downloaded,
                        size,
                        percent,
                    },
                );
                last_emit = std::time::Instant::now();
            }
        }
        out.flush().ok();
        drop(out);
        fs::rename(&tmp, &dest).map_err(|e| format!("Finalize {local}: {e}"))?;

        let _ = window.emit(
            "model-download-progress",
            ProgressEvent {
                file: (*local).to_string(),
                index,
                total,
                downloaded,
                size: size.max(downloaded),
                percent: 100.0,
            },
        );
    }

    let _ = fs::write(&marker, want);
    Ok(detect(Some(&dir.to_string_lossy())))
}
