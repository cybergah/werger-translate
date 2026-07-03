//! Online translation backends.
//!
//! Out of the box the app uses **key-free** providers so online translation
//! works with no setup:
//!   1. Google Translate's free `gtx` endpoint (best Kurdish coverage), then
//!   2. MyMemory as a fallback.
//! If the user configures a LibreTranslate endpoint (self-hosted or paid),
//! that is used instead. FLORES-200 codes are mapped per backend in `crate::lang`.

use serde::{Deserialize, Serialize};
use std::time::Duration;

use crate::lang::{flores_to_google, flores_to_libre};

#[derive(Serialize)]
struct LibreRequest<'a> {
    q: &'a str,
    source: &'a str,
    target: &'a str,
    format: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    api_key: Option<&'a str>,
}

#[derive(Deserialize)]
struct LibreResponse {
    #[serde(rename = "translatedText")]
    translated_text: String,
}

#[derive(Deserialize)]
struct LibreError {
    error: String,
}

fn http_client(timeout_secs: u64) -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(timeout_secs))
        .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Werger/1.0")
        .build()
        .map_err(|e| format!("HTTP client error: {e}"))
}

/// Translate `text` from `source` to `target` (FLORES-200 codes).
///
/// `endpoint` selects the backend: when it is `None`/empty we use the key-free
/// providers (Google → MyMemory); otherwise we POST to that LibreTranslate URL.
/// Runs on a blocking client; the Tauri command wrapper spawns it off the UI thread.
pub fn translate(
    text: &str,
    source: &str,
    target: &str,
    endpoint: Option<&str>,
    api_key: Option<&str>,
) -> Result<String, String> {
    let endpoint = endpoint.map(str::trim).filter(|e| !e.is_empty());

    match endpoint {
        // User configured a LibreTranslate server → use it.
        Some(url) => translate_libre(text, source, target, url, api_key),
        // Default: no key required.
        None => translate_google(text, source, target)
            .or_else(|google_err| {
                translate_mymemory(text, source, target)
                    .map_err(|mm_err| format!("{google_err} | MyMemory: {mm_err}"))
            }),
    }
}

/// Google Translate free endpoint (`translate_a/single`, client=gtx). No key.
fn translate_google(text: &str, source: &str, target: &str) -> Result<String, String> {
    let sl = flores_to_google(source);
    let tl = flores_to_google(target);

    let client = http_client(20)?;
    let resp = client
        .get("https://translate.googleapis.com/translate_a/single")
        .query(&[
            ("client", "gtx"),
            ("sl", sl),
            ("tl", tl),
            ("dt", "t"),
            ("q", text),
        ])
        .send()
        .map_err(|e| format!("Google request failed: {e}"))?;

    let status = resp.status();
    let raw = resp.text().map_err(|e| format!("Read body failed: {e}"))?;
    if !status.is_success() {
        return Err(format!("Google HTTP {status}"));
    }

    // Response shape: [[["translated","orig",...], ...], ...]
    let json: serde_json::Value =
        serde_json::from_str(&raw).map_err(|e| format!("Bad Google response: {e}"))?;
    let segments = json
        .get(0)
        .and_then(|v| v.as_array())
        .ok_or("Unexpected Google response")?;

    let mut out = String::new();
    for seg in segments {
        if let Some(s) = seg.get(0).and_then(|v| v.as_str()) {
            out.push_str(s);
        }
    }
    if out.is_empty() {
        return Err("Google returned no text".to_string());
    }
    Ok(out)
}

/// MyMemory free endpoint. No key (anonymous daily quota applies).
fn translate_mymemory(text: &str, source: &str, target: &str) -> Result<String, String> {
    #[derive(Deserialize)]
    struct MmData {
        #[serde(rename = "translatedText")]
        translated_text: String,
    }
    #[derive(Deserialize)]
    struct MmResponse {
        #[serde(rename = "responseData")]
        response_data: MmData,
        #[serde(rename = "responseStatus", default)]
        response_status: serde_json::Value,
    }

    let sl = flores_to_google(source);
    let tl = flores_to_google(target);
    let langpair = format!("{sl}|{tl}");

    let client = http_client(20)?;
    let resp = client
        .get("https://api.mymemory.translated.net/get")
        .query(&[("q", text), ("langpair", langpair.as_str())])
        .send()
        .map_err(|e| format!("MyMemory request failed: {e}"))?;

    let raw = resp.text().map_err(|e| format!("Read body failed: {e}"))?;
    let parsed: MmResponse =
        serde_json::from_str(&raw).map_err(|e| format!("Bad MyMemory response: {e}"))?;

    // responseStatus can be a number or string depending on the error.
    let ok = parsed
        .response_status
        .as_i64()
        .map(|c| c == 200)
        .unwrap_or(true);
    if !ok || parsed.response_data.translated_text.trim().is_empty() {
        return Err("MyMemory could not translate this pair".to_string());
    }
    Ok(parsed.response_data.translated_text)
}

