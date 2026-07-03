//! Offline NLLB-200 translation engine on ONNX Runtime (`ort` v2).
//!
//! Supports two model layouts, auto-detected from the folder:
//!   * MERGED  (distilled-600M, Xenova): `encoder.onnx` + `decoder.onnx`
//!     (one decoder with a `use_cache_branch` switch).
//!   * SPLIT   (distilled-1.3B, exported here): `encoder_model.onnx` +
//!     `decoder_model.onnx` (prefill) + `decoder_with_past_model.onnx` (steps),
//!     each possibly with an external-data `.onnx_data` file (the 1.3B decoders
//!     exceed ONNX's 2 GB single-proto limit, so they can't be merged).
//!
//! Pipeline either way: tokenize -> encoder -> greedy decode (no-repeat 3-gram)
//! -> detokenize.
//!
//! Only compiled when the `offline` feature is enabled.
#![cfg(feature = "offline")]

use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use ndarray::{Array, Array2, Array4, ArrayD, Axis, IxDyn};
use ort::session::{builder::GraphOptimizationLevel, Session};
use ort::value::Tensor;

use crate::lang::flores_to_nllb_id;
use crate::tokenizer::{NllbTokenizer, EOS_ID, PAD_ID};

/// Max sequences per batched inference call (bounds peak memory for the KV cache).
const MAX_BATCH: usize = 16;

const NUM_HEADS: usize = 16;
const HEAD_DIM: usize = 64;
const DECODER_START: i64 = 2; // </s>

/// The decoder layout in use.
enum Decoders {
    /// 600M: single decoder with `use_cache_branch`.
    Merged(Mutex<Session>),
    /// 1.3B: separate prefill + with-past decoders.
    Split {
        init: Mutex<Session>,
        past: Mutex<Session>,
    },
}

pub struct OfflineEngine {
    encoder: Mutex<Session>,
    decoders: Decoders,
    tokenizer: NllbTokenizer,
    num_layers: usize,
}

/// Count decoder layers from inputs/outputs named `*.<N>.decoder.key`.
fn count_layers<'a>(names: impl Iterator<Item = &'a str>, infix: &str) -> usize {
    let mut max_idx: i64 = -1;
    for n in names {
        if let Some(rest) = n.strip_prefix(infix) {
            if let Some(idx) = rest.split('.').next().and_then(|s| s.parse::<i64>().ok()) {
                if idx > max_idx {
                    max_idx = idx;
                }
            }
        }
    }
    if max_idx >= 0 {
        (max_idx + 1) as usize
    } else {
        12
    }
}

impl OfflineEngine {
    /// Build the engine from a folder, auto-detecting the merged/split layout.
    pub fn from_dir(dir: &Path) -> Result<Self, String> {
        let tok_path = dir.join("tokenizer.json");
        if !tok_path.is_file() {
            return Err(format!("Missing model file: {}", tok_path.display()));
        }
        let tokenizer = NllbTokenizer::from_file(&tok_path)?;

        let split_past = dir.join("decoder_with_past_model.onnx");
        if split_past.is_file() {
            // ---- SPLIT (1.3B) ----
            let enc_path = dir.join("encoder_model.onnx");
            let init_path = dir.join("decoder_model.onnx");
            for p in [&enc_path, &init_path, &split_past] {
                if !p.is_file() {
                    return Err(format!("Missing model file: {}", p.display()));
                }
            }
            let encoder = build_session(&enc_path)?;
            let init = build_session(&init_path)?;
            let past = build_session(&split_past)?;
            let num_layers = count_layers(past.inputs().iter().map(|i| i.name()), "past_key_values.");
            Ok(OfflineEngine {
                encoder: Mutex::new(encoder),
                decoders: Decoders::Split {
                    init: Mutex::new(init),
                    past: Mutex::new(past),
                },
                tokenizer,
                num_layers,
            })
        } else {
            // ---- MERGED (600M) ----
            let enc_path = dir.join("encoder.onnx");
            let dec_path = dir.join("decoder.onnx");
            for p in [&enc_path, &dec_path] {
                if !p.is_file() {
                    return Err(format!("Missing model file: {}", p.display()));
                }
            }
            let encoder = build_session(&enc_path)?;
            let decoder = build_session(&dec_path)?;
            let num_layers =
                count_layers(decoder.inputs().iter().map(|i| i.name()), "past_key_values.");
            Ok(OfflineEngine {
                encoder: Mutex::new(encoder),
                decoders: Decoders::Merged(Mutex::new(decoder)),
                tokenizer,
                num_layers,
            })
        }
    }

