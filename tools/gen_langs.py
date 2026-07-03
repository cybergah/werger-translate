#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate the full FLORES-200 / NLLB-200 language tables for Wergêr and write
them directly into:
  - desktop/src/i18n.js            (TRANSLATE_LANGS + SPEECH_LANG)
  - desktop/src-tauri/src/lang.rs  (flores_to_nllb_id / _libre / _google)
  - app/.../nllb/Languages.kt      (Languages.all)

Token IDs are read from tools/tokenizer.json (authoritative). The curated head
languages keep their native/Turkish names; the long tail uses canonical English
names. Run: python tools/gen_langs.py
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOK = os.path.join(ROOT, "tools", "tokenizer.json")

# --- token ids (authoritative) -------------------------------------------
tok = json.load(io.open(TOK, encoding="utf-8"))
IDS = {}
import re
pat = re.compile(r"^[a-z]{2,4}_[A-Z][a-z]{3}$")
for t in tok.get("added_tokens", []):
    if pat.match(t["content"]):
        IDS[t["content"]] = t["id"]

# --- canonical English names for all 202 codes ---------------------------
EN = {
 "ace_Arab":"Acehnese (Arabic)","ace_Latn":"Acehnese (Latin)","acm_Arab":"Mesopotamian Arabic",
 "acq_Arab":"Ta'izzi-Adeni Arabic","aeb_Arab":"Tunisian Arabic","afr_Latn":"Afrikaans",
 "ajp_Arab":"South Levantine Arabic","aka_Latn":"Akan","amh_Ethi":"Amharic",
 "apc_Arab":"North Levantine Arabic","arb_Arab":"Arabic","ars_Arab":"Najdi Arabic",
 "ary_Arab":"Moroccan Arabic","arz_Arab":"Egyptian Arabic","asm_Beng":"Assamese",
 "ast_Latn":"Asturian","awa_Deva":"Awadhi","ayr_Latn":"Aymara","azb_Arab":"South Azerbaijani",
 "azj_Latn":"Azerbaijani","bak_Cyrl":"Bashkir","bam_Latn":"Bambara","ban_Latn":"Balinese",
 "bel_Cyrl":"Belarusian","bem_Latn":"Bemba","ben_Beng":"Bengali","bho_Deva":"Bhojpuri",
 "bjn_Arab":"Banjar (Arabic)","bjn_Latn":"Banjar (Latin)","bod_Tibt":"Tibetan","bos_Latn":"Bosnian",
 "bug_Latn":"Buginese","bul_Cyrl":"Bulgarian","cat_Latn":"Catalan","ceb_Latn":"Cebuano",
 "ces_Latn":"Czech","cjk_Latn":"Chokwe","ckb_Arab":"Central Kurdish","crh_Latn":"Crimean Tatar",
 "cym_Latn":"Welsh","dan_Latn":"Danish","deu_Latn":"German","dik_Latn":"Dinka","dyu_Latn":"Dyula",
 "dzo_Tibt":"Dzongkha","ell_Grek":"Greek","eng_Latn":"English","epo_Latn":"Esperanto",
 "est_Latn":"Estonian","eus_Latn":"Basque","ewe_Latn":"Ewe","fao_Latn":"Faroese",
 "pes_Arab":"Persian","fij_Latn":"Fijian","fin_Latn":"Finnish","fon_Latn":"Fon","fra_Latn":"French",
 "fur_Latn":"Friulian","fuv_Latn":"Nigerian Fulfulde","gla_Latn":"Scottish Gaelic","gle_Latn":"Irish",
 "glg_Latn":"Galician","grn_Latn":"Guarani","guj_Gujr":"Gujarati","hat_Latn":"Haitian Creole",
 "hau_Latn":"Hausa","heb_Hebr":"Hebrew","hin_Deva":"Hindi","hne_Deva":"Chhattisgarhi",
 "hrv_Latn":"Croatian","hun_Latn":"Hungarian","hye_Armn":"Armenian","ibo_Latn":"Igbo",
 "ilo_Latn":"Ilocano","ind_Latn":"Indonesian","isl_Latn":"Icelandic","ita_Latn":"Italian",
 "jav_Latn":"Javanese","jpn_Jpan":"Japanese","kab_Latn":"Kabyle","kac_Latn":"Jingpho",
 "kam_Latn":"Kamba","kan_Knda":"Kannada","kas_Arab":"Kashmiri (Arabic)","kas_Deva":"Kashmiri (Devanagari)",
 "kat_Geor":"Georgian","knc_Arab":"Kanuri (Arabic)","knc_Latn":"Kanuri (Latin)","kaz_Cyrl":"Kazakh",
 "kbp_Latn":"Kabiye","kea_Latn":"Kabuverdianu","khm_Khmr":"Khmer","kik_Latn":"Kikuyu",
 "kin_Latn":"Kinyarwanda","kir_Cyrl":"Kyrgyz","kmb_Latn":"Kimbundu","kon_Latn":"Kikongo",
 "kor_Hang":"Korean","kmr_Latn":"Northern Kurdish","lao_Laoo":"Lao","lvs_Latn":"Latvian",
 "lij_Latn":"Ligurian","lim_Latn":"Limburgish","lin_Latn":"Lingala","lit_Latn":"Lithuanian",
 "lmo_Latn":"Lombard","ltg_Latn":"Latgalian","ltz_Latn":"Luxembourgish","lua_Latn":"Luba-Kasai",
 "lug_Latn":"Ganda","luo_Latn":"Luo","lus_Latn":"Mizo","mag_Deva":"Magahi","mai_Deva":"Maithili",
 "mal_Mlym":"Malayalam","mar_Deva":"Marathi","min_Latn":"Minangkabau","mkd_Cyrl":"Macedonian",
 "plt_Latn":"Malagasy","mlt_Latn":"Maltese","mni_Beng":"Meitei","khk_Cyrl":"Mongolian",
 "mos_Latn":"Mossi","mri_Latn":"Maori","zsm_Latn":"Malay","mya_Mymr":"Burmese","nld_Latn":"Dutch",
 "nno_Latn":"Norwegian Nynorsk","nob_Latn":"Norwegian Bokmal","npi_Deva":"Nepali","nso_Latn":"Northern Sotho",
 "nus_Latn":"Nuer","nya_Latn":"Nyanja","oci_Latn":"Occitan","gaz_Latn":"Oromo","ory_Orya":"Odia",
 "pag_Latn":"Pangasinan","pan_Guru":"Punjabi","pap_Latn":"Papiamento","pol_Latn":"Polish",
 "por_Latn":"Portuguese","prs_Arab":"Dari","pbt_Arab":"Pashto","quy_Latn":"Quechua","ron_Latn":"Romanian",
 "run_Latn":"Rundi","rus_Cyrl":"Russian","sag_Latn":"Sango","san_Deva":"Sanskrit","sat_Beng":"Santali",
 "scn_Latn":"Sicilian","shn_Mymr":"Shan","sin_Sinh":"Sinhala","slk_Latn":"Slovak","slv_Latn":"Slovenian",
 "smo_Latn":"Samoan","sna_Latn":"Shona","snd_Arab":"Sindhi","som_Latn":"Somali","sot_Latn":"Southern Sotho",
 "spa_Latn":"Spanish","als_Latn":"Albanian","srd_Latn":"Sardinian","srp_Cyrl":"Serbian","ssw_Latn":"Swati",
 "sun_Latn":"Sundanese","swe_Latn":"Swedish","swh_Latn":"Swahili","szl_Latn":"Silesian","tam_Taml":"Tamil",
 "tat_Cyrl":"Tatar","tel_Telu":"Telugu","tgk_Cyrl":"Tajik","tgl_Latn":"Tagalog","tha_Thai":"Thai",
 "tir_Ethi":"Tigrinya","taq_Latn":"Tamasheq (Latin)","taq_Tfng":"Tamasheq (Tifinagh)","tpi_Latn":"Tok Pisin",
 "tsn_Latn":"Tswana","tso_Latn":"Tsonga","tuk_Latn":"Turkmen","tum_Latn":"Tumbuka","tur_Latn":"Turkish",
 "twi_Latn":"Twi","tzm_Tfng":"Tamazight","uig_Arab":"Uyghur","ukr_Cyrl":"Ukrainian","umb_Latn":"Umbundu",
 "urd_Arab":"Urdu","uzn_Latn":"Uzbek","vec_Latn":"Venetian","vie_Latn":"Vietnamese","war_Latn":"Waray",
 "wol_Latn":"Wolof","xho_Latn":"Xhosa","ydd_Hebr":"Yiddish","yor_Latn":"Yoruba","yue_Hant":"Cantonese",
 "zho_Hans":"Chinese (Simplified)","zho_Hant":"Chinese (Traditional)","zul_Latn":"Zulu",
}

