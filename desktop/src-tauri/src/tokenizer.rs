//! Pure-Rust NLLB-200 BPE tokenizer, ported from the validated
//! `NllbTokenizer.kt` / `validate.py` reference implementations.
//!
//! Pipeline (from Xenova/nllb-200-distilled-600M tokenizer.json):
//!   normalizer:    NFKC (approximating SentencePiece nmt_nfkc) + whitespace collapse
//!   pre_tokenizer: Metaspace (space -> '▁', add_prefix_space, split per word)
//!   model:         BPE (fuse_unk = true)
//!   post_process:  [src_lang] + tokens + </s>
//!
//! Only compiled when the `offline` feature is enabled.
#![cfg(feature = "offline")]

use std::collections::HashMap;
use std::fs;
use std::path::Path;

use unicode_normalization::UnicodeNormalization;

const META: char = '\u{2581}'; // ▁ LOWER ONE EIGHTH BLOCK
pub const UNK_ID: i64 = 3;
pub const EOS_ID: i64 = 2; // </s>
#[allow(dead_code)]
pub const PAD_ID: i64 = 1;
#[allow(dead_code)]
pub const BOS_ID: i64 = 0; // <s>

pub struct NllbTokenizer {
    vocab: HashMap<String, i64>,
    id_to_token: HashMap<i64, String>,
    merge_ranks: HashMap<String, usize>,
}

impl NllbTokenizer {
    /// Load vocab + merges from a tokenizer.json file.
    pub fn from_file(path: &Path) -> Result<Self, String> {
        let raw = fs::read_to_string(path).map_err(|e| format!("Read tokenizer.json: {e}"))?;
        let json: serde_json::Value =
            serde_json::from_str(&raw).map_err(|e| format!("Parse tokenizer.json: {e}"))?;

        let model = json
            .get("model")
            .ok_or("tokenizer.json: missing 'model'")?;

        // ---- vocab ----
        let vocab_obj = model
            .get("vocab")
            .and_then(|v| v.as_object())
            .ok_or("tokenizer.json: missing 'model.vocab'")?;
        let mut vocab = HashMap::with_capacity(vocab_obj.len());
        let mut id_to_token = HashMap::with_capacity(vocab_obj.len());
        for (tok, id) in vocab_obj {
            if let Some(i) = id.as_i64() {
                vocab.insert(tok.clone(), i);
                id_to_token.insert(i, tok.clone());
            }
        }

        // ---- merges (array of "left right" strings, or [left, right] pairs) ----
        let merges = model
            .get("merges")
            .and_then(|v| v.as_array())
            .ok_or("tokenizer.json: missing 'model.merges'")?;
        let mut merge_ranks = HashMap::with_capacity(merges.len());
        for (rank, m) in merges.iter().enumerate() {
            let key = if let Some(s) = m.as_str() {
                s.to_string()
            } else if let Some(pair) = m.as_array() {
                // Newer tokenizers serialize merges as ["left","right"].
                let a = pair.get(0).and_then(|x| x.as_str()).unwrap_or("");
                let b = pair.get(1).and_then(|x| x.as_str()).unwrap_or("");
                format!("{a} {b}")
            } else {
                continue;
            };
            merge_ranks.entry(key).or_insert(rank);
        }

        Ok(NllbTokenizer {
            vocab,
            id_to_token,
            merge_ranks,
        })
    }

    pub fn token_id(&self, token: &str) -> i64 {
        *self.vocab.get(token).unwrap_or(&UNK_ID)
    }

    /// Encode `text` with `src_lang_token`: [src_lang_id, ...subwords, EOS].
    pub fn encode(&self, text: &str, src_lang_token: &str) -> Vec<i64> {
        let pieces = self.tokenize_to_ids(text);
        let mut out = Vec::with_capacity(pieces.len() + 2);
        out.push(self.token_id(src_lang_token));
        out.extend_from_slice(&pieces);
        out.push(EOS_ID);
        out
    }