    /// Translate `text` from `src` to `tgt` (FLORES-200 codes).
    pub fn translate(&self, text: &str, src: &str, tgt: &str) -> Result<String, String> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Ok(String::new());
        }

        let tgt_lang_id =
            flores_to_nllb_id(tgt).ok_or_else(|| format!("Unsupported target language: {tgt}"))?;
        let _ = flores_to_nllb_id(src)
            .ok_or_else(|| format!("Unsupported source language: {src}"))?;

        let input_ids = self.tokenizer.encode(trimmed, src);
        let src_len = input_ids.len();

        // ---- Encoder ----
        let enc_hidden = self.run_encoder(&input_ids, src_len)?;
        let enc_mask = Array::from_elem((1, src_len), 1i64);
        let max_out = (24 + src_len * 3).min(512);

        let generated = match &self.decoders {
            Decoders::Merged(dec) => {
                self.decode_merged(dec, tgt_lang_id, &enc_mask, &enc_hidden, max_out)?
            }
            Decoders::Split { init, past } => {
                self.decode_split(init, past, tgt_lang_id, &enc_mask, &enc_hidden, max_out)?
            }
        };
        Ok(self.tokenizer.decode(&generated))
    }

    /// Translate many texts at once. On the merged (600M) model this runs a
    /// single batched encoder + batched greedy decode, so wall-clock is bounded
    /// by the longest line instead of the sum of all lines — a large speedup for
    /// the photo overlay (many short lines). Identical texts are deduped and
    /// non-linguistic lines (pure numbers/symbols) are passed through untouched.
    ///
    /// Robustness: on any batched-decode error it falls back to per-line
    /// [`translate`], and any empty result for a non-empty input is retried
    /// individually — so this never returns worse output than the serial path.
    pub fn translate_batch(&self, texts: &[String], src: &str, tgt: &str) -> Result<Vec<String>, String> {
        let tgt_lang_id =
            flores_to_nllb_id(tgt).ok_or_else(|| format!("Unsupported target language: {tgt}"))?;
        flores_to_nllb_id(src).ok_or_else(|| format!("Unsupported source language: {src}"))?;

        let mut out = vec![String::new(); texts.len()];

        // Dedupe: map each entry to a unique index; skip pure number/symbol lines.
        let mut uniq: Vec<String> = Vec::new();
        let mut uniq_index: std::collections::HashMap<String, usize> = std::collections::HashMap::new();
        let mut slot: Vec<Option<usize>> = vec![None; texts.len()];
        for (i, raw) in texts.iter().enumerate() {
            let trimmed = raw.trim();
            if trimmed.is_empty() {
                continue;
            }
            if !has_letters(trimmed) {
                out[i] = raw.clone(); // numbers, "(F:)", "8 GB", "×" … keep as-is
                continue;
            }
            let key = trimmed.to_string();
            let u = *uniq_index.entry(key.clone()).or_insert_with(|| {
                uniq.push(key);
                uniq.len() - 1
            });
            slot[i] = Some(u);
        }
        if uniq.is_empty() {
            return Ok(out);
        }

        // Translate unique texts (batched on merged; serial on split/1.3B).
        let mut translated: Vec<String> = Vec::with_capacity(uniq.len());
        match &self.decoders {
            Decoders::Merged(dec) => {
                for chunk in uniq.chunks(MAX_BATCH) {
                    let refs: Vec<&str> = chunk.iter().map(|s| s.as_str()).collect();
                    match self.translate_many_merged(dec, &refs, src, tgt_lang_id) {
                        Ok(v) => translated.extend(v),
                        Err(_) => {
                            // Fall back to per-line for this chunk.
                            for t in &refs {
                                translated.push(
                                    self.translate(t, src, tgt).unwrap_or_else(|_| (*t).to_string()),
                                );
                            }
                        }
                    }
                }
            }
            Decoders::Split { .. } => {
                for t in &uniq {
                    translated.push(self.translate(t, src, tgt).unwrap_or_else(|_| t.clone()));
                }
            }
        }

        // Safety net: retry any empty result individually.
        for (u, t) in uniq.iter().enumerate() {
            if translated[u].trim().is_empty() {
                translated[u] = self.translate(t, src, tgt).unwrap_or_else(|_| t.clone());
            }
        }

        for (i, s) in slot.iter().enumerate() {
            if let Some(u) = s {
                out[i] = translated[*u].clone();
            }
        }
        Ok(out)
    }

    /// Batched encoder + greedy decode on the merged decoder for a set of texts.
    fn translate_many_merged(
        &self,
        dec: &Mutex<Session>,
        texts: &[&str],
        src: &str,
        tgt_lang_id: i64,
    ) -> Result<Vec<String>, String> {
        let b = texts.len();
        if b == 0 {
            return Ok(Vec::new());
        }

        // Encode + right-pad to the longest sequence in the batch.
        let enc: Vec<Vec<i64>> = texts.iter().map(|t| self.tokenizer.encode(t, src)).collect();
        let max_s = enc.iter().map(|v| v.len()).max().unwrap_or(1).max(1);
        let mut ids = Array2::<i64>::from_elem((b, max_s), PAD_ID);
        let mut mask = Array2::<i64>::from_elem((b, max_s), 0i64);
        for (r, v) in enc.iter().enumerate() {
            for (c, &tk) in v.iter().enumerate() {
                ids[[r, c]] = tk;
                mask[[r, c]] = 1;
            }
        }

        let enc_hidden = self.run_encoder_batch(ids, &mask)?;
        let max_out = (24 + max_s * 3).min(512);

        // ---- batched greedy decode (merged: use_cache_branch) ----
        let mut past_dec_key = empty_kv_b(self.num_layers, b);
        let mut past_dec_val = empty_kv_b(self.num_layers, b);
        let mut past_enc_key = empty_kv_b(self.num_layers, b);
        let mut past_enc_val = empty_kv_b(self.num_layers, b);

        let mut generated: Vec<Vec<i64>> = vec![Vec::new(); b];
        let mut finished = vec![false; b];

        // Prefill with [</s>, <tgt_lang>] for every row.
        let prime =
            Array2::<i64>::from_shape_fn((b, 2), |(_, c)| if c == 0 { DECODER_START } else { tgt_lang_id });
        let res = self.run_merged_decoder_batch(
            dec, &prime, &mask, &enc_hidden, false, &past_dec_key, &past_dec_val, &past_enc_key,
            &past_enc_val,
        )?;
        past_dec_key = res.dec_key;
        past_dec_val = res.dec_val;
        past_enc_key = res.enc_key;
        past_enc_val = res.enc_val;
        let mut next = argmax_batch_last(&res.logits);

        let mut step = 0;
        loop {
            for r in 0..b {
                if finished[r] {
                    continue;
                }
                if next[r] == EOS_ID {
                    finished[r] = true;
                } else {
                    generated[r].push(next[r]);
                }
            }
            if finished.iter().all(|&f| f) || step >= max_out {
                break;
            }
            step += 1;

            // Finished rows feed PAD; their logits are ignored.
            let feed = Array2::<i64>::from_shape_fn((b, 1), |(r, _)| if finished[r] { PAD_ID } else { next[r] });
            let res = self.run_merged_decoder_batch(
                dec, &feed, &mask, &enc_hidden, true, &past_dec_key, &past_dec_val, &past_enc_key,
                &past_enc_val,
            )?;
            past_dec_key = res.dec_key;
            past_dec_val = res.dec_val;
            for r in 0..b {
                if finished[r] {
                    continue;
                }
                let banned = banned_ngram_tokens(&generated[r], 3);
                next[r] = argmax_row_banned(&res.logits, r, &banned);
            }
        }

        Ok(generated.iter().map(|g| self.tokenizer.decode(g)).collect())
    }

    fn run_encoder_batch(&self, ids: Array2<i64>, mask: &Array2<i64>) -> Result<ArrayD<f32>, String> {
        let mut enc = self.encoder.lock().unwrap();
        let outputs = enc
            .run(ort::inputs![
                "input_ids" => Tensor::from_array(ids).map_err(err)?,
                "attention_mask" => Tensor::from_array(mask.clone()).map_err(err)?,
            ])
            .map_err(err)?;
        let (shape, data) = outputs["last_hidden_state"]
            .try_extract_tensor::<f32>()
            .map_err(err)?;
        let dims: Vec<usize> = shape.iter().map(|&d| d as usize).collect();
        ArrayD::from_shape_vec(IxDyn(&dims), data.to_vec())
            .map_err(|e| format!("enc hidden batch shape: {e}"))
    }

    #[allow(clippy::too_many_arguments)]
    fn run_merged_decoder_batch(
        &self,
        dec: &Mutex<Session>,
        input_ids: &Array2<i64>,
        enc_mask: &Array2<i64>,
        enc_hidden: &ArrayD<f32>,
        use_cache: bool,
        past_dec_key: &[Array4<f32>],
        past_dec_val: &[Array4<f32>],
        past_enc_key: &[Array4<f32>],
        past_enc_val: &[Array4<f32>],
    ) -> Result<DecodeResult, String> {
        let b = input_ids.shape()[0];
        let mut inputs = ort::inputs! {
            "input_ids" => Tensor::from_array(input_ids.clone()).map_err(err)?,
            "encoder_attention_mask" => Tensor::from_array(enc_mask.clone()).map_err(err)?,
            "encoder_hidden_states" => Tensor::from_array(enc_hidden.clone()).map_err(err)?,
            "use_cache_branch" => Tensor::from_array(([1usize], vec![use_cache])).map_err(err)?,
        };
        for i in 0..self.num_layers {
            let (dk, dv, ek, ev) = if use_cache {
                (
                    past_dec_key[i].clone(),
                    past_dec_val[i].clone(),
                    past_enc_key[i].clone(),
                    past_enc_val[i].clone(),
                )
            } else {
                (empty4b(b), empty4b(b), empty4b(b), empty4b(b))
            };
            inputs.push((
                format!("past_key_values.{i}.decoder.key").into(),
                Tensor::from_array(dk).map_err(err)?.into(),
            ));
            inputs.push((
                format!("past_key_values.{i}.decoder.value").into(),
                Tensor::from_array(dv).map_err(err)?.into(),
            ));
            inputs.push((
                format!("past_key_values.{i}.encoder.key").into(),
                Tensor::from_array(ek).map_err(err)?.into(),
            ));
            inputs.push((
                format!("past_key_values.{i}.encoder.value").into(),
                Tensor::from_array(ev).map_err(err)?.into(),
            ));
        }

        let mut sess = dec.lock().unwrap();
        let outputs = sess.run(inputs).map_err(err)?;

        let logits = read_logits(&outputs)?;
        let mut dec_key = Vec::with_capacity(self.num_layers);
        let mut dec_val = Vec::with_capacity(self.num_layers);
        for i in 0..self.num_layers {
            dec_key.push(read_kv(&outputs, &format!("present.{i}.decoder.key"))?);
            dec_val.push(read_kv(&outputs, &format!("present.{i}.decoder.value"))?);
        }
        let (enc_key, enc_val) = if use_cache {
            (past_enc_key.to_vec(), past_enc_val.to_vec())
        } else {
            let mut ek = Vec::with_capacity(self.num_layers);
            let mut ev = Vec::with_capacity(self.num_layers);
            for i in 0..self.num_layers {
                ek.push(read_kv(&outputs, &format!("present.{i}.encoder.key"))?);
                ev.push(read_kv(&outputs, &format!("present.{i}.encoder.value"))?);
            }
            (ek, ev)
        };

        Ok(DecodeResult {
            logits,
            dec_key,
            dec_val,
            enc_key,
            enc_val,
        })
    }

    fn run_encoder(&self, input_ids: &[i64], src_len: usize) -> Result<ArrayD<f32>, String> {
        let ids = Array::from_shape_vec((1, src_len), input_ids.to_vec())
            .map_err(|e| format!("encoder ids shape: {e}"))?;
        let mask = Array::from_elem((1, src_len), 1i64);
        let mut enc = self.encoder.lock().unwrap();
        let outputs = enc
            .run(ort::inputs![
                "input_ids" => Tensor::from_array(ids).map_err(err)?,
                "attention_mask" => Tensor::from_array(mask).map_err(err)?,
            ])
            .map_err(err)?;
        let (shape, data) = outputs["last_hidden_state"]
            .try_extract_tensor::<f32>()
            .map_err(err)?;
        let dims: Vec<usize> = shape.iter().map(|&d| d as usize).collect();
        ArrayD::from_shape_vec(IxDyn(&dims), data.to_vec())
            .map_err(|e| format!("enc hidden shape: {e}"))
    }

    // ===================== SPLIT (1.3B) =====================

    #[allow(clippy::too_many_arguments)]
    fn decode_split(
        &self,
        init: &Mutex<Session>,
        past: &Mutex<Session>,
        tgt_lang_id: i64,
        enc_mask: &Array<i64, ndarray::Ix2>,
        enc_hidden: &ArrayD<f32>,
        max_out: usize,
    ) -> Result<Vec<i64>, String> {
        // ---- Prefill: decoder_model.onnx with [</s>, <tgt_lang>] ----
        let (logits, mut dec_key, mut dec_val, enc_key, enc_val) = {
            let prime = Array::from_shape_vec((1, 2), vec![DECODER_START, tgt_lang_id])
                .map_err(|e| format!("prime shape: {e}"))?;
            let mut sess = init.lock().unwrap();
            let outputs = sess
                .run(ort::inputs![
                    "input_ids" => Tensor::from_array(prime).map_err(err)?,
                    "encoder_attention_mask" => Tensor::from_array(enc_mask.clone()).map_err(err)?,
                    "encoder_hidden_states" => Tensor::from_array(enc_hidden.clone()).map_err(err)?,
                ])
                .map_err(err)?;
            let logits = read_logits(&outputs)?;
            let mut dk = Vec::with_capacity(self.num_layers);
            let mut dv = Vec::with_capacity(self.num_layers);
            let mut ek = Vec::with_capacity(self.num_layers);
            let mut ev = Vec::with_capacity(self.num_layers);
            for i in 0..self.num_layers {
                dk.push(read_kv(&outputs, &format!("present.{i}.decoder.key"))?);
                dv.push(read_kv(&outputs, &format!("present.{i}.decoder.value"))?);
                ek.push(read_kv(&outputs, &format!("present.{i}.encoder.key"))?);
                ev.push(read_kv(&outputs, &format!("present.{i}.encoder.value"))?);
            }
            (logits, dk, dv, ek, ev)
        };

        let mut generated: Vec<i64> = Vec::with_capacity(64);
        let mut next_id = argmax_last_row(&logits);

        let mut step = 0;
        while next_id != EOS_ID && step < max_out {
            generated.push(next_id);
            step += 1;

            let ids = Array::from_shape_vec((1, 1), vec![next_id])
                .map_err(|e| format!("dec ids shape: {e}"))?;
            let mut inputs = ort::inputs! {
                "input_ids" => Tensor::from_array(ids).map_err(err)?,
                "encoder_attention_mask" => Tensor::from_array(enc_mask.clone()).map_err(err)?,
            };
            for i in 0..self.num_layers {
                inputs.push((
                    format!("past_key_values.{i}.decoder.key").into(),
                    Tensor::from_array(dec_key[i].clone()).map_err(err)?.into(),
                ));
                inputs.push((
                    format!("past_key_values.{i}.decoder.value").into(),
                    Tensor::from_array(dec_val[i].clone()).map_err(err)?.into(),
                ));
                inputs.push((
                    format!("past_key_values.{i}.encoder.key").into(),
                    Tensor::from_array(enc_key[i].clone()).map_err(err)?.into(),
                ));
                inputs.push((
                    format!("past_key_values.{i}.encoder.value").into(),
                    Tensor::from_array(enc_val[i].clone()).map_err(err)?.into(),
                ));
            }

            let mut sess = past.lock().unwrap();
            let outputs = sess.run(inputs).map_err(err)?;
            let logits = read_logits(&outputs)?;
            let banned = banned_ngram_tokens(&generated, 3);
            next_id = argmax_last_row_banned(&logits, &banned);
            // Encoder KV is constant; only the decoder KV grows.
            for i in 0..self.num_layers {
                dec_key[i] = read_kv(&outputs, &format!("present.{i}.decoder.key"))?;
                dec_val[i] = read_kv(&outputs, &format!("present.{i}.decoder.value"))?;
            }
        }
        Ok(generated)
    }

    // ===================== MERGED (600M) =====================

    fn decode_merged(
        &self,
        dec: &Mutex<Session>,
        tgt_lang_id: i64,
        enc_mask: &Array<i64, ndarray::Ix2>,
        enc_hidden: &ArrayD<f32>,
        max_out: usize,
    ) -> Result<Vec<i64>, String> {
        let mut past_dec_key = empty_kv(self.num_layers);
        let mut past_dec_val = empty_kv(self.num_layers);
        let mut past_enc_key = empty_kv(self.num_layers);
        let mut past_enc_val = empty_kv(self.num_layers);

        let mut generated: Vec<i64> = Vec::with_capacity(64);

        // Prefill: input_ids = [</s>, <tgt_lang>], use_cache_branch = false.
        let mut next_id = {
            let res = self.run_merged_decoder(
                dec,
                &[DECODER_START, tgt_lang_id],
                enc_mask,
                enc_hidden,
                false,
                &past_dec_key,
                &past_dec_val,
                &past_enc_key,
                &past_enc_val,
            )?;
            past_dec_key = res.dec_key;
            past_dec_val = res.dec_val;
            past_enc_key = res.enc_key;
            past_enc_val = res.enc_val;
            argmax_last_row(&res.logits)
        };

        let mut step = 0;
        while next_id != EOS_ID && step < max_out {
            generated.push(next_id);
            step += 1;
            let res = self.run_merged_decoder(
                dec,
                &[next_id],
                enc_mask,
                enc_hidden,
                true,
                &past_dec_key,
                &past_dec_val,
                &past_enc_key,
                &past_enc_val,
            )?;
            let banned = banned_ngram_tokens(&generated, 3);
            next_id = argmax_last_row_banned(&res.logits, &banned);
            past_dec_key = res.dec_key;
            past_dec_val = res.dec_val;
        }
        Ok(generated)
    }

    #[allow(clippy::too_many_arguments)]
    fn run_merged_decoder(
        &self,
        dec: &Mutex<Session>,
        input_ids: &[i64],
        enc_mask: &Array<i64, ndarray::Ix2>,
        enc_hidden: &ArrayD<f32>,
        use_cache: bool,
        past_dec_key: &[Array4<f32>],
        past_dec_val: &[Array4<f32>],
        past_enc_key: &[Array4<f32>],
        past_enc_val: &[Array4<f32>],
    ) -> Result<DecodeResult, String> {
        let dec_len = input_ids.len();
        let ids = Array::from_shape_vec((1, dec_len), input_ids.to_vec())
            .map_err(|e| format!("decoder ids shape: {e}"))?;

        let mut inputs = ort::inputs! {
            "input_ids" => Tensor::from_array(ids).map_err(err)?,
            "encoder_attention_mask" => Tensor::from_array(enc_mask.clone()).map_err(err)?,
            "encoder_hidden_states" => Tensor::from_array(enc_hidden.clone()).map_err(err)?,
            "use_cache_branch" => Tensor::from_array(([1usize], vec![use_cache])).map_err(err)?,
        };

        for i in 0..self.num_layers {
            let (dk, dv, ek, ev) = if use_cache {
                (
                    past_dec_key[i].clone(),
                    past_dec_val[i].clone(),
                    past_enc_key[i].clone(),
                    past_enc_val[i].clone(),
                )
            } else {
                (empty4(), empty4(), empty4(), empty4())
            };
            inputs.push((
                format!("past_key_values.{i}.decoder.key").into(),
                Tensor::from_array(dk).map_err(err)?.into(),
            ));
            inputs.push((
                format!("past_key_values.{i}.decoder.value").into(),
                Tensor::from_array(dv).map_err(err)?.into(),
            ));
            inputs.push((
                format!("past_key_values.{i}.encoder.key").into(),
                Tensor::from_array(ek).map_err(err)?.into(),
            ));
            inputs.push((
                format!("past_key_values.{i}.encoder.value").into(),
                Tensor::from_array(ev).map_err(err)?.into(),
            ));
        }

        let mut sess = dec.lock().unwrap();
        let outputs = sess.run(inputs).map_err(err)?;

        let logits = read_logits(&outputs)?;
        let mut dec_key = Vec::with_capacity(self.num_layers);
        let mut dec_val = Vec::with_capacity(self.num_layers);
        for i in 0..self.num_layers {
            dec_key.push(read_kv(&outputs, &format!("present.{i}.decoder.key"))?);
            dec_val.push(read_kv(&outputs, &format!("present.{i}.decoder.value"))?);
        }
        let (enc_key, enc_val) = if use_cache {
            (past_enc_key.to_vec(), past_enc_val.to_vec())
        } else {
            let mut ek = Vec::with_capacity(self.num_layers);
            let mut ev = Vec::with_capacity(self.num_layers);
            for i in 0..self.num_layers {
                ek.push(read_kv(&outputs, &format!("present.{i}.encoder.key"))?);
                ev.push(read_kv(&outputs, &format!("present.{i}.encoder.value"))?);
            }
            (ek, ev)
        };

        Ok(DecodeResult {
            logits,
            dec_key,
            dec_val,
            enc_key,
            enc_val,
        })
    }
}

