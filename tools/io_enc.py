import onnxruntime as ort
s = ort.InferenceSession(r"C:\Users\harun\Desktop\wergir\tools\onnx\encoder.onnx", providers=["CPUExecutionProvider"])
print("=== ENCODER INPUTS ===")
for i in s.get_inputs(): print(i.name, i.shape, i.type)
print("=== ENCODER OUTPUTS ===")
for o in s.get_outputs(): print(o.name, o.shape, o.type)