# --- curated head: nice native + Turkish names, flags, exact codes --------
# (native, tr, emoji, google, libre, bcp47)
OVER = {
 "kmr_Latn":("Kurdî (Kurmancî)","Kürtçe (Kurmancî)","🟡","ku","ku","ku"),
 "ckb_Arab":("کوردیی ناوەندی (Soranî)","Kürtçe (Soranî)","🟢","ckb","ckb","ckb"),
 "tur_Latn":("Türkçe","Türkçe","🇹🇷","tr","tr","tr-TR"),
 "eng_Latn":("English","İngilizce","🇬🇧","en","en","en-US"),
 "arb_Arab":("العربية","Arapça","🇸🇦","ar","ar","ar-SA"),
 "pes_Arab":("فارسی","Farsça","🇮🇷","fa","fa","fa-IR"),
 "azj_Latn":("Azərbaycanca","Azerice","🇦🇿","az","az","az-AZ"),
 "deu_Latn":("Deutsch","Almanca","🇩🇪","de","de","de-DE"),
 "fra_Latn":("Français","Fransızca","🇫🇷","fr","fr","fr-FR"),
 "rus_Cyrl":("Русский","Rusça","🇷🇺","ru","ru","ru-RU"),
 "spa_Latn":("Español","İspanyolca","🇪🇸","es","es","es-ES"),
 "ita_Latn":("Italiano","İtalyanca","🇮🇹","it","it","it-IT"),
 "por_Latn":("Português","Portekizce","🇵🇹","pt","pt","pt-PT"),
 "nld_Latn":("Nederlands","Felemenkçe","🇳🇱","nl","nl","nl-NL"),
 "pol_Latn":("Polski","Lehçe","🇵🇱","pl","pl","pl-PL"),
 "ukr_Cyrl":("Українська","Ukraynaca","🇺🇦","uk","uk","uk-UA"),
 "ron_Latn":("Română","Rumence","🇷🇴","ro","ro","ro-RO"),
 "ell_Grek":("Ελληνικά","Yunanca","🇬🇷","el","el","el-GR"),
 "swe_Latn":("Svenska","İsveççe","🇸🇪","sv","sv","sv-SE"),
 "dan_Latn":("Dansk","Danca","🇩🇰","da","da","da-DK"),
 "nob_Latn":("Norsk Bokmål","Norveççe","🇳🇴","no","nb","nb-NO"),
 "fin_Latn":("Suomi","Fince","🇫🇮","fi","fi","fi-FI"),
 "ces_Latn":("Čeština","Çekçe","🇨🇿","cs","cs","cs-CZ"),
 "slk_Latn":("Slovenčina","Slovakça","🇸🇰","sk","sk","sk-SK"),
 "hun_Latn":("Magyar","Macarca","🇭🇺","hu","hu","hu-HU"),
 "bul_Cyrl":("Български","Bulgarca","🇧🇬","bg","bg","bg-BG"),
 "srp_Cyrl":("Српски","Sırpça","🇷🇸","sr","sr","sr-RS"),
 "hrv_Latn":("Hrvatski","Hırvatça","🇭🇷","hr","hr","hr-HR"),
 "heb_Hebr":("עברית","İbranice","🇮🇱","he","he","he-IL"),
 "tha_Thai":("ไทย","Tayca","🇹🇭","th","th","th-TH"),
 "vie_Latn":("Tiếng Việt","Vietnamca","🇻🇳","vi","vi","vi-VN"),
 "ind_Latn":("Bahasa Indonesia","Endonezce","🇮🇩","id","id","id-ID"),
 "zsm_Latn":("Bahasa Melayu","Malayca","🇲🇾","ms","ms","ms-MY"),
 "tgl_Latn":("Tagalog","Filipince (Tagalog)","🇵🇭","tl","tl","fil-PH"),
 "hin_Deva":("हिन्दी","Hintçe","🇮🇳","hi","hi","hi-IN"),
 "urd_Arab":("اردو","Urduca","🇵🇰","ur","ur","ur-PK"),
 "ben_Beng":("বাংলা","Bengalce","🇧🇩","bn","bn","bn-BD"),
 "jpn_Jpan":("日本語","Japonca","🇯🇵","ja","ja","ja-JP"),
 "kor_Hang":("한국어","Korece","🇰🇷","ko","ko","ko-KR"),
 "zho_Hans":("中文（简体）","Çince (Basit)","🇨🇳","zh-CN","zh","zh-CN"),
 "zho_Hant":("中文（繁體）","Çince (Geleneksel)","🇹🇼","zh-TW","zt","zh-TW"),
 "kaz_Cyrl":("Қазақша","Kazakça","🇰🇿","kk","kk","kk-KZ"),
 "uzn_Latn":("Oʻzbekcha","Özbekçe","🇺🇿","uz","uz","uz-UZ"),
 "tat_Cyrl":("Татарча","Tatarca","🏳️","tt","tt","tt-RU"),
 "tgk_Cyrl":("Тоҷикӣ","Tacikçe","🇹🇯","tg","tg","tg-TJ"),
 "uig_Arab":("ئۇيغۇرچە","Uygurca","🏳️","ug","ug","ug-CN"),
 "hye_Armn":("Հայերեն","Ermenice","🇦🇲","hy","hy","hy-AM"),
 "kat_Geor":("ქართული","Gürcüce","🇬🇪","ka","ka","ka-GE"),
 "swh_Latn":("Kiswahili","Svahili","🇰🇪","sw","sw","sw-KE"),
 "afr_Latn":("Afrikaans","Afrikaanca","🇿🇦","af","af","af-ZA"),
}
HEAD = list(OVER.keys())  # display order for the picker (curated first)

