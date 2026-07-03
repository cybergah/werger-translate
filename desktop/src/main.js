// Wergêr frontend logic. Uses Tauri's global API (window.__TAURI__).
const invoke = window.__TAURI__.core.invoke;
const listen = window.__TAURI__.event.listen;
const openDialog = window.__TAURI__.dialog.open;
const openUrl = window.__TAURI__.opener.openUrl;

// ---------------- State ----------------
const state = {
  ui: localStorage.getItem("ui_lang") || "kmr",
  mode: localStorage.getItem("mode") || "auto", // auto | online | offline
  endpoint: localStorage.getItem("endpoint") || "",
  apiKey: localStorage.getItem("api_key") || "",
  folder: localStorage.getItem("folder") || "",
  src: localStorage.getItem("src_lang") || "tur_Latn",
  tgt: localStorage.getItem("tgt_lang") || "kmr_Latn",
  hq: localStorage.getItem("hq") === "1",
  online: false,
  offlineSupported: false,
  models: null,
};

const $ = (id) => document.getElementById(id);
const t = (key) => (I18N[state.ui] && I18N[state.ui][key]) || I18N.en[key] || key;

// ---------------- i18n application ----------------
function applyI18n() {
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t(el.getAttribute("data-i18n"));
  });
  document.querySelectorAll("[data-i18n-ph]").forEach((el) => {
    el.placeholder = t(el.getAttribute("data-i18n-ph"));
  });
  document.querySelectorAll("[data-i18n-title]").forEach((el) => {
    el.title = t(el.getAttribute("data-i18n-title"));
  });

  const rtl = RTL_LANGS.includes(state.ui);
  document.documentElement.dir = rtl ? "rtl" : "ltr";
  document.documentElement.lang = state.ui;

  updateLangLabels();
  updateStatusUI();
  renderModelStatus();
  renderGuide();
  // Keep an open onboarding card in sync with the chosen UI language.
  if (!$("onboard").classList.contains("hidden")) showOnboardStep(onboardIdx);
}

// RTL scripts → set the textarea direction so Arabic/Hebrew/etc. read correctly.
function dirFor(code) {
  return /_(Arab|Hebr|Thaa|Syrc|Nkoo|Adlm)$/.test(code) ? "rtl" : "ltr";
}

function langLabelHtml(code) {
  const name = (TRANSLATE_LANGS.find((l) => l.code === code) || {}).name || code;
  const esc = name.replace(/&/g, "&amp;").replace(/</g, "&lt;");
  // Kurmancî + Soranî show the Kurdistan flag icon.
  const flag = (code === "kmr_Latn" || code === "ckb_Arab")
    ? '<img src="assets/kurdistan_flag.png" class="lang-flag" alt="" />'
    : "";
  return flag + esc;
}

function updateLangLabels() {
  $("src-label").innerHTML = langLabelHtml(state.src);
  $("tgt-label").innerHTML = langLabelHtml(state.tgt);
  $("src-text").dir = dirFor(state.src);
  $("tgt-text").dir = dirFor(state.tgt);
}

// ---------------- Language pickers ----------------
// With ~200 languages, group the curated head as "Popular" and the rest as
// "All languages". Native <select> typeahead still lets you jump by typing.
const POPULAR_COUNT = 50;
function fillLangSelect(sel, selected) {
  sel.innerHTML = "";
  const mkOpt = (l) => {
    const o = document.createElement("option");
    o.value = l.code;
    o.textContent = l.name;
    if (l.code === selected) o.selected = true;
    return o;
  };
  const gPop = document.createElement("optgroup");
  gPop.label = t("lang_group_popular");
  const gAll = document.createElement("optgroup");
  gAll.label = t("lang_group_all");
  TRANSLATE_LANGS.forEach((l, i) => {
    (i < POPULAR_COUNT ? gPop : gAll).appendChild(mkOpt(l));
  });
  sel.appendChild(gPop);
  if (gAll.childElementCount) sel.appendChild(gAll);
}

// ---------------- Navigation ----------------
function showPage(id) {
  document.querySelectorAll(".page").forEach((p) => p.classList.remove("active"));
  $(id).classList.add("active");
}

// ---------------- Connectivity ----------------
async function refreshOnline() {
  try {
    state.online = await invoke("check_online");
  } catch {
    state.online = false;
  }
  updateStatusUI();
}

function updateStatusUI() {
  const dot = $("status-dot");
  const text = $("status-text");
  dot.classList.remove("online", "offline");
  if (state.online) {
    dot.classList.add("online");
    text.textContent = t("status_online");
  } else {
    dot.classList.add("offline");
    text.textContent = t("status_offline");
  }
}

