import json
d = json.load(open(r"C:\Users\harun\Desktop\wergir\tools\tokenizer.json", encoding="utf-8"))
m = d["model"]
merges = m["merges"]; v = m["vocab"]
print("MERGES TYPE:", type(merges[0]), "FIRST3:", [repr(x) for x in merges[:3]])
print("VOCAB FIRST 8:", [(repr(k),i) for k,i in list(v.items())[:8]])
for t in ["<s>","</s>","<pad>","<unk>","eng_Latn","tur_Latn","kmr_Latn","ckb_Arab","arb_Arab","azj_Latn","deu_Latn","fra_Latn","rus_Cyrl","pes_Arab","spa_Latn"," kurdî"]:
    print("id["+t+"] =", v.get(t))
print("GEN:", open(r"C:\Users\harun\Desktop\wergir\tools\generation_config.json", encoding="utf-8").read())
print("CFG:", open(r"C:\Users\harun\Desktop\wergir\tools\config.json", encoding="utf-8").read())