# --- ISO 639-3 (FLORES prefix) -> ISO 639-1 for the long tail ------------
ISO = {
 "afr":"af","aka":"ak","amh":"am","ara":"ar","arb":"ar","acm":"ar","acq":"ar","aeb":"ar","ajp":"ar",
 "apc":"ar","ars":"ar","ary":"ar","arz":"ar","asm":"as","ast":"ast","ayr":"ay","aze":"az","azj":"az",
 "azb":"az","bak":"ba","bam":"bm","bel":"be","ben":"bn","bho":"bho","bod":"bo","bos":"bs","bul":"bg",
 "cat":"ca","ceb":"ceb","ces":"cs","ckb":"ckb","crh":"crh","cym":"cy","dan":"da","deu":"de","dzo":"dz",
 "ell":"el","eng":"en","epo":"eo","est":"et","eus":"eu","ewe":"ee","fao":"fo","pes":"fa","prs":"fa",
 "fij":"fj","fin":"fi","fon":"fon","fra":"fr","fur":"fur","gla":"gd","gle":"ga","glg":"gl","grn":"gn",
 "guj":"gu","hat":"ht","hau":"ha","heb":"he","hin":"hi","hne":"hne","hrv":"hr","hun":"hu","hye":"hy",
 "ibo":"ig","ilo":"ilo","ind":"id","isl":"is","ita":"it","jav":"jv","jpn":"ja","kab":"kab","kan":"kn",
 "kas":"ks","kat":"ka","kaz":"kk","khm":"km","kik":"ki","kin":"rw","kir":"ky","kon":"kg","kor":"ko",
 "kmr":"ku","lao":"lo","lvs":"lv","lij":"lij","lim":"li","lin":"ln","lit":"lt","ltz":"lb","lug":"lg",
 "luo":"luo","mag":"mag","mai":"mai","mal":"ml","mar":"mr","mkd":"mk","plt":"mg","mlt":"mt","khk":"mn",
 "mri":"mi","zsm":"ms","mya":"my","nld":"nl","nno":"nn","nob":"nb","npi":"ne","nso":"nso","nya":"ny",
 "oci":"oc","gaz":"om","ory":"or","pan":"pa","pap":"pap","pol":"pl","por":"pt","pbt":"ps","quy":"qu",
 "ron":"ro","run":"rn","rus":"ru","sag":"sg","san":"sa","sat":"sat","scn":"scn","shn":"shn","sin":"si",
 "slk":"sk","slv":"sl","smo":"sm","sna":"sn","snd":"sd","som":"so","sot":"st","spa":"es","als":"sq",
 "srd":"sc","srp":"sr","ssw":"ss","sun":"su","swe":"sv","swh":"sw","tam":"ta","tat":"tt","tel":"te",
 "tgk":"tg","tgl":"tl","tha":"th","tir":"ti","tpi":"tpi","tsn":"tn","tso":"ts","tuk":"tk","tum":"tum",
 "tur":"tr","twi":"tw","tzm":"tzm","uig":"ug","ukr":"uk","urd":"ur","uzn":"uz","vie":"vi","wol":"wo",
 "xho":"xh","ydd":"yi","yor":"yo","yue":"yue","zho":"zh","zul":"zu",
}
def code2(flores):
    return ISO.get(flores[:3], flores[:3])