struct DecodeResult {
    logits: ArrayD<f32>,
    dec_key: Vec<Array4<f32>>,
    dec_val: Vec<Array4<f32>>,
    enc_key: Vec<Array4<f32>>,
    enc_val: Vec<Array4<f32>>,
}

fn build_session(path: &PathBuf) -> Result<Session, String> {
    let threads = std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(2)
        .clamp(2, 8);
    Session::builder()
        .map_err(err)?
        .with_optimization_level(GraphOptimizationLevel::Level3)
        .map_err(err)?
        .with_intra_threads(threads)
        .map_err(err)?
        .commit_from_file(path)
        .map_err(err)
}

fn read_logits(outputs: &ort::session::SessionOutputs) -> Result<ArrayD<f32>, String> {
    let (shape, data) = outputs["logits"].try_extract_tensor::<f32>().map_err(err)?;
    let dims: Vec<usize> = shape.iter().map(|&d| d as usize).collect();
    ArrayD::from_shape_vec(IxDyn(&dims), data.to_vec()).map_err(|e| format!("logits shape: {e}"))
}

/// Read a present.* KV output into a [1,16,seq,64] array.
fn read_kv(outputs: &ort::session::SessionOutputs, name: &str) -> Result<Array4<f32>, String> {
    let (shape, data) = outputs[name].try_extract_tensor::<f32>().map_err(err)?;
    let dims: Vec<usize> = shape.iter().map(|&d| d as usize).collect();
    if dims.len() != 4 {
        return Err(format!("{name}: expected 4D, got {dims:?}"));
    }
    Array::from_shape_vec((dims[0], dims[1], dims[2], dims[3]), data.to_vec())
        .map_err(|e| format!("{name} shape: {e}"))
}