// Decide the effective engine for the current mode + connectivity.
function effectiveEngine() {
  if (state.mode === "online") return "online";
  if (state.mode === "offline") return "offline";
  // auto
  return state.online ? "online" : "offline";
}

// ---------------- Translation ----------------
let translating = false;
let photoAbort = false; // user cancelled the photo overlay
let docAbort = false; // user cancelled the PDF overlay
let photoOrigUrl = null; // original photo (data URL)
let photoTransUrl = null; // translated overlay (data URL)
let photoShowingOriginal = false;
let docPdfB64 = null; // translated PDF bytes, held until the user saves
let docPdfName = "werger-translated.pdf";

// ---------------- Fullscreen image viewer ----------------
function openImgModal(url) {
  if (!url) return;
  $("img-modal-img").src = url;
  $("img-modal").classList.remove("hidden");
}

// Toggle the photo canvas between original and translated.
function togglePhotoView() {
  if (!photoTransUrl || !photoOrigUrl) return;
  photoShowingOriginal = !photoShowingOriginal;
  const url = photoShowingOriginal ? photoOrigUrl : photoTransUrl;
  const img = new Image();
  img.onload = () => {
    const c = $("photo-canvas");
    c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
  };
  img.src = url;
  $("photo-toggle").textContent = t(photoShowingOriginal ? "photo_show_translated" : "photo_show_original");
}

// Save the already-built translated PDF (deferred from the preview).
async function savePdfPreview() {
  if (!docPdfB64) return;
  let out;
  try {
    out = await window.__TAURI__.dialog.save({
      defaultPath: docPdfName,
      filters: [{ name: "PDF", extensions: ["pdf"] }],
    });
  } catch {
    return;
  }
  if (!out) return;
  try {
    await invoke("save_png", { path: out, dataBase64: docPdfB64 });
    $("docpreview-status").textContent = t("doc_saved");
    try { await window.__TAURI__.opener.revealItemInDir(out); } catch {}
  } catch (e) {
    $("docpreview-status").textContent = t("save_failed") + ": " + e;
  }
}
async function doTranslate() {
  if (translating) return;
  const text = $("src-text").value.trim();
  $("error-msg").textContent = "";
  if (!text) {
    $("error-msg").textContent = t("no_text");
    return;
  }

  translating = true;
  $("translate-btn").disabled = true;
  $("spinner").classList.remove("hidden");
  $("tgt-text").value = "";

  // Refresh connectivity for Auto mode so the choice is current.
  if (state.mode === "auto") await refreshOnline();
  const engine = effectiveEngine();

  try {
    let out;
    if (engine === "offline") {
      out = await invoke("translate_offline", {
        text,
        source: state.src,
        target: state.tgt,
        folder: state.folder || null,
      });
      $("engine-badge").textContent = "● " + t("engine_offline");
    } else {
      out = await invoke("translate_online", {
        text,
        source: state.src,
        target: state.tgt,
        endpoint: state.endpoint || null,
        apiKey: state.apiKey || null,
      });
      $("engine-badge").textContent = "● " + t("engine_online");
    }
    $("tgt-text").value = out;
  } catch (e) {
    // Auto fallback: if online failed and offline is supported, try offline.
    if (state.mode === "auto" && engine === "online" && state.offlineSupported) {
      try {
        const out = await invoke("translate_offline", {
          text,
          source: state.src,
          target: state.tgt,
          folder: state.folder || null,
        });
        $("tgt-text").value = out;
        $("engine-badge").textContent = "● " + t("engine_offline");
        return;
      } catch (e2) {
        $("error-msg").textContent = t("translate_error") + ": " + (e2 || e);
      }
    } else {
      $("error-msg").textContent = t("translate_error") + ": " + e;
    }
  } finally {
    translating = false;
    $("translate-btn").disabled = false;
    $("spinner").classList.add("hidden");
  }
}

// ---------------- TTS ----------------
// Strategy: use the OS (Web Speech) voice when one matches the language; for
// languages with no OS voice (especially Kurdish on Windows) fall back to the
// offline Meta MMS-TTS engine in the Rust backend.
let mmsAudio = null;

function findOsVoice(flores) {
  if (!("speechSynthesis" in window)) return null;
  const want = (SPEECH_LANG[flores] || "en").toLowerCase();
  const base = want.split("-")[0];
  const voices = window.speechSynthesis.getVoices();
  return (
    voices.find((v) => v.lang.toLowerCase() === want) ||
    voices.find((v) => v.lang.toLowerCase().startsWith(base)) ||
    null
  );
}