    /// NFKC normalize + collapse whitespace + trim.
    fn normalize(&self, text: &str) -> String {
        let nfkc: String = text.nfkc().collect();
        // Collapse any whitespace run to a single ASCII space, then trim.
        let mut result = String::with_capacity(nfkc.len());
        let mut prev_space = false;
        for ch in nfkc.chars() {
            if ch.is_whitespace() {
                if !prev_space {
                    result.push(' ');
                    prev_space = true;
                }
            } else {
                result.push(ch);
                prev_space = false;
            }
        }
        result.trim().to_string()
    }

    fn tokenize_to_ids(&self, text: &str) -> Vec<i64> {
        let normalized = self.normalize(text);
        if normalized.is_empty() {
            return Vec::new();
        }
        let mut ids: Vec<i64> = Vec::with_capacity(normalized.len() + 4);
        for word in normalized.split(' ') {
            if word.is_empty() {
                continue;
            }
            let piece = format!("{META}{word}");
            for sym in self.bpe(&piece) {
                match self.vocab.get(&sym) {
                    Some(&id) => ids.push(id),
                    None => {
                        // fuse_unk = true: collapse consecutive unknowns.
                        if ids.last() != Some(&UNK_ID) {
                            ids.push(UNK_ID);
                        }
                    }
                }
            }
        }
        ids
    }

    /// BPE over a single Metaspace word: repeatedly merge the lowest-rank pair.
    fn bpe(&self, word: &str) -> Vec<String> {
        // Split into Unicode scalar values (Rust chars already handle this).
        let mut symbols: Vec<String> = word.chars().map(|c| c.to_string()).collect();
        if symbols.len() < 2 {
            return symbols;
        }
        loop {
            let mut best_rank = usize::MAX;
            let mut best_idx: isize = -1;
            for j in 0..symbols.len() - 1 {
                let key = format!("{} {}", symbols[j], symbols[j + 1]);
                if let Some(&r) = self.merge_ranks.get(&key) {
                    if r < best_rank {
                        best_rank = r;
                        best_idx = j as isize;
                    }
                }
            }
            if best_idx < 0 {
                break;
            }
            let bi = best_idx as usize;
            let a = symbols[bi].clone();
            let b = symbols[bi + 1].clone();
            let merged = format!("{a}{b}");
            let mut next: Vec<String> = Vec::with_capacity(symbols.len());
            let mut k = 0;
            while k < symbols.len() {
                if k < symbols.len() - 1 && symbols[k] == a && symbols[k + 1] == b {
                    next.push(merged.clone());
                    k += 2;
                } else {
                    next.push(symbols[k].clone());
                    k += 1;
                }
            }
            if next.len() == symbols.len() {
                break;
            }
            symbols = next;
            if symbols.len() < 2 {
                break;
            }
        }
        symbols
    }

    /// Decode generated ids to text: skip specials/language codes, ▁ -> space.
    pub fn decode(&self, ids: &[i64]) -> String {
        let mut sb = String::new();
        for &id in ids {
            if id == EOS_ID || id == BOS_ID || id == PAD_ID || id == UNK_ID {
                continue;
            }
            let tok = match self.id_to_token.get(&id) {
                Some(t) => t,
                None => continue,
            };
            if is_special_token(tok) {
                continue;
            }
            sb.push_str(tok);
        }
        sb.replace(META, " ").trim().to_string()
    }
}

/// Language codes look like "xxx_Yyyy"; other specials are wrapped in <...>.
fn is_special_token(tok: &str) -> bool {
    if tok.len() >= 2 && tok.starts_with('<') && tok.ends_with('>') {
        return true;
    }
    let chars: Vec<char> = tok.chars().collect();
    if chars.len() == 8 && chars[3] == '_' {
        let lhs_ok = chars[0..3].iter().all(|c| c.is_ascii_lowercase());
        let rhs_ok = chars[4..8].iter().all(|c| c.is_ascii_alphabetic());
        return lhs_ok && rhs_ok;
    }
    false
}
