//! Offline OCR for the Photo screen.
//!
//! On Windows we use the built-in `Windows.Media.Ocr` engine — it runs fully
//! offline and recognizes whatever OCR language packs the user has installed
//! (Settings → Time & Language → Language → Optional features). This mirrors
//! the Android app's ML Kit OCR.
//!
//! Kurdish has no OCR pack on Windows; Latin-script Kurmancî is best recognized
//! with the English/Turkish pack installed. Arabic-script recognition needs the
//! Arabic OCR pack.

use serde::Serialize;

/// One recognized line with its pixel bounding box (source-image coordinates).
#[derive(Serialize)]
pub struct OcrLine {
    pub text: String,
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

/// The full OCR result for the overlay renderer: image dimensions, the original
/// image inlined as a `data:` URL (so the webview can draw it without needing
/// filesystem/asset-protocol access), and every recognized line with its box.
#[derive(Serialize)]
pub struct OcrLayout {
    pub width: u32,
    pub height: u32,
    pub data_url: String,
    pub lines: Vec<OcrLine>,
}

/// Guess an image MIME type from a file extension (for the inlined data URL).
fn mime_for(path: &str) -> &'static str {
    let ext = std::path::Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();
    match ext.as_str() {
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "bmp" => "image/bmp",
        "gif" => "image/gif",
        "tif" | "tiff" => "image/tiff",
        "webp" => "image/webp",
        _ => "application/octet-stream",
    }
}

/// Read the image file and encode it as a `data:` URL.
fn data_url(path: &str) -> Result<String, String> {
    use base64::Engine;
    let bytes = std::fs::read(path).map_err(|e| e.to_string())?;
    let b64 = base64::engine::general_purpose::STANDARD.encode(&bytes);
    Ok(format!("data:{};base64,{b64}", mime_for(path)))
}

/// Map a FLORES-200 code to a BCP-47 tag for the OCR engine.
fn flores_to_bcp47(flores: &str) -> &str {
    match flores {
        "kmr_Latn" => "tr", // best-effort: Latin pack (Turkish covers Kurmancî letters)
        "ckb_Arab" | "arb_Arab" | "pes_Arab" | "urd_Arab" | "uig_Arab" => "ar",
        "tur_Latn" => "tr",
        "eng_Latn" => "en",
        "deu_Latn" => "de",
        "fra_Latn" => "fr",
        "spa_Latn" => "es",
        "rus_Cyrl" => "ru",
        "zho_Hans" => "zh-Hans",
        "zho_Hant" => "zh-Hant",
        "jpn_Jpan" => "ja",
        "kor_Hang" => "ko",
        other => other.split('_').next().unwrap_or("en"),
    }
}

#[cfg(windows)]
pub fn recognize(path: &str, flores: &str) -> Result<String, String> {
    use windows::core::HSTRING;
    use windows::Globalization::Language;
    use windows::Graphics::Imaging::BitmapDecoder;
    use windows::Media::Ocr::OcrEngine;
    use windows::Storage::{FileAccessMode, StorageFile};

    // `Error::message()` returns String on some windows-rs versions and HSTRING
    // on others; `.to_string()` normalizes both to String.
    let err = |e: windows::core::Error| e.message().to_string();

    // Load the image file into a SoftwareBitmap.
    let file = StorageFile::GetFileFromPathAsync(&HSTRING::from(path))
        .map_err(err)?
        .get()
        .map_err(err)?;
    let stream = file
        .OpenAsync(FileAccessMode::Read)
        .map_err(err)?
        .get()
        .map_err(err)?;
    let decoder = BitmapDecoder::CreateAsync(&stream)
        .map_err(err)?
        .get()
        .map_err(err)?;
    let bitmap = decoder
        .GetSoftwareBitmapAsync()
        .map_err(err)?
        .get()
        .map_err(err)?;

    // Pick an OCR engine: prefer the requested language, else the user's
    // profile languages, else fail with a clear message.
    let bcp47 = flores_to_bcp47(flores);
    let engine = Language::CreateLanguage(&HSTRING::from(bcp47))
        .ok()
        .and_then(|lang| OcrEngine::TryCreateFromLanguage(&lang).ok())
        .or_else(|| OcrEngine::TryCreateFromUserProfileLanguages().ok())
        .ok_or_else(|| {
            format!(
                "No OCR language pack available for '{bcp47}'. Install it in Windows \
                 Settings → Language → Optional features → Add OCR."
            )
        })?;

    let result = engine
        .RecognizeAsync(&bitmap)
        .map_err(err)?
        .get()
        .map_err(err)?;

    result.Text().map(|t| t.to_string_lossy()).map_err(err)
}

#[cfg(not(windows))]
pub fn recognize(_path: &str, _flores: &str) -> Result<String, String> {
    Err("On-device OCR is only available on Windows in this build.".to_string())
}