/// LibreTranslate (self-hosted or paid). Uses the optional API key.
fn translate_libre(
    text: &str,
    source: &str,
    target: &str,
    endpoint: &str,
    api_key: Option<&str>,
) -> Result<String, String> {
    let src = flores_to_libre(source);
    let tgt = flores_to_libre(target);
    let key = api_key.filter(|k| !k.trim().is_empty());

    let body = LibreRequest {
        q: text,
        source: src,
        target: tgt,
        format: "text",
        api_key: key,
    };

    let client = http_client(30)?;
    let resp = client
        .post(endpoint)
        .json(&body)
        .send()
        .map_err(|e| format!("Request failed: {e}"))?;

    let status = resp.status();
    let raw = resp.text().map_err(|e| format!("Read body failed: {e}"))?;

    if !status.is_success() {
        if let Ok(err) = serde_json::from_str::<LibreError>(&raw) {
            return Err(format!("LibreTranslate ({status}): {}", err.error));
        }
        return Err(format!("LibreTranslate HTTP {status}: {raw}"));
    }

    let parsed: LibreResponse =
        serde_json::from_str(&raw).map_err(|e| format!("Bad response: {e} — {raw}"))?;
    Ok(parsed.translated_text)
}

/// Online text-to-speech via Google Translate's free `translate_tts` endpoint
/// (no API key). Returns concatenated MP3 bytes. This covers most of the ~200
/// languages when online — languages with an offline MMS voice (Kurdish) or an
/// OS voice are handled before this on the frontend.
///
/// The endpoint accepts ~200 chars per request, so long text is split on word
/// boundaries and the resulting MP3 fragments are concatenated (players handle
/// back-to-back MPEG frames fine).
pub fn tts(text: &str, flores: &str) -> Result<Vec<u8>, String> {
    let tl = flores_to_google(flores);
    let chunks = chunk_text(text, 180);
    if chunks.is_empty() {
        return Err("No text to speak.".into());
    }

    let client = http_client(20)?;
    let mut audio: Vec<u8> = Vec::new();
    for chunk in chunks {
        let resp = client
            .get("https://translate.google.com/translate_tts")
            .query(&[
                ("ie", "UTF-8"),
                ("client", "tw-ob"),
                ("tl", tl),
                ("q", chunk.as_str()),
            ])
            .send()
            .map_err(|e| format!("TTS request failed: {e}"))?;
        if !resp.status().is_success() {
            return Err(format!("TTS HTTP {}", resp.status()));
        }
        let bytes = resp.bytes().map_err(|e| format!("TTS read failed: {e}"))?;
        audio.extend_from_slice(&bytes);
    }
    if audio.is_empty() {
        return Err("TTS returned no audio".into());
    }
    Ok(audio)
}

/// Split text into chunks of at most `max` characters, breaking on whitespace.
fn chunk_text(text: &str, max: usize) -> Vec<String> {
    let text = text.trim();
    if text.is_empty() {
        return Vec::new();
    }
    let mut chunks = Vec::new();
    let mut cur = String::new();
    for word in text.split_whitespace() {
        // A single word longer than `max` is hard-split by chars.
        if word.chars().count() > max {
            if !cur.is_empty() {
                chunks.push(std::mem::take(&mut cur));
            }
            let mut piece = String::new();
            for ch in word.chars() {
                if piece.chars().count() >= max {
                    chunks.push(std::mem::take(&mut piece));
                }
                piece.push(ch);
            }
            if !piece.is_empty() {
                chunks.push(piece);
            }
            continue;
        }
        if cur.chars().count() + word.chars().count() + 1 > max {
            chunks.push(std::mem::take(&mut cur));
        }
        if !cur.is_empty() {
            cur.push(' ');
        }
        cur.push_str(word);
    }
    if !cur.is_empty() {
        chunks.push(cur);
    }
    chunks
}

/// Lightweight connectivity probe. Returns true if we can reach a host.
/// Used by the "Auto" mode to decide online vs offline.
pub fn is_online() -> bool {
    let client = match reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(4))
        .build()
    {
        Ok(c) => c,
        Err(_) => return false,
    };
    // A small, reliable endpoint. HEAD keeps it cheap.
    client
        .head("https://www.google.com/generate_204")
        .send()
        .map(|r| r.status().is_success() || r.status().as_u16() == 204)
        .unwrap_or(false)
}