# --- assemble ordered rows: curated head first, then the rest A-Z ---------
tail = sorted([c for c in IDS if c not in OVER], key=lambda c: EN.get(c, c).lower())
ORDER = HEAD + tail

rows = []
for c in ORDER:
    if c not in IDS:
        continue
    if c in OVER:
        native, tr, emoji, g, lt, bcp = OVER[c]
    else:
        native = EN.get(c, c)
        tr = native
        emoji = "🌐"
        g = lt = code2(c)
        bcp = code2(c)
    rows.append((c, IDS[c], native, tr, emoji, g, lt, bcp))

def jsstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'

# ---------------- emit i18n.js block ----------------
tl = ["// Translation languages (FLORES-200 codes) and their display names.",
      "// AUTO-GENERATED by tools/gen_langs.py — all ~200 NLLB-200 languages.",
      "const TRANSLATE_LANGS = ["]
for c, _id, native, tr, emoji, g, lt, bcp in rows:
    tl.append("  { code: %s, name: %s }," % (jsstr(c), jsstr(native)))
tl.append("];")
tl.append("")
tl.append("// FLORES-200 -> BCP-47 tag for Web Speech / online-TTS voice selection.")
tl.append("const SPEECH_LANG = {")
for c, _id, native, tr, emoji, g, lt, bcp in rows:
    tl.append("  %s: %s," % (c, jsstr(bcp)))
