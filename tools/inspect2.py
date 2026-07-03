import json
d = json.load(open(r"C:\Users\harun\Desktop\wergir\tools\tokenizer.json", encoding="utf-8"))
m = d["model"]
for k in ["dropout","unk_token","continuing_subword_prefix","end_of_word_suffix","fuse_unk","byte_fallback"]:
    print(k, "=", m.get(k))
v = m["vocab"]; merges = m["merges"]
print("MERGES LEN:", len(merges), "FIRST:", merges[:3], "TYPE:", type(merges[0]))
items = list(v.items())
print("VOCAB FIRST 8:", items[:8])
# byte tokens
print("byte <0x41>:", v.get("<0x41>"))
# special / lang ids
for t in ["<s>","</s>","<pad>","<unk>","eng_Latn","tur_Latn","kmr_Latn","ckb_Arab","arb_Arab","azj_Latn","deu_Latn","fra_Latn","rus_Cyrl","fas_Arab","spa_Latn"]:
    print(f"id[{t}] =", v.get(t))
print("---- configs ----")
print("GEN:", open(r"C:\Users\harun\Desktop\wergir\tools\generation_config.json").read())
print("CFG:", open(r"C:\Users\harun\Desktop\wergir\tools\config.json").read())
