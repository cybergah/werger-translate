//! Document handling for the Document screen.
//!
//! Two jobs:
//!   - `extract`          : pull plain text out of .txt/.pdf (for quick preview).
//!   - `translate_file`   : translate a document and write a **same-format** copy
//!                          next to it, preserving structure:
//!       * .txt / .md / …  → translate line by line, write .txt
//!       * .docx           → translate every <w:t> text node inside the zipped
//!                           XML, keeping ALL Word formatting/images/tables
//!       * .pdf            → not done in-place here (see note in translate_file)

use std::collections::HashMap;
use std::fs;
use std::io::{Read, Write};
use std::path::Path;

/// Extract text from a document at `path` (.pdf or plain text).
pub fn extract(path: &str) -> Result<String, String> {
    let p = Path::new(path);
    let ext = p
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_lowercase();
    match ext.as_str() {
        "pdf" => pdf_extract::extract_text(p).map_err(|e| format!("PDF read failed: {e}")),
        "txt" | "md" | "markdown" | "csv" | "log" | "" => {
            fs::read_to_string(p).map_err(|e| format!("Read failed: {e}"))
        }
        other => Err(format!("Unsupported document type: .{other}. Use .txt or .pdf.")),
    }
}

/// Where a translated copy is written: `report.docx` → `report.translated.docx`.
pub fn output_path(input: &str) -> String {
    let p = Path::new(input);
    let ext = p.extension().and_then(|e| e.to_str()).unwrap_or("");
    let stem = p.file_stem().and_then(|e| e.to_str()).unwrap_or("document");
    let name = if ext.is_empty() {
        format!("{stem}.translated")
    } else {
        format!("{stem}.translated.{ext}")
    };
    match p.parent() {
        Some(dir) if !dir.as_os_str().is_empty() => dir.join(name).to_string_lossy().to_string(),
        _ => name,
    }
}

/// Translate `path` and write a same-format translated copy. Returns its path.
/// `translate` translates a single text segment.
pub fn translate_file<F>(path: &str, mut translate: F) -> Result<String, String>
where
    F: FnMut(&str) -> Result<String, String>,
{
    let ext = Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_lowercase();
    let out = output_path(path);

    // Cache identical segments so repeated runs/lines hit the engine only once.
    let mut cache: HashMap<String, String> = HashMap::new();
    let mut tr = |s: &str| -> Result<String, String> {
        if s.trim().is_empty() {
            return Ok(s.to_string());
        }
        if let Some(v) = cache.get(s) {
            return Ok(v.clone());
        }
        let v = translate(s)?;
        cache.insert(s.to_string(), v.clone());
        Ok(v)
    };

    match ext.as_str() {
        "txt" | "md" | "markdown" | "csv" | "log" | "" => translate_txt(path, &out, &mut tr)?,
        "docx" => translate_docx(path, &out, &mut tr)?,
        "pdf" => {
            return Err(
                "PDF in-place translation isn't supported yet — convert the PDF to .docx \
                 (or use the 📷/text flow). .txt and .docx keep their formatting."
                    .to_string(),
            )
        }
        other => return Err(format!("Unsupported document type: .{other}")),
    }
    Ok(out)
}

fn translate_txt(
    path: &str,
    out: &str,
    tr: &mut dyn FnMut(&str) -> Result<String, String>,
) -> Result<(), String> {
    let text = fs::read_to_string(path).map_err(|e| format!("Read failed: {e}"))?;
    let mut lines = Vec::new();
    for line in text.split('\n') {
        lines.push(tr(line)?);
    }
    fs::write(out, lines.join("\n")).map_err(|e| format!("Write failed: {e}"))
}

/// Translate a .docx by rewriting the text in its XML parts, preserving the rest
/// of the zip (styles, images, relationships) untouched.
fn translate_docx(
    path: &str,
    out: &str,
    tr: &mut dyn FnMut(&str) -> Result<String, String>,
) -> Result<(), String> {
    let file = fs::File::open(path).map_err(|e| format!("Open failed: {e}"))?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| format!("Bad .docx: {e}"))?;

    let out_file = fs::File::create(out).map_err(|e| format!("Create failed: {e}"))?;
    let mut writer = zip::ZipWriter::new(out_file);
    let opts: zip::write::FileOptions<()> =
        zip::write::FileOptions::default().compression_method(zip::CompressionMethod::Deflated);

    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| format!("Zip read: {e}"))?;
        let name = entry.name().to_string();
        // Translate the main document plus headers/footers (where body text lives).
        let is_text_part = name == "word/document.xml"
            || (name.starts_with("word/header") && name.ends_with(".xml"))
            || (name.starts_with("word/footer") && name.ends_with(".xml"));

        if entry.is_dir() {
            continue;
        }

        if is_text_part {
            let mut xml = String::new();
            entry
                .read_to_string(&mut xml)
                .map_err(|e| format!("Read {name}: {e}"))?;
            let new_xml = transform_wt(&xml, tr)?;
            writer
                .start_file(name.clone(), opts)
                .map_err(|e| format!("Zip write {name}: {e}"))?;
            writer
                .write_all(new_xml.as_bytes())
                .map_err(|e| format!("Zip write {name}: {e}"))?;
        } else {
            // Copy bytes verbatim (images, styles, rels, …).
            let mut buf = Vec::new();
            entry
                .read_to_end(&mut buf)
                .map_err(|e| format!("Read {name}: {e}"))?;
            writer
                .start_file(name.clone(), opts)
                .map_err(|e| format!("Zip write {name}: {e}"))?;
            writer
                .write_all(&buf)
                .map_err(|e| format!("Zip write {name}: {e}"))?;
        }
    }
    writer.finish().map_err(|e| format!("Finalize: {e}"))?;
    Ok(())
}

/// Replace the text inside every `<w:t ...>…</w:t>` node, translating it while
/// leaving the surrounding XML/formatting intact.
fn transform_wt(
    xml: &str,
    tr: &mut dyn FnMut(&str) -> Result<String, String>,
) -> Result<String, String> {
    // (?s) so . matches newlines; non-greedy inner text.
    let re = regex::Regex::new(r"(?s)(<w:t\b[^>]*>)(.*?)(</w:t>)").unwrap();
    let mut out = String::with_capacity(xml.len());
    let mut last = 0usize;
    for caps in re.captures_iter(xml) {
        let m = caps.get(0).unwrap();
        out.push_str(&xml[last..m.start()]);
        let open = caps.get(1).unwrap().as_str();
        let inner = caps.get(2).unwrap().as_str();
        let close = caps.get(3).unwrap().as_str();

        let decoded = xml_decode(inner);
        let translated = if decoded.trim().is_empty() {
            decoded
        } else {
            tr(&decoded)?
        };
        out.push_str(open);
        out.push_str(&xml_encode(&translated));
        out.push_str(close);
        last = m.end();
    }
    out.push_str(&xml[last..]);
    Ok(out)
}

fn xml_decode(s: &str) -> String {
    s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

fn xml_encode(s: &str) -> String {
    s.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}