function speakOs(text, flores, voice) {
  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text);
  u.lang = SPEECH_LANG[flores] || "en-US";
  if (voice) u.voice = voice;
  window.speechSynthesis.speak(u);
}

async function speakMms(text, flores) {
  // Stop any ongoing speech first.
  if ("speechSynthesis" in window) window.speechSynthesis.cancel();
  if (mmsAudio) {
    mmsAudio.pause();
    mmsAudio = null;
  }
  try {
    const dataUrl = await invoke("mms_speak", {
      text,
      flores,
      folder: state.folder || null,
    });
    mmsAudio = new Audio(dataUrl);
    mmsAudio.play();
  } catch (e) {
    const msg = String(e);
    // Distinguish "no voice installed" from other failures for a helpful hint.
    if (/not found|No offline|no output|Nothing speakable/i.test(msg)) {
      $("error-msg").textContent = t("tts_unavailable");
    } else {
      $("error-msg").textContent = t("tts_failed") + ": " + msg;
    }
  }
}

async function speakOnline(text, flores) {
  if ("speechSynthesis" in window) window.speechSynthesis.cancel();
  if (mmsAudio) {
    mmsAudio.pause();
    mmsAudio = null;
  }
  try {
    const dataUrl = await invoke("online_tts", { text, flores });
    mmsAudio = new Audio(dataUrl);
    mmsAudio.play();
  } catch (e) {
    $("error-msg").textContent = t("tts_failed") + ": " + e;
  }
}

async function speak(text, flores) {
  if (!text.trim()) return;
  $("error-msg").textContent = "";
  // 1) Offline neural voice — best for Kurdish (only present in offline builds).
  let hasMms = false;
  try {
    hasMms = await invoke("mms_voice_available", { flores, folder: state.folder || null });
  } catch {}
  if (hasMms) {
    await speakMms(text, flores);
    return;
  }
  // 2) OS voice (instant, offline) when the language is installed.
  const osVoice = findOsVoice(flores);
  if (osVoice) {
    speakOs(text, flores, osVoice);
    return;
  }
  // 3) Online TTS — covers most of the ~200 languages. Tried regardless of the
  //    cached connectivity flag; it fails gracefully when actually offline.
  await speakOnline(text, flores);
}

// ---------------- Photo / Document import ----------------
function setSourceText(text) {
  $("src-text").value = text || "";
  $("src-count").textContent = $("src-text").value.length;
}

// ---------------- Photo: in-place overlay translation ----------------
// OCR each line with its bounding box, translate it, and paint the translation
// back onto a copy of the image (Google-Lens style). The result is shown on a
// <canvas> and can be saved as a PNG.

// Estimate the background colour behind a text box by averaging a 1px strip
// just outside it (above, else below, else the box itself), so the fill blends
// into the image instead of always being white.
function sampleBg(ctx, box, cw, ch) {
  const gap = Math.max(2, Math.round(box.h * 0.3));
  const above = Math.round(box.y - gap);
  const below = Math.round(box.y + box.h + gap);
  const yy = above >= 0 ? above : below < ch ? below : Math.round(box.y);
  const x0 = Math.max(0, Math.round(box.x));
  const w = Math.max(1, Math.min(cw - x0, Math.round(box.w)));
  let r = 255, g = 255, b = 255;
  try {
    const d = ctx.getImageData(x0, Math.max(0, Math.min(ch - 1, yy)), w, 1).data;
    let sr = 0, sg = 0, sb = 0, n = 0;
    for (let i = 0; i < d.length; i += 4) {
      sr += d[i]; sg += d[i + 1]; sb += d[i + 2]; n++;
    }
    if (n) { r = sr / n; g = sg / n; b = sb / n; }
  } catch {}
  return [r, g, b];
}

