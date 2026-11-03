"""Deterministic metadata ranking; never infer audio features from a search response."""

import re
import unicodedata
from itertools import zip_longest

from .catalog import MOOD_TERMS, query_for


def normalized(value):
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def rank_candidates(groups, intent):
    # Preserve provider relevance for ties, interleaving providers in configured order.
    pool = [item for row in zip_longest(*groups) for item in row if item is not None]
    wanted = {normalized(tag) for tag in intent.musicTags}
    if not wanted and not intent.text:
        wanted.add(MOOD_TERMS[intent.targetMood])
    terms = set(re.findall(r"\w+", normalized(query_for(intent))))
    ranked, seen = [], set()
    for position, candidate in enumerate(pool):
        track = candidate.track
        if track.ref.key in seen:
            continue
        seen.add(track.ref.key)
        matches = sorted(wanted & {normalized(tag) for tag in track.tags})
        metadata = set(re.findall(r"\w+", normalized(track.title + " " + track.artist)))
        text_matches = sorted(terms & metadata)
        # Deep copy: repeated queries must not append explanations to cached provider objects.
        candidate = type(candidate)(
            track.model_copy(deep=True), candidate.audio, candidate.license_value
        )
        candidate.track.recommendationReason += (
            f" 排序依据：目录标签匹配 {len(matches)} 项，标题/艺人词匹配 {len(text_matches)} 项；"
            "并列保留来源检索顺序，不代表听感或音频分析。"
        )
        ranked.append(((-len(matches), -len(text_matches), position), candidate))
    return [candidate for _, candidate in sorted(ranked, key=lambda item: item[0])]
