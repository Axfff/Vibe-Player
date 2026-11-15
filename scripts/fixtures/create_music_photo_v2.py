"""Create a NEW fixture version for #51, preserving frozen v1 byte for byte.

No relevance label, media byte, membership or representative track changes.
This is a development-set repair, not a new held-out accuracy benchmark.
Requires Python 3.12+ and requirements.txt. Refuses an existing destination.
"""
import argparse
import re
import shutil
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw
import fixture_tools as f


def create(source, destination):
    source=source.resolve(); destination=destination.resolve()
    if destination.exists() or source in destination.parents or destination in source.parents:
        raise ValueError('Supply a new, separate fixture directory')
    f.validate(source,decode=False)
    original=f.sha(source/'checksums.sha256')
    shutil.copytree(source,destination,ignore=shutil.ignore_patterns('freeze.json','checksums.sha256'))
    manifest=f.read_json(destination/'manifest.json'); manifest['dataset_id']='music-photo-v2'
    notice='Bien Bacarra Jr'
    for asset in manifest['assets']:
        if asset['asset_id']=='img_0040':
            asset['copyright_notice']=notice
            asset['attribution'] += ' Original JPEG copyright notice: '+notice+'. Original JPEG author: Bien Orellana Bacarra Jr.'
            evidence=f.read_json(destination/'evidence/licenses/img_0040.json')
            evidence['asset']=asset
            evidence['notice_review']={'date':'2026-11-10','source':asset['source_page']+'#Metadata',
                'copyright_notice':notice,'author_metadata':'Bien Orellana Bacarra Jr'}
            f.write_json(destination/'evidence/licenses/img_0040.json',evidence)
    f.write_json(destination/'manifest.json',manifest)
    attribution=destination/'ATTRIBUTION.md'
    attribution.write_text(attribution.read_text(encoding='utf8').replace(
        '- Author / 作者: Bien02', '- Author / 作者: Bien02 (Commons uploader); original JPEG author: Bien Orellana Bacarra Jr\n- Original JPEG copyright notice: Bien Bacarra Jr'),encoding='utf8')
    for n,group in enumerate(f.PLAYLISTS,1):
        path=destination/f'candidates/case_{n:02}.json'; case=f.read_json(path)
        byid={p['asset_id']:p for p in case['photos']}
        ids=f.independent_order(byid,n); case['photos']=[byid[id] for id in ids]; f.write_json(path,case)
        for folder in ('galleries','evaluation'):
            page=destination/f'{folder}/{group}.html'; content=page.read_text(encoding='utf8')
            figures=re.findall(r'<figure>.*?</figure>',content,flags=re.S)
            indexed={re.search(r'alt="(img_\d+)"',figure)[1]:figure for figure in figures}
            assert set(indexed)==set(ids)
            first=content.index('<figure>'); last=content.rindex('</figure>')+len('</figure>')
            page.write_text(content[:first]+''.join(indexed[id] for id in ids)+content[last:],encoding='utf8')
        sheet=Image.new('RGB',(1280,5*210),'#f4f3ef'); draw=ImageDraw.Draw(sheet)
        for i,id in enumerate(ids):
            with Image.open(destination/byid[id]['path']) as picture:
                thumb=ImageOps.contain(picture.convert('RGB'),(300,175))
            x=(i%4)*320; y=(i//4)*210
            sheet.paste(thumb,(x+(320-thumb.width)//2,y+(175-thumb.height)//2))
            draw.text((x+10,y+180),id,fill='#212b2c')
        sheet.save(destination/f'galleries/{group}.jpg',quality=90)
    protocol=f.read_json(source/'evaluation/protocol.json')
    protocol.pop('seed'); protocol.pop('seed_rule')
    protocol['ordering']='SHA-256 ASCII music-photo-v2:{case_number}:{neutral_photo_id}; ascending digest; independent of annotation row order'
    protocol['ranking_run']='External; supply actual source SHA and zero to five unique in-scope IDs'
    protocol['ranking_reason']='Labels remain provisional development labels; models have already been compared on v1.'
    f.write_json(destination/'evaluation/protocol.json',protocol)
    (destination/'README.md').write_text('''# Music/photo fixtures v2

Repairs the input-order and offline-attribution findings in Issue #51. Requires **Python 3.12+** (pinned PyAV requires it).

All 20 audio files, 33 photos, 80 case memberships, representative tracks, and both evaluation CSV files are copied unchanged from [frozen v1](../music-photo-v1/README.md). This is a development set already seen during model work, not a fresh held-out study. Provisional labels still need human listening and visual review.

Candidate order is SHA-256 of `music-photo-v2:{case_number}:{neutral_photo_id}`, sorted by digest. It does not use the order of relevance rows. Reversing a published shuffle cannot reconstruct the annotation row order. No label or source metadata goes into the neutral inference JSON. The HTML galleries and contact sheets use the new order.

The original Mayon JPEG copyright notice **Bien Bacarra Jr** and author metadata **Bien Orellana Bacarra Jr** are preserved in the manifest, per-item evidence and bundled ATTRIBUTION. Commons uploader Bien02 and the original CC BY 4.0 terms remain credited. Materialized exports include these offline records.

The old v1 package and its freeze/checksums are untouched. Copied evidence dated November 7 describes historical acquisition/decoding; it does not claim a v2 model result. The new freeze records the input-order repair and unchanged label hashes.

From the repository root, in a Python 3.12+ virtual environment:

```text
python -m pip install -r scripts/fixtures/requirements.txt
python -m unittest discover -s scripts/fixtures/tests -v
python scripts/fixtures/validate_music_photo_fixtures.py --output ../fixture-v2-validation.json
python scripts/fixtures/materialize_music_photo_fixtures.py --output ../music-photo-v2-export
python scripts/fixtures/evaluate_rankings.py ../actual-results.json --output ../actual-evaluation.json
```

Evaluation accepts 0–5 distinct in-scope results and always divides relevant returns by five for P@5. It reports returned count and precision among returned photos separately; the latter is null for an empty result. Missing slots are never silently discarded. Models, actual rankings, user media and APKs are not included in this package.
''',encoding='utf8')
    f.write_json(destination/'evaluation/freeze.json',{'version':2,'frozen_on':'2026-11-10',
        'stage':'Input-order repair of previously evaluated development set; not held-out',
        'parent_dataset':'music-photo-v1','parent_checksums_sha256':original,
        'changes':['Independent candidate order and corresponding galleries','Offline original JPEG copyright/author notice','Python minimum and short-list evaluation documentation'],
        'files':f.freeze_members(destination)})
    f.write_checksums(destination)
    assert f.sha(source/'checksums.sha256')==original
    for csv in ('expected-relevance.csv','per-track-overrides.csv'):
        assert f.sha(source/'evaluation'/csv)==f.sha(destination/'evaluation'/csv)
    f.validate(destination,decode=False)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',type=Path,default=f.ROOT.with_name('music-photo-v1'))
    p.add_argument('--output',type=Path,default=f.ROOT)
    a=p.parse_args();create(a.source,a.output)
