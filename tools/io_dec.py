import onnxruntime as ort
s = ort.InferenceSession(r"C:\Users\harun\Desktop\wergir\tools\onnx\decoder_merged.onnx", providers=["CPUExecutionProvider"])
print("=== DECODER INPUTS ===")
for i in s.get_inputs(): print(i.name, i.shape, i.type)
print("=== DECODER OUTPUTS ===")
for o in s.get_outputs(): print(o.name, o.shape, o.type)