// Cover the original text with the sampled background, then draw the translation
// scaled to fit the box.
function drawOverlayLine(ctx, box, text, cw, ch) {
  const [r, g, b] = sampleBg(ctx, box, cw, ch);
  const pad = Math.max(1, box.h * 0.12);
  ctx.fillStyle = `rgb(${r | 0}, ${g | 0}, ${b | 0})`;
  ctx.fillRect(box.x - pad, box.y - pad, box.w + pad * 2, box.h + pad * 2);

  const lum = 0.299 * r + 0.587 * g + 0.114 * b;
  ctx.fillStyle = lum > 140 ? "#111111" : "#ffffff";

  const rtl = /_(Arab|Hebr|Thaa|Syrc|Nkoo|Adlm)$/.test(state.tgt);
  const family = "Arial, 'Segoe UI', 'Noto Sans', sans-serif";
  let size = Math.max(9, Math.floor(box.h * 0.82));
  while (size > 9) {
    ctx.font = `${size}px ${family}`;
    if (ctx.measureText(text).width <= box.w) break;
    size -= 1;
  }
  ctx.font = `${size}px ${family}`;
  ctx.textBaseline = "middle";
  ctx.direction = rtl ? "rtl" : "ltr";
  ctx.textAlign = rtl ? "right" : "left";
  const tx = rtl ? box.x + box.w : box.x;
  ctx.fillText(text, tx, box.y + box.h / 2, box.w);
}

// Run up to `concurrency` async tasks over `items`, preserving order.
async function mapPool(items, concurrency, fn) {
  const results = new Array(items.length);
  let idx = 0;
  const runner = async () => {
    while (idx < items.length) {
      const i = idx++;
      results[i] = await fn(items[i], i);
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, runner));
  return results;
}

// Translate an array of lines fast: offline uses one batched call; online runs
// several requests concurrently. Returns a same-length array (original text on
// any per-line failure).
async function translateLines(texts) {
  if (state.mode === "auto") await refreshOnline();
  const engine = effectiveEngine();

  if (engine === "offline") {
    try {
      return await invoke("translate_offline_batch", {
        texts,
        source: state.src,
        target: state.tgt,
        folder: state.folder || null,
      });
    } catch (e) {
      if (state.mode !== "auto") throw e; // online fallback only in Auto mode
    }
  }

  return await mapPool(texts, 5, async (tx) => {
    try {
      return await invoke("translate_online", {
        text: tx,
        source: state.src,
        target: state.tgt,
        endpoint: state.endpoint || null,
        apiKey: state.apiKey || null,
      });
    } catch {
      return tx;
    }
  });
}

async function openPhotoTranslate() {
  $("photo-error").textContent = "";
  let path;
  try {
    path = await openDialog({
      multiple: false,
      directory: false,
      filters: [
        { name: t("images"), extensions: ["png", "jpg", "jpeg", "bmp", "tiff", "gif", "webp"] },
      ],
    });
  } catch {
    return;
  }
  if (!path) return;
  showPage("page-photo");
  await runPhotoOverlay(path);
}

async function runPhotoOverlay(path) {
  const spin = $("photo-spinner");
  const err = $("photo-error");
  const status = $("photo-status");
  err.textContent = "";
  status.textContent = "";
  $("photo-save").disabled = true;
  photoAbort = false;
  photoShowingOriginal = false;
  photoOrigUrl = null;
  photoTransUrl = null;
  $("photo-toggle").classList.add("hidden");
  $("photo-fullscreen").classList.add("hidden");
  $("photo-cancel").classList.remove("hidden");
  spin.classList.remove("hidden");
  try {
    const layout = await invoke("ocr_image_layout", { path, flores: state.src });
    if (photoAbort) { status.textContent = t("cancelled"); return; }

    const img = new Image();
    await new Promise((res, rej) => {
      img.onload = res;
      img.onerror = () => rej(new Error("image load failed"));
      img.src = layout.data_url;
    });

    const canvas = $("photo-canvas");
    const cw = layout.width || img.naturalWidth;
    const ch = layout.height || img.naturalHeight;
    canvas.width = cw;
    canvas.height = ch;
    const ctx = canvas.getContext("2d");
    ctx.drawImage(img, 0, 0, cw, ch);

    const lines = (layout.lines || []).filter((l) => (l.text || "").trim());
    if (!lines.length) {
      err.textContent = t("ocr_empty");
      $("photo-save").disabled = false; // let them save the plain image anyway
      return;
    }

    // Translate all lines together (batched offline / concurrent online).
    const translations = await translateLines(lines.map((l) => l.text.trim()));
    if (photoAbort) { status.textContent = t("cancelled"); return; }
    for (let i = 0; i < lines.length; i++) {
      drawOverlayLine(ctx, lines[i], translations[i] || lines[i].text, cw, ch);
    }
    // Keep both images so the user can toggle original ↔ translated and zoom.
    photoOrigUrl = layout.data_url;
    photoTransUrl = canvas.toDataURL("image/png");
    photoShowingOriginal = false;
    $("photo-toggle").textContent = t("photo_show_original");
    $("photo-toggle").classList.remove("hidden");
    $("photo-fullscreen").classList.remove("hidden");
    status.textContent = t("photo_done");
    $("photo-save").disabled = false;
  } catch (e) {
    err.textContent = t("ocr_failed") + ": " + e;
  } finally {
    spin.classList.add("hidden");
    $("photo-cancel").classList.add("hidden");
  }
}

