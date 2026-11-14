"""Compare actual external Top-5 IDs against frozen provisional labels, offline.

This evaluates supplied results; it does not infer mood or rank photographs.
Prediction JSON: {"source_sha": "...", "cases": [{"case_id":"case_01",
"audio_id":"audio_0001", "photo_ids":["img_0001", ...]}]}. Supply all four cases.
"""
import argparse
import csv
import json
import re
from pathlib import Path
from fixture_tools import ROOT, PLAYLISTS, read_json, validate, write_json


def evaluate(root, predictions):
    validate(root, decode=False)
    if not re.fullmatch('[0-9a-f]{40}', predictions.get('source_sha', '')):
        raise ValueError('Actual recommender source SHA is required')
    cases=predictions['cases']
    if len(cases)!=4 or {c['case_id'] for c in cases}!={f'case_{i:02}' for i in range(1,5)}:
        raise ValueError('Exactly one actual result for each of four cases is required')
    with (root/'evaluation/expected-relevance.csv').open(encoding='utf8',newline='') as f:
        labels=list(csv.DictReader(f))
    result=[]
    for case in cases:
        index=int(case['case_id'][-2:]); group=PLAYLISTS[index-1]
        inputs=read_json(root/f'candidates/{case["case_id"]}.json')
        if case['audio_id']!=inputs['audio']['asset_id']:
            raise ValueError('This evaluation entry expects the frozen representative track')
        ids=case['photo_ids']
        if not isinstance(ids,list) or len(ids)>5 or any(not isinstance(x,str) for x in ids) or len(set(ids))!=len(ids) or not set(ids)<= {p['asset_id'] for p in inputs['photos']}:
            raise ValueError('Top-5 must contain zero to five distinct in-scope candidate IDs')
        rows={x['photo_id']:x for x in labels if x['playlist_id']==group}
        precision=sum(int(rows[x]['expected_relevance'])>=2 for x in ids)/5
        result.append({'case_id':case['case_id'],'audio_id':case['audio_id'],'top_5':ids,
                       'returned':len(ids),
                       'precision_at_returned':precision*5/len(ids) if ids else None,
                       'provisional_precision_at_5':precision,
                       'hard_distractors_at_5':sum(rows[x]['role']=='hard_distractor' for x in ids),
                       'uniform_random_expected_precision_at_5':sum(int(x['expected_relevance'])>=2 for x in rows.values())/20,
                       'uniform_random_expected_hard_distractors_at_5':1.0})
    return {'source_sha':predictions['source_sha'],'labels':'provisional, not human ground truth',
            'freeze':read_json(root/'evaluation/freeze.json'),'cases':result,
            'limitation':'Four small, manually curated groups do not demonstrate generalization.'}


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('predictions',type=Path)
    p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();write_json(a.output,evaluate(a.root,read_json(a.predictions)))
