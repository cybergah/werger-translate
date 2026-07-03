//! Offline neural text-to-speech using Meta's **MMS-TTS** (VITS) models on
//! ONNX Runtime (`ort`). This is what gives the desktop app real Kurdish speech
//! (Kurmancî), which Windows' built-in voices never provide.
//!
//! Meta does not publish ONNX for these models — only PyTorch. A one-off export
//! step (`tools/export_mms_tts.py`) converts `facebook/mms-tts-<lang>` into the
//! layout this module expects:
//!
//!   <models>/tts/<flores>/model.onnx
//!   <models>/tts/<flores>/vocab.json              (char -> id)
//!   <models>/tts/<flores>/tokenizer_config.json   (add_blank, normalize, …)
//!   <models>/tts/<flores>/config.json             (sampling_rate)
//!
//! Pipeline (faithful to transformers `VitsTokenizer` + `VitsModel`):
//!   normalize (lowercase + drop out-of-vocab chars)
//!     -> char ids, optionally interspersed with the pad id (add_blank)
//!     -> ONNX VITS  ->  f32 waveform  ->  16-bit PCM WAV bytes
//!
//! Only compiled when the `offline` feature is enabled (it needs `ort`).
#![cfg(feature = "offline")]

use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use ndarray::Array;
use ort::session::{builder::GraphOptimizationLevel, Session};
use ort::value::Tensor;

/// Which MMS-TTS model (HuggingFace repo) backs each FLORES-200 language.
/// Only languages with a real Meta model are listed; the rest fall back to the
/// OS speech engine on the frontend.
///
/// NOTE: Meta ships **no Sorani (ckb) TTS model** — only Kurmancî. The Arabic
/// entry below is Kurmancî written in Arabic script, not Sorani.
pub fn mms_repo_for(flores: &str) -> Option<&'static str> {
    Some(match flores {
        "kmr_Latn" => "facebook/mms-tts-kmr-script_latin",
        "tur_Latn" => "facebook/mms-tts-tur",
        "eng_Latn" => "facebook/mms-tts-eng",
        "arb_Arab" => "facebook/mms-tts-ara",
        "pes_Arab" => "facebook/mms-tts-fas",
        "rus_Cyrl" => "facebook/mms-tts-rus",
        "deu_Latn" => "facebook/mms-tts-deu",
        "fra_Latn" => "facebook/mms-tts-fra",
        "spa_Latn" => "facebook/mms-tts-spa",
        _ => return None,
    })
}

/// A loaded MMS-TTS voice. `Session::run` needs `&mut self`, so the session is
/// behind a Mutex (same pattern as the offline NLLB engine).
struct Voice {
    session: Mutex<Session>,
    /// char -> token id
    vocab: HashMap<String, i64>,
    add_blank: bool,
    normalize: bool,
    pad_id: i64,
    sampling_rate: u32,
}

#[derive(Default)]
pub struct TtsState {
    /// Cache of loaded voices keyed by their folder path.
    voices: Mutex<HashMap<String, std::sync::Arc<Voice>>>,
}

impl TtsState {
    pub fn new() -> Self {
        Self::default()
    }
}

/// The per-language voice folder under the models directory.
fn voice_dir(models_folder: &Path, flores: &str) -> PathBuf {
    models_folder.join("tts").join(flores)
}

/// True if the four files a voice needs are all present.
pub fn voice_available(models_folder: &Path, flores: &str) -> bool {
    if mms_repo_for(flores).is_none() {
        return false;
    }
    let dir = voice_dir(models_folder, flores);
    ["model.onnx", "vocab.json", "config.json"]
        .iter()
        .all(|f| dir.join(f).is_file())
}

fn load_voice(dir: &Path) -> Result<Voice, String> {
    let onnx = dir.join("model.onnx");
    if !onnx.is_file() {
        return Err(format!("MMS voice not found: {}", onnx.display()));
    }

    // ---- vocab.json: { "a": 20, ... } ----
    let vocab_raw = fs::read_to_string(dir.join("vocab.json"))
        .map_err(|e| format!("read vocab.json: {e}"))?;
    let vocab_json: serde_json::Value =
        serde_json::from_str(&vocab_raw).map_err(|e| format!("parse vocab.json: {e}"))?;
    let mut vocab = HashMap::new();
    if let Some(obj) = vocab_json.as_object() {
        for (k, v) in obj {
            if let Some(id) = v.as_i64() {
                vocab.insert(k.clone(), id);
            }
        }
    }
    if vocab.is_empty() {
        return Err("vocab.json is empty or malformed".into());
    }

    // ---- tokenizer_config.json: add_blank / normalize / pad_token ----
    let (add_blank, normalize, pad_token) =
        match fs::read_to_string(dir.join("tokenizer_config.json")) {
            Ok(raw) => {
                let j: serde_json::Value = serde_json::from_str(&raw)
                    .map_err(|e| format!("parse tokenizer_config.json: {e}"))?;
                (
                    j.get("add_blank").and_then(|v| v.as_bool()).unwrap_or(true),
                    j.get("normalize").and_then(|v| v.as_bool()).unwrap_or(true),
                    j.get("pad_token")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                )
            }
            // Sensible MMS defaults if the file is missing.
            Err(_) => (true, true, String::new()),
        };
    // The blank/pad id: look up the pad token char, else default to 0 (MMS uses 0).
    let pad_id = vocab.get(&pad_token).copied().unwrap_or(0);

    // ---- config.json: sampling_rate ----
    let sampling_rate = fs::read_to_string(dir.join("config.json"))
        .ok()
        .and_then(|raw| serde_json::from_str::<serde_json::Value>(&raw).ok())
        .and_then(|j| j.get("sampling_rate").and_then(|v| v.as_u64()))
        .unwrap_or(16000) as u32;

    let threads = std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(2)
        .clamp(1, 4);
    let session = Session::builder()
        .map_err(err)?
        .with_optimization_level(GraphOptimizationLevel::Level3)
        .map_err(err)?
        .with_intra_threads(threads)
        .map_err(err)?
        .commit_from_file(&onnx)
        .map_err(err)?;

    Ok(Voice {
        session: Mutex::new(session),
        vocab,
        add_blank,
        normalize,
        pad_id,
        sampling_rate,
    })
}