async function savePhoto() {
  const canvas = $("photo-canvas");
  if (!canvas.width) return;
  const b64 = canvas.toDataURL("image/png").split(",")[1];
  let out;
  try {
    out = await window.__TAURI__.dialog.save({
      defaultPath: "werger-translated.png",
      filters: [{ name: "PNG", extensions: ["png"] }],
    });
  } catch {
    return;
  }
  if (!out) return;
  try {
    await invoke("save_png", { path: out, dataBase64: b64 });
    $("photo-status").textContent = t("doc_saved");
    try {
      await window.__TAURI__.opener.revealItemInDir(out);
    } catch {}
  } catch (e) {
    $("photo-error").textContent = t("save_failed") + ": " + e;
  }
}

const DOC_MAX_BYTES = 2 * 1024 * 1024;

function base64ToBytes(b64) {
  const bin = atob(b64);
  const arr = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
  return arr;
}

function bytesToBase64(bytes) {
  let bin = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(bin);
}

function translatedName(path, ext) {
  const base = (path.split(/[\\/]/).pop() || "document").replace(/\.[^.]+$/, "");
  return `${base}.translated.${ext}`;
}

// PDF → in-place overlay: render each page (pdf.js), OCR it, translate the lines,
// draw the translations back onto the page, and rebuild a PDF (pdf-lib).
async function runPdfOverlay(path) {
  showPage("page-translate");
  docAbort = false;
  $("doc-cancel").classList.remove("hidden");
  $("spinner").classList.remove("hidden");
  $("error-msg").textContent = t("doc_translating");
  try {
    const b64 = await invoke("read_file_capped", { path, maxBytes: DOC_MAX_BYTES });
    const pdfjsLib = window.pdfjsLib;
    pdfjsLib.GlobalWorkerOptions.workerSrc = "vendor/pdf.worker.min.js";
    const pdf = await pdfjsLib.getDocument({ data: base64ToBytes(b64) }).promise;
    const { PDFDocument } = window.PDFLib;
    const outDoc = await PDFDocument.create();
    const pageUrls = [];

    const scale = 2; // render at 2× for crisper OCR
    for (let p = 1; p <= pdf.numPages; p++) {
      if (docAbort) { $("error-msg").textContent = t("cancelled"); return; }
      $("error-msg").textContent = `${t("doc_translating")} ${p}/${pdf.numPages}`;
      const page = await pdf.getPage(p);
      const viewport = page.getViewport({ scale });
      const canvas = document.createElement("canvas");
      canvas.width = Math.ceil(viewport.width);
      canvas.height = Math.ceil(viewport.height);
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      await page.render({ canvasContext: ctx, viewport }).promise;

      const pngB64 = canvas.toDataURL("image/png").split(",")[1];
      let layout;
      try {
        layout = await invoke("ocr_image_layout_bytes", { dataBase64: pngB64, flores: state.src });
      } catch {
        layout = { lines: [] };
      }
      const lines = (layout.lines || []).filter((l) => (l.text || "").trim());
      if (lines.length) {
        const translations = await translateLines(lines.map((l) => l.text.trim()));
        for (let i = 0; i < lines.length; i++) {
          drawOverlayLine(ctx, lines[i], translations[i] || lines[i].text, canvas.width, canvas.height);
        }
      }

      const ptW = viewport.width / scale;
      const ptH = viewport.height / scale;
      const dataUrl = canvas.toDataURL("image/png");
      pageUrls.push(dataUrl);
      const img = await outDoc.embedPng(base64ToBytes(dataUrl.split(",")[1]));
      const outPage = outDoc.addPage([ptW, ptH]);
      outPage.drawImage(img, { x: 0, y: 0, width: ptW, height: ptH });
    }

    // Build the translated PDF once, keep it for the deferred Save.
    docPdfB64 = bytesToBase64(await outDoc.save());
    docPdfName = translatedName(path, "pdf");

    // Show the translated pages in-app; the user saves when ready.
    const cont = $("docpreview-pages");
    cont.innerHTML = "";
    for (const url of pageUrls) {
      const im = document.createElement("img");
      im.src = url;
      im.addEventListener("click", () => openImgModal(url));
      cont.appendChild(im);
    }
    $("docpreview-status").textContent = "";
    showPage("page-docpreview");
    $("error-msg").textContent = "";
  } catch (e) {
    $("error-msg").textContent = t("doc_failed") + ": " + e;
  } finally {
    $("spinner").classList.add("hidden");
    $("doc-cancel").classList.add("hidden");
  }
}