/// OCR a decoded SoftwareBitmap into (width, height, per-line boxes).
#[cfg(windows)]
fn ocr_from_bitmap(
    bitmap: &windows::Graphics::Imaging::SoftwareBitmap,
    flores: &str,
) -> Result<(u32, u32, Vec<OcrLine>), String> {
    use windows::core::HSTRING;
    use windows::Globalization::Language;
    use windows::Media::Ocr::OcrEngine;

    let err = |e: windows::core::Error| e.message().to_string();

    let width = bitmap.PixelWidth().map_err(err)? as u32;
    let height = bitmap.PixelHeight().map_err(err)? as u32;

    let bcp47 = flores_to_bcp47(flores);
    let engine = Language::CreateLanguage(&HSTRING::from(bcp47))
        .ok()
        .and_then(|lang| OcrEngine::TryCreateFromLanguage(&lang).ok())
        .or_else(|| OcrEngine::TryCreateFromUserProfileLanguages().ok())
        .ok_or_else(|| {
            format!(
                "No OCR language pack available for '{bcp47}'. Install it in Windows \
                 Settings → Language → Optional features → Add OCR."
            )
        })?;

    let result = engine
        .RecognizeAsync(bitmap)
        .map_err(err)?
        .get()
        .map_err(err)?;

    let mut lines = Vec::new();
    let ocr_lines = result.Lines().map_err(err)?;
    let n = ocr_lines.Size().map_err(err)?;
    for i in 0..n {
        let line = ocr_lines.GetAt(i).map_err(err)?;
        let words = line.Words().map_err(err)?;
        let wn = words.Size().map_err(err)?;
        if wn == 0 {
            continue;
        }
        // The line box is the union of its word boxes.
        let (mut min_x, mut min_y, mut max_x, mut max_y) =
            (f32::MAX, f32::MAX, f32::MIN, f32::MIN);
        for j in 0..wn {
            let r = words.GetAt(j).map_err(err)?.BoundingRect().map_err(err)?;
            min_x = min_x.min(r.X);
            min_y = min_y.min(r.Y);
            max_x = max_x.max(r.X + r.Width);
            max_y = max_y.max(r.Y + r.Height);
        }
        let text = line.Text().map_err(err)?.to_string_lossy();
        if text.trim().is_empty() {
            continue;
        }
        lines.push(OcrLine {
            text,
            x: min_x as f64,
            y: min_y as f64,
            w: (max_x - min_x) as f64,
            h: (max_y - min_y) as f64,
        });
    }
    Ok((width, height, lines))
}

/// Recognize text AND its per-line bounding boxes for the overlay renderer.
/// Boxes are in the source image's pixel coordinates.
#[cfg(windows)]
pub fn recognize_layout(path: &str, flores: &str) -> Result<OcrLayout, String> {
    use windows::core::HSTRING;
    use windows::Graphics::Imaging::BitmapDecoder;
    use windows::Storage::{FileAccessMode, StorageFile};

    let err = |e: windows::core::Error| e.message().to_string();

    let file = StorageFile::GetFileFromPathAsync(&HSTRING::from(path))
        .map_err(err)?
        .get()
        .map_err(err)?;
    let stream = file
        .OpenAsync(FileAccessMode::Read)
        .map_err(err)?
        .get()
        .map_err(err)?;
    let decoder = BitmapDecoder::CreateAsync(&stream)
        .map_err(err)?
        .get()
        .map_err(err)?;
    let bitmap = decoder
        .GetSoftwareBitmapAsync()
        .map_err(err)?
        .get()
        .map_err(err)?;

    let (width, height, lines) = ocr_from_bitmap(&bitmap, flores)?;
    Ok(OcrLayout {
        width,
        height,
        data_url: data_url(path)?,
        lines,
    })
}

/// Like [`recognize_layout`] but from in-memory image bytes (e.g. a PDF page
/// rendered to PNG by the webview). `data_url` is left empty — the caller
/// already has the pixels.
#[cfg(windows)]
pub fn recognize_layout_bytes(bytes: &[u8], flores: &str) -> Result<OcrLayout, String> {
    use windows::Graphics::Imaging::BitmapDecoder;
    use windows::Storage::Streams::{DataWriter, InMemoryRandomAccessStream};

    let err = |e: windows::core::Error| e.message().to_string();

    let stream = InMemoryRandomAccessStream::new().map_err(err)?;
    let writer = DataWriter::CreateDataWriter(&stream.GetOutputStreamAt(0).map_err(err)?)
        .map_err(err)?;
    writer.WriteBytes(bytes).map_err(err)?;
    writer.StoreAsync().map_err(err)?.get().map_err(err)?;
    writer.FlushAsync().map_err(err)?.get().map_err(err)?;
    stream.Seek(0).map_err(err)?;

    let decoder = BitmapDecoder::CreateAsync(&stream)
        .map_err(err)?
        .get()
        .map_err(err)?;
    let bitmap = decoder
        .GetSoftwareBitmapAsync()
        .map_err(err)?
        .get()
        .map_err(err)?;

    let (width, height, lines) = ocr_from_bitmap(&bitmap, flores)?;
    Ok(OcrLayout {
        width,
        height,
        data_url: String::new(),
        lines,
    })
}

#[cfg(not(windows))]
pub fn recognize_layout(_path: &str, _flores: &str) -> Result<OcrLayout, String> {
    Err("On-device OCR is only available on Windows in this build.".to_string())
}

#[cfg(not(windows))]
pub fn recognize_layout_bytes(_bytes: &[u8], _flores: &str) -> Result<OcrLayout, String> {
    Err("On-device OCR is only available on Windows in this build.".to_string())
}