/// Argmax over the last sequence row of a [1, seq, vocab] logits array.
fn argmax_last_row(logits: &ArrayD<f32>) -> i64 {
    let seq = logits.shape()[1];
    let row = logits.index_axis(Axis(0), 0);
    let last = row.index_axis(Axis(0), seq - 1);
    let mut best = 0usize;
    let mut best_val = f32::NEG_INFINITY;
    for (i, &v) in last.iter().enumerate() {
        if v > best_val {
            best_val = v;
            best = i;
        }
    }
    best as i64
}

/// Tokens that would complete an already-seen `n`-gram, given the history.
fn banned_ngram_tokens(generated: &[i64], n: usize) -> HashSet<i64> {
    let mut banned = HashSet::new();
    if n < 2 || generated.len() < n - 1 {
        return banned;
    }
    let k = n - 1;
    let prefix = &generated[generated.len() - k..];
    let limit = generated.len().saturating_sub(k);
    for i in 0..limit {
        if &generated[i..i + k] == prefix {
            if let Some(&t) = generated.get(i + k) {
                banned.insert(t);
            }
        }
    }
    banned
}

/// Argmax over the last row, skipping `banned` tokens.
fn argmax_last_row_banned(logits: &ArrayD<f32>, banned: &HashSet<i64>) -> i64 {
    if banned.is_empty() {
        return argmax_last_row(logits);
    }
    let seq = logits.shape()[1];
    let row = logits.index_axis(Axis(0), 0);
    let last = row.index_axis(Axis(0), seq - 1);
    let mut best = -1i64;
    let mut best_val = f32::NEG_INFINITY;
    for (i, &v) in last.iter().enumerate() {
        if banned.contains(&(i as i64)) {
            continue;
        }
        if v > best_val {
            best_val = v;
            best = i as i64;
        }
    }
    if best < 0 {
        argmax_last_row(logits)
    } else {
        best
    }
}