async function importFromDocument() {
  $("error-msg").textContent = "";
  let path;
  try {
    path = await openDialog({
      multiple: false,
      directory: false,
      filters: [
        { name: t("documents"), extensions: ["pdf", "txt", "md", "csv", "log", "docx"] },
      ],
    });
  } catch {
    return;
  }
  if (!path) return;
  // PDF → layout-preserving overlay; .txt/.docx → same-format copy in Rust.
  if (/\.pdf$/i.test(path)) {
    await runPdfOverlay(path);
    return;
  }
  showPage("page-translate");
  $("spinner").classList.remove("hidden");
  $("error-msg").textContent = t("doc_translating");
  try {
    if (state.mode === "auto") await refreshOnline();
    const engine = effectiveEngine();
    // Produce a same-format translated copy next to the original.
    const outPath = await invoke("translate_document", {
      path,
      source: state.src,
      target: state.tgt,
      engine,
      folder: state.folder || null,
      endpoint: state.endpoint || null,
      apiKey: state.apiKey || null,
    });
    $("error-msg").textContent = t("doc_saved") + ": " + outPath;
    // Reveal the result in the file manager (best-effort).
    try {
      await window.__TAURI__.opener.revealItemInDir(outPath);
    } catch {}
  } catch (e) {
    $("error-msg").textContent = t("doc_failed") + ": " + e;
  } finally {
    $("spinner").classList.add("hidden");
  }
}

// ---------------- Models ----------------
function renderModelStatus() {
  const el = $("model-status");
  if (!el) return;
  if (!state.models) {
    el.textContent = "";
    return;
  }
  const m = state.models;
  const line = (ok, label) =>
    `<div class="${ok ? "file-ok" : "file-no"}">${ok ? "✓" : "✗"} ${label}</div>`;
  el.innerHTML =
    line(m.encoder, "encoder.onnx") +
    line(m.decoder, "decoder.onnx") +
    line(m.tokenizer, "tokenizer.json") +
    `<div style="margin-top:6px">${m.complete ? t("models_ready") : t("models_missing")}</div>`;
}

async function detectModels() {
  try {
    state.models = await invoke("detect_models", { folder: state.folder || null });
    if (!state.folder) {
      state.folder = state.models.folder;
      $("folder-input").value = state.folder;
    }
    renderModelStatus();
  } catch (e) {
    $("model-status").textContent = String(e);
  }
}

async function downloadModels() {
  $("error-msg").textContent = "";
  $("progress-wrap").classList.remove("hidden");
  $("download-btn").disabled = true;
  $("progress-label").textContent = t("downloading") + "…";
  try {
    state.models = await invoke("download_models", {
      folder: state.folder || null,
      highQuality: state.hq,
    });
    $("progress-fill").style.width = "100%";
    $("progress-label").textContent = t("download_done");
    renderModelStatus();
  } catch (e) {
    $("progress-label").textContent = t("download_failed") + ": " + e;
  } finally {
    $("download-btn").disabled = false;
  }
}

// ---------------- Wiring ----------------
function persist() {
  localStorage.setItem("ui_lang", state.ui);
  localStorage.setItem("mode", state.mode);
  localStorage.setItem("endpoint", state.endpoint);
  localStorage.setItem("api_key", state.apiKey);
  localStorage.setItem("folder", state.folder);
  localStorage.setItem("src_lang", state.src);
  localStorage.setItem("tgt_lang", state.tgt);
}

// ---------------- User guide + onboarding ----------------
// One content set drives both the Guide screen and the first-run tutorial.
const GUIDE_STEPS = [
  { icon: "👋", key: "welcome" },
  { icon: "✍️", key: "text" },
  { icon: "📷", key: "photo" },
  { icon: "📄", key: "doc" },
  { icon: "🌐", key: "modes" },
  { icon: "🔒", key: "privacy" },
];