tl.append("};")
i18n_block = "\n".join(tl) + "\n"

i18n_path = os.path.join(ROOT, "desktop", "src", "i18n.js")
src = io.open(i18n_path, encoding="utf-8").read()
marker = "// Translation languages (FLORES-200 codes)"
head = src[: src.index(marker)]
io.open(i18n_path, "w", encoding="utf-8", newline="\n").write(head + i18n_block)

# ---------------- emit lang.rs ----------------
def rust_match(fn, getter, ret_ty, body_rows, fallback):
    out = [fn, "    match code {"]
    for c, _id, native, tr, emoji, g, lt, bcp in body_rows:
        out.append('        "%s" => %s,' % (c, getter(c, _id, g, lt)))
    out.append("        %s" % fallback)
    out.append("    }")
    out.append("}")
    return "\n".join(out)

lang_rs = '''//! Central language-code mapping — AUTO-GENERATED by tools/gen_langs.py.
//!
//! FLORES-200 codes (e.g. `kmr_Latn`) are mapped to LibreTranslate ISO codes,
//! Google Translate codes, and NLLB language-token ids. Token ids come straight
//! from the model's tokenizer.json, so they are authoritative.

/// FLORES-200 -> LibreTranslate ISO code.
pub fn flores_to_libre(code: &str) -> &\'static str {
    match code {
'''
for c, _id, native, tr, emoji, g, lt, bcp in rows:
    lang_rs += '        "%s" => "%s",\n' % (c, lt)
lang_rs += '''        _ => "en",
    }
}

/// FLORES-200 -> Google Translate (free `gtx`) code. Best Kurdish coverage.
pub fn flores_to_google(code: &str) -> &\'static str {
    match code {
'''
for c, _id, native, tr, emoji, g, lt, bcp in rows:
    lang_rs += '        "%s" => "%s",\n' % (c, g)
lang_rs += '''        _ => "en",
    }
}

/// NLLB language-token id for a FLORES-200 code (offline engine).
#[cfg_attr(not(feature = "offline"), allow(dead_code))]
pub fn flores_to_nllb_id(code: &str) -> Option<i64> {
    let id = match code {
'''
for c, _id, native, tr, emoji, g, lt, bcp in rows:
    lang_rs += '        "%s" => %d,\n' % (c, _id)
lang_rs += '''        _ => return None,
    };
    Some(id)
}
'''
lang_path = os.path.join(ROOT, "desktop", "src-tauri", "src", "lang.rs")
io.open(lang_path, "w", encoding="utf-8", newline="\n").write(lang_rs)

# ---------------- emit Languages.kt ----------------
def ktstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'

kt = '''package dev.wergir.translate.nllb

/**
 * A translation language identified by its NLLB-200 (FLORES-200) code.
 * AUTO-GENERATED by tools/gen_langs.py — all ~200 NLLB-200 languages.
 */
data class Language(
    val token: String,
    val tokenId: Int,
    /** Native (or English) display name. */
    val nativeName: String,
    /** Turkish display name. */
    val trName: String,
    val emoji: String,
)

object Languages {
    val all: List<Language> = listOf(
'''
for c, _id, native, tr, emoji, g, lt, bcp in rows:
    kt += "        Language(%s, %d, %s, %s, %s),\n" % (ktstr(c), _id, ktstr(native), ktstr(tr), ktstr(emoji))
kt += '''    )

    val byToken: Map<String, Language> = all.associateBy { it.token }

    fun default(token: String): Language = byToken[token] ?: all.first()
}
'''
kt_path = os.path.join(ROOT, "app", "src", "main", "java", "dev", "wergir", "translate", "nllb", "Languages.kt")
io.open(kt_path, "w", encoding="utf-8", newline="\n").write(kt)

print("languages:", len(rows))
print("wrote:", i18n_path)
print("wrote:", lang_path)
print("wrote:", kt_path)