fn empty4() -> Array4<f32> {
    Array4::<f32>::zeros((1, NUM_HEADS, 0, HEAD_DIM))
}

fn empty_kv(n: usize) -> Vec<Array4<f32>> {
    (0..n).map(|_| empty4()).collect()
}

fn empty4b(b: usize) -> Array4<f32> {
    Array4::<f32>::zeros((b, NUM_HEADS, 0, HEAD_DIM))
}

fn empty_kv_b(n: usize, b: usize) -> Vec<Array4<f32>> {
    (0..n).map(|_| empty4b(b)).collect()
}

/// True if the string contains at least one alphabetic character (so pure
/// numbers/punctuation lines can skip the model).
fn has_letters(s: &str) -> bool {
    s.chars().any(|c| c.is_alphabetic())
}

/// Per-row argmax over the last sequence position of a [b, seq, vocab] tensor.
fn argmax_batch_last(logits: &ArrayD<f32>) -> Vec<i64> {
    let b = logits.shape()[0];
    (0..b).map(|r| argmax_row_banned(logits, r, &HashSet::new())).collect()
}

/// Argmax over the last row of batch element `r`, skipping `banned` tokens.
fn argmax_row_banned(logits: &ArrayD<f32>, r: usize, banned: &HashSet<i64>) -> i64 {
    let seq = logits.shape()[1];
    let row = logits.index_axis(Axis(0), r);
    let last = row.index_axis(Axis(0), seq - 1);
    let mut best = -1i64;
    let mut best_val = f32::NEG_INFINITY;
    for (i, &v) in last.iter().enumerate() {
        if banned.contains(&(i as i64)) {
            continue;
        }
        if v > best_val {
            best_val = v;
            best = i as i64;
        }
    }
    if best >= 0 {
        return best;
    }
    // Everything banned → pick the unrestricted best.
    let mut b2 = 0usize;
    let mut bv2 = f32::NEG_INFINITY;
    for (i, &v) in last.iter().enumerate() {
        if v > bv2 {
            bv2 = v;
            b2 = i;
        }
    }
    b2 as i64
}

fn err<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}