/// Turn text into the VITS input id sequence, faithful to `VitsTokenizer`:
/// lowercase + drop out-of-vocab chars, then (optionally) intersperse the pad id.
fn text_to_ids(voice: &Voice, text: &str) -> Vec<i64> {
    let prepared: String = if voice.normalize {
        text.to_lowercase()
    } else {
        text.to_string()
    };

    let mut ids: Vec<i64> = Vec::with_capacity(prepared.chars().count());
    for ch in prepared.chars() {
        let key = ch.to_string();
        if let Some(&id) = voice.vocab.get(&key) {
            ids.push(id);
        }
        // Out-of-vocab chars are dropped (matches normalize fallback filtering).
    }

    if voice.add_blank {
        // interspersed = [pad, id0, pad, id1, pad, ..., pad]  (len = 2*n + 1)
        let mut interspersed = Vec::with_capacity(ids.len() * 2 + 1);
        interspersed.push(voice.pad_id);
        for id in ids {
            interspersed.push(id);
            interspersed.push(voice.pad_id);
        }
        interspersed
    } else {
        ids
    }
}

/// Synthesize `text` in `flores` and return 16-bit PCM WAV bytes.
pub fn synthesize(
    state: &TtsState,
    models_folder: &Path,
    flores: &str,
    text: &str,
) -> Result<Vec<u8>, String> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return Err("No text to speak.".into());
    }
    if mms_repo_for(flores).is_none() {
        return Err(format!("No offline MMS-TTS voice exists for {flores}."));
    }

    let dir = voice_dir(models_folder, flores);
    let key = dir.to_string_lossy().to_string();

    // Load (or reuse) the voice.
    let voice = {
        let mut guard = state.voices.lock().unwrap();
        if !guard.contains_key(&key) {
            let v = load_voice(&dir)?;
            guard.insert(key.clone(), std::sync::Arc::new(v));
        }
        guard.get(&key).unwrap().clone()
    };

    let ids = text_to_ids(&voice, trimmed);
    if ids.is_empty() {
        return Err("Nothing speakable in this text for the selected language.".into());
    }
    let len = ids.len();

    let input_ids = Array::from_shape_vec((1, len), ids).map_err(|e| format!("ids shape: {e}"))?;
    let attention_mask = Array::from_elem((1, len), 1i64);

    let waveform: Vec<f32> = {
        let mut sess = voice.session.lock().unwrap();
        let outputs = sess
            .run(ort::inputs![
                "input_ids" => Tensor::from_array(input_ids).map_err(err)?,
                "attention_mask" => Tensor::from_array(attention_mask).map_err(err)?,
            ])
            .map_err(err)?;
        // Our export (tools/export_mms_tts.py) names the VITS output "waveform".
        let (_shape, data) = outputs["waveform"]
            .try_extract_tensor::<f32>()
            .map_err(err)?;
        data.to_vec()
    };

    Ok(pcm_f32_to_wav(&waveform, voice.sampling_rate))
}

/// Encode mono f32 samples in [-1, 1] as a little-endian 16-bit PCM WAV file.
fn pcm_f32_to_wav(samples: &[f32], sample_rate: u32) -> Vec<u8> {
    let num_samples = samples.len() as u32;
    let bytes_per_sample = 2u32;
    let block_align = bytes_per_sample; // mono
    let byte_rate = sample_rate * block_align;
    let data_len = num_samples * bytes_per_sample;
    let mut out = Vec::with_capacity(44 + data_len as usize);

    out.extend_from_slice(b"RIFF");
    out.extend_from_slice(&(36 + data_len).to_le_bytes());
    out.extend_from_slice(b"WAVE");
    out.extend_from_slice(b"fmt ");
    out.extend_from_slice(&16u32.to_le_bytes()); // fmt chunk size
    out.extend_from_slice(&1u16.to_le_bytes()); // PCM
    out.extend_from_slice(&1u16.to_le_bytes()); // mono
    out.extend_from_slice(&sample_rate.to_le_bytes());
    out.extend_from_slice(&byte_rate.to_le_bytes());
    out.extend_from_slice(&(block_align as u16).to_le_bytes());
    out.extend_from_slice(&16u16.to_le_bytes()); // bits per sample
    out.extend_from_slice(b"data");
    out.extend_from_slice(&data_len.to_le_bytes());

    for &s in samples {
        let clamped = s.clamp(-1.0, 1.0);
        let v = (clamped * 32767.0) as i16;
        out.extend_from_slice(&v.to_le_bytes());
    }
    out
}

fn err<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}
