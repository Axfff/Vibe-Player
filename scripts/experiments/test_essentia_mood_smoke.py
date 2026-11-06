"""Contract tests for the exploratory audio CLI, without model downloads.

Run: python -m unittest discover -s scripts/experiments -p 'test_*.py'
These tests stub the inference runtime; they do not establish model accuracy.
"""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

import numpy as np


SCRIPT = Path(__file__).with_name("essentia_mood_smoke.py")


class MoodSmokeContractTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.model_dir = Path(self.tmp.name)
        labels = [f"label_{index}" for index in range(56)]
        (self.model_dir / "mtg_jamendo_moodtheme-discogs-effnet-1.json").write_text(
            json.dumps({"classes": labels}), encoding="utf-8"
        )
        self.embeddings = np.ones((2, 1280), dtype=np.float32)
        self.predictions = np.zeros((2, 56), dtype=np.float32)
        self.predictions[:, 0] = [0.2, 0.6]
        self.predictions[:, 1] = [0.5, 0.5]
        self.loader_calls = []

    def run_cli(self, paths):
        standard = types.ModuleType("essentia.standard")

        def loader(**kwargs):
            self.loader_calls.append(kwargs)
            if Path(kwargs["filename"]).name == "missing.wav":
                raise OSError("audio unavailable")
            return lambda: np.zeros(32000, dtype=np.float32)

        standard.MonoLoader = loader
        standard.TensorflowPredictEffnetDiscogs = lambda **kwargs: lambda audio: self.embeddings
        standard.TensorflowPredict2D = lambda **kwargs: lambda embeddings: self.predictions
        essentia = types.ModuleType("essentia")
        essentia.standard = standard
        spec = importlib.util.spec_from_file_location("mood_smoke_under_test", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        output = io.StringIO()
        argv = [str(SCRIPT), "--model-dir", str(self.model_dir), *paths]
        with patch.dict(sys.modules, {"essentia": essentia, "essentia.standard": standard}):
            spec.loader.exec_module(module)
            with patch.object(sys, "argv", argv), contextlib.redirect_stdout(output):
                status = module.main()
        return status, json.loads(output.getvalue())

    def test_track_scores_are_averaged_and_ranked_with_metadata_labels(self):
        status, output = self.run_cli(["song.wav"])
        self.assertEqual(status, 0)
        self.assertEqual(self.loader_calls[0]["sampleRate"], 16000)
        result = output["results"][0]
        self.assertEqual(result["duration_seconds"], 2.0)
        self.assertEqual(result["embedding_shape"], [2, 1280])
        self.assertEqual(result["prediction_shape"], [2, 56])
        scores = result["mean_scores_descending"]
        self.assertEqual([item["label"] for item in scores[:2]], ["label_1", "label_0"])
        self.assertAlmostEqual(scores[0]["score"], 0.5)
        self.assertAlmostEqual(scores[1]["score"], 0.4)
        self.assertEqual(len(scores), 56)

    def test_one_missing_file_is_reported_and_does_not_hide_other_results(self):
        status, output = self.run_cli(["missing.wav", "song.wav"])
        self.assertEqual(status, 1)
        self.assertIn("audio unavailable", output["results"][0]["error"])
        self.assertEqual(output["results"][1]["audio"], "song.wav")
        self.assertEqual(len(output["results"][1]["mean_scores_descending"]), 56)

    def test_invalid_embeddings_are_reported_instead_of_used(self):
        self.embeddings = np.zeros((2, 1279), dtype=np.float32)
        status, output = self.run_cli(["song.wav"])
        self.assertEqual(status, 1)
        self.assertIn("Invalid embeddings", output["results"][0]["error"])
        self.assertNotIn("mean_scores_descending", output["results"][0])

    def test_nonfinite_predictions_are_reported_instead_of_ranked(self):
        self.predictions[0, 0] = np.nan
        status, output = self.run_cli(["song.wav"])
        self.assertEqual(status, 1)
        self.assertIn("Invalid predictions", output["results"][0]["error"])
        self.assertNotIn("mean_scores_descending", output["results"][0])


if __name__ == "__main__":
    unittest.main()
