import huggingface_hub
import os

repo_id = "onnx-community/Voxtral-Mini-3B-2507-ONNX"
local_dir = "voxtral_onnx"

# Files to download (fp16 for embed tokens as requested by the model)
files = [
    "onnx/embed_tokens_fp16.onnx",
    "onnx/embed_tokens_fp16.onnx_data"
]

print(f"Downloading {len(files)} files into {local_dir}/ ...")
for file in files:
    huggingface_hub.hf_hub_download(
        repo_id=repo_id,
        filename=file,
        local_dir=local_dir,
        local_dir_use_symlinks=False
    )
    print(f"Downloaded: {file}")

print("Download complete!")
