"""Checks for the exploratory, manually annotated caption-ranking bridge."""

import unittest

from label_caption_ranking import VIBES, rank


def result(**overrides):
    values = {label: 0.0 for labels in VIBES.values() for label in labels}
    values.update(overrides)
    return {"audio": "example", "mean_scores_descending": [
        {"label": label, "score": score} for label, score in values.items()
    ]}


class CaptionRankingTest(unittest.TestCase):
    def setUp(self):
        self.candidates = [
            {"id": "shore", "description": "Quiet shore", "human_assigned_vibes": ["peaceful"]},
            {"id": "mountain", "description": "Mountain hike", "human_assigned_vibes": ["adventurous"]},
            {"id": "receipt", "description": "Receipt", "human_assigned_vibes": []},
        ]

    def test_music_signal_changes_candidate_order(self):
        adventure = rank(result(adventure=0.9), self.candidates, True)
        calm = rank(result(relaxing=0.9, calm=0.9, meditative=0.9), self.candidates, True)
        self.assertEqual(adventure["candidates"][0]["id"], "mountain")
        self.assertEqual(calm["candidates"][0]["id"], "shore")
        self.assertIsNone(calm["candidates"][-1]["score"])

    def test_explicit_unusable_signal_and_analysis_failure_abstain(self):
        self.assertEqual(rank(result(adventure=0.9), self.candidates, False)["status"], "manual fallback")
        self.assertEqual(rank({"error": "missing audio"}, self.candidates, True)["status"], "manual fallback")

    def test_no_supported_candidate_abstains(self):
        self.assertEqual(rank(result(adventure=0.9), self.candidates[-1:], True)["status"], "manual fallback")

    def test_missing_or_unknown_labels_are_not_silently_accepted(self):
        with self.assertRaises(ValueError):
            rank({"mean_scores_descending": []}, self.candidates, True)
        with self.assertRaises(ValueError):
            rank(result(), [{"id": "bad", "human_assigned_vibes": ["unknown"]}], True)

    def test_nonfinite_and_out_of_range_scores_are_rejected(self):
        for value in (float("nan"), float("inf"), -0.1, 1.1):
            with self.subTest(value=value), self.assertRaises(ValueError):
                rank(result(adventure=value), self.candidates, True)


if __name__ == "__main__":
    unittest.main()