function renderGuide() {
  const cont = $("guide-content");
  if (!cont) return;
  cont.innerHTML = "";
  for (const step of GUIDE_STEPS) {
    const sec = document.createElement("div");
    sec.className = "guide-section";
    const icon = document.createElement("div");
    icon.className = "g-icon";
    icon.textContent = step.icon;
    const body = document.createElement("div");
    const h = document.createElement("p");
    h.className = "g-title";
    h.textContent = t(`guide_${step.key}_title`);
    const p = document.createElement("p");
    p.className = "g-body";
    p.textContent = t(`guide_${step.key}_body`);
    body.appendChild(h);
    body.appendChild(p);
    sec.appendChild(icon);
    sec.appendChild(body);
    cont.appendChild(sec);
  }
}

let onboardIdx = 0;
function showOnboardStep(i) {
  onboardIdx = i;
  const step = GUIDE_STEPS[i];
  $("onboard-icon").textContent = step.icon;
  $("onboard-title").textContent = t(`guide_${step.key}_title`);
  $("onboard-body").textContent = t(`guide_${step.key}_body`);
  // Progress dots
  const dots = $("onboard-dots");
  dots.innerHTML = "";
  GUIDE_STEPS.forEach((_, k) => {
    const d = document.createElement("span");
    d.className = "dot-i" + (k === i ? " active" : "");
    dots.appendChild(d);
  });
  // Back hidden on the first step; Next becomes "Get started" on the last.
  $("onboard-back").style.visibility = i === 0 ? "hidden" : "visible";
  $("onboard-next").textContent = i === GUIDE_STEPS.length - 1 ? t("onboard_start") : t("onboard_next");
}

function startOnboarding() {
  $("onboard").classList.remove("hidden");
  showOnboardStep(0);
}

function finishOnboarding() {
  $("onboard").classList.add("hidden");
  localStorage.setItem("onboarded", "1");
}

