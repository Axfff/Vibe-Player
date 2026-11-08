"""Export the pinned TinyCLIP image tower and fixed scene text prototypes.

No photos, music, filenames, or evaluation labels are inputs to this exporter.
Download the author checkpoint at REVISION into --source; no remote code is run.
Use Python 3.12 and requirements.txt (CPU torch wheel is sufficient).
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from transformers import CLIPModel, CLIPTokenizerFast

REVISION = "a2a8c6eaa2549ad66eb7c31b85022bf58273a26c"
CHECKPOINT_SHA256 = "9339ee3d736344d0ddcaa6c03edc9f89688f08caaea5401220885233da726fcc"


class SceneModel(torch.nn.Module):
    def __init__(self, model, prototypes):
        super().__init__()
        self.vision = model.vision_model
        self.projection = model.visual_projection
        self.register_buffer("scene_prototypes", prototypes.T.contiguous())

    def forward(self, pixels):
        image = self.projection(self.vision(pixel_values=pixels)[1])
        image = image / image.norm(dim=-1, keepdim=True)
        return image @ self.scene_prototypes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(Path(__file__).with_name("source-manifest.json").read_text(encoding="utf-8"))
    assert manifest["revision"] == REVISION
    for item in manifest["files"]:
        path = args.source / item["name"]
        assert path.stat().st_size == item["size"] and hashlib.sha256(path.read_bytes()).hexdigest() == item["sha256"], item["name"]
    assert hashlib.sha256((args.source / "model.safetensors").read_bytes()).hexdigest() == CHECKPOINT_SHA256
    vocabulary_path = Path(__file__).with_name("scene-vocabulary-v2.json")
    vocabulary = json.loads(vocabulary_path.read_text(encoding="utf-8"))
    policy = Path(__file__).resolve().parents[2] / "project/app/src/main/java/com/iven/musicplayergo/photos/PhotoScenePolicy.kt"
    kotlin_names = re.findall(r'"([^"]+)" to "([^"]+)"', policy.read_text(encoding="utf-8").split("private val families", 1)[0])
    assert kotlin_names == [(scene[0], scene[2]) for scene in vocabulary["classes"]], "Android class order differs from exported model"
    torch.set_num_threads(1)
    model = CLIPModel.from_pretrained(args.source, local_files_only=True, attn_implementation="eager").eval()
    tokenizer = CLIPTokenizerFast.from_pretrained(args.source, local_files_only=True)
    prompts = [template.format(scene[1]) for scene in vocabulary["classes"] for template in vocabulary["templates"]]
    with torch.no_grad():
        text = model.get_text_features(**tokenizer(prompts, padding=True, return_tensors="pt"))
        text = text / text.norm(dim=-1, keepdim=True)
        text = text.reshape(len(vocabulary["classes"]), len(vocabulary["templates"]), -1).mean(dim=1)
        text = text / text.norm(dim=-1, keepdim=True)
        export = SceneModel(model, text).eval()
        torch.manual_seed(0)
        example = torch.randn(2, 3, 224, 224)
        expected = export(example).numpy()
        torch.onnx.export(export, example, args.output, input_names=["pixels"], output_names=["scene_cosines"],
                          opset_version=17, do_constant_folding=True, dynamo=False)
    graph = onnx.load(args.output)
    onnx.checker.check_model(graph)
    session = ort.InferenceSession(str(args.output), providers=["CPUExecutionProvider"])
    actual = session.run(None, {"pixels": example.numpy()})[0]
    np.testing.assert_allclose(expected, actual, atol=1e-5, rtol=1e-5)
    receipt = {"upstream": "wkcn/TinyCLIP-ViT-8M-16-Text-3M-YFCC15M", "revision": REVISION,
               "checkpoint_sha256": CHECKPOINT_SHA256,
               "vocabulary_sha256": hashlib.sha256(vocabulary_path.read_bytes()).hexdigest(),
               "filename": args.output.name, "bytes": args.output.stat().st_size,
               "sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
               "input_shape": [2, 3, 224, 224], "output_shape": list(actual.shape),
               "onnx_opset": 17, "onnx_ir": graph.ir_version,
               "max_export_difference": float(abs(expected-actual).max()),
               "torch": torch.__version__, "classes": vocabulary["classes"]}
    args.output.with_suffix(".json").write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in receipt.items() if k != "classes"}, indent=2))


if __name__ == "__main__":
    main()