function init() {
  // Dismiss the animated splash after a few seconds, then — on the very first
  // run — show the step-by-step onboarding tutorial (skippable).
  setTimeout(() => {
    const s = $("splash");
    if (s) {
      s.classList.add("splash-hide");
      setTimeout(() => s.remove(), 700);
    }
    if (!localStorage.getItem("onboarded")) startOnboarding();
  }, 2600);

  // UI language
  $("ui-lang-select").value = state.ui;
  $("ui-lang-select").addEventListener("change", (e) => {
    state.ui = e.target.value;
    persist();
    applyI18n();
  });

  // Translate language pickers
  fillLangSelect($("src-lang"), state.src);
  fillLangSelect($("tgt-lang"), state.tgt);
  $("src-lang").addEventListener("change", (e) => {
    state.src = e.target.value;
    persist();
    updateLangLabels();
  });
  $("tgt-lang").addEventListener("change", (e) => {
    state.tgt = e.target.value;
    persist();
    updateLangLabels();
  });
  $("swap-btn").addEventListener("click", () => {
    [state.src, state.tgt] = [state.tgt, state.src];
    fillLangSelect($("src-lang"), state.src);
    fillLangSelect($("tgt-lang"), state.tgt);
    // Swap text too, if any.
    const a = $("src-text").value;
    $("src-text").value = $("tgt-text").value;
    $("tgt-text").value = a;
    persist();
    updateLangLabels();
  });

  // Translate
  $("translate-btn").addEventListener("click", doTranslate);
  $("src-text").addEventListener("input", () => {
    $("src-count").textContent = $("src-text").value.length;
  });
  // Ctrl+Enter to translate
  $("src-text").addEventListener("keydown", (e) => {
    if (e.ctrlKey && e.key === "Enter") doTranslate();
  });

  // Tools
  $("src-clear").addEventListener("click", () => {
    $("src-text").value = "";
    $("src-count").textContent = "0";
  });
  $("tgt-copy").addEventListener("click", () => {
    navigator.clipboard.writeText($("tgt-text").value);
  });
  $("src-speak").addEventListener("click", () =>
    speak($("src-text").value, state.src)
  );
  $("tgt-speak").addEventListener("click", () =>
    speak($("tgt-text").value, state.tgt)
  );

  // Navigation
  $("nav-settings").addEventListener("click", () => showPage("page-settings"));
  $("nav-guide").addEventListener("click", () => showPage("page-guide"));
  $("nav-about").addEventListener("click", () => showPage("page-about"));
  $("nav-photo").addEventListener("click", openPhotoTranslate);
  $("nav-doc").addEventListener("click", importFromDocument);
  $("photo-pick").addEventListener("click", openPhotoTranslate);
  $("photo-save").addEventListener("click", savePhoto);
  $("photo-cancel").addEventListener("click", () => { photoAbort = true; });
  $("doc-cancel").addEventListener("click", () => { docAbort = true; });
  $("photo-toggle").addEventListener("click", togglePhotoView);
  $("photo-fullscreen").addEventListener("click", () => openImgModal(photoShowingOriginal ? photoOrigUrl : photoTransUrl));
  $("img-modal").addEventListener("click", () => $("img-modal").classList.add("hidden"));
  $("docpreview-save").addEventListener("click", savePdfPreview);
  $("docpreview-back").addEventListener("click", () => showPage("page-translate"));
  $("photo-back").addEventListener("click", () => showPage("page-translate"));
  $("settings-back").addEventListener("click", () => showPage("page-translate"));
  $("guide-back").addEventListener("click", () => showPage("page-translate"));
  $("guide-replay").addEventListener("click", startOnboarding);
  $("about-back").addEventListener("click", () => showPage("page-translate"));

  // Onboarding tutorial controls
  $("onboard-skip").addEventListener("click", finishOnboarding);
  $("onboard-back").addEventListener("click", () => {
    if (onboardIdx > 0) showOnboardStep(onboardIdx - 1);
  });
  $("onboard-next").addEventListener("click", () => {
    if (onboardIdx < GUIDE_STEPS.length - 1) showOnboardStep(onboardIdx + 1);
    else finishOnboarding();
  });
  $("cybergah-link").addEventListener("click", (e) => {
    e.preventDefault();
    openUrl("https://cybergah.com");
  });

  // Mode segmented control
  document.querySelectorAll("#mode-seg .seg-btn").forEach((btn) => {
    if (btn.dataset.mode === state.mode) btn.classList.add("active");
    else btn.classList.remove("active");
    btn.addEventListener("click", () => {
      state.mode = btn.dataset.mode;
      document
        .querySelectorAll("#mode-seg .seg-btn")
        .forEach((b) => b.classList.toggle("active", b === btn));
      persist();
    });
  });

  // Online settings
  $("endpoint-input").value = state.endpoint;
  $("apikey-input").value = state.apiKey;
  $("endpoint-input").addEventListener("change", (e) => {
    state.endpoint = e.target.value.trim();
    persist();
  });
  $("apikey-input").addEventListener("change", (e) => {
    state.apiKey = e.target.value.trim();
    persist();
  });

  // Models settings
  $("folder-input").value = state.folder;
  $("folder-input").addEventListener("change", (e) => {
    state.folder = e.target.value.trim();
    persist();
    detectModels();
  });
  $("pick-folder").addEventListener("click", async () => {
    const dir = await openDialog({ directory: true, multiple: false });
    if (dir) {
      state.folder = dir;
      $("folder-input").value = dir;
      persist();
      detectModels();
    }
  });
  const hqToggle = $("hq-toggle");
  if (hqToggle) {
    hqToggle.checked = state.hq;
    hqToggle.addEventListener("change", (e) => {
      state.hq = e.target.checked;
      localStorage.setItem("hq", state.hq ? "1" : "0");
    });
  }
  $("download-btn").addEventListener("click", downloadModels);
  $("redetect-btn").addEventListener("click", detectModels);

  // Quick-translate: text captured by the global shortcut (Ctrl+Shift+T).
  listen("quick-translate", (ev) => {
    const text = ev.payload || "";
    if (!text) return;
    showPage("page-translate");
    setSourceText(text);
    doTranslate();
  });

  // Download progress events
  listen("model-download-progress", (ev) => {
    const p = ev.payload;
    $("progress-fill").style.width = p.percent.toFixed(1) + "%";
    $("progress-label").textContent =
      `${t("downloading")}: ${p.file} (${p.index + 1}/${p.total}) — ${p.percent.toFixed(0)}%`;
  });

  applyI18n();

  // Async startup
  (async () => {
    try {
      state.offlineSupported = await invoke("offline_supported");
    } catch {
      state.offlineSupported = false;
    }
    const hint = $("offline-support-hint");
    if (hint) {
      hint.textContent = state.offlineSupported ? "" : t("offline_not_built");
    }
    if (!state.folder) {
      try {
        state.folder = await invoke("default_models_folder");
        $("folder-input").value = state.folder;
      } catch {}
    }
    await detectModels();
    await refreshOnline();
    // Periodic connectivity refresh.
    setInterval(refreshOnline, 20000);
  })();

  // Some browsers populate voices asynchronously.
  if ("speechSynthesis" in window) {
    window.speechSynthesis.onvoiceschanged = () => {};
  }
}

window.addEventListener("DOMContentLoaded", init);
