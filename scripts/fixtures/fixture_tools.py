"""Portable, fail-closed tools for the locked music/photo fixture package.

No production code, model, Android storage, or network service is invoked by
validation/materialization. Only the download command accesses the network.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import http.client
import io
import json
import math
import os
from pathlib import Path, PurePosixPath
import random
import re
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[2] / 'test-data/music-photo-v1'
PLAYLISTS = ('bright', 'calm', 'melancholic', 'energetic')
ROLES = {'strong_match': 8, 'acceptable_match': 4, 'hard_distractor': 4, 'mismatch': 4}
LICENSES = {
    'CC0 1.0': 'https://creativecommons.org/publicdomain/zero/1.0/',
    'CC BY 3.0': 'https://creativecommons.org/licenses/by/3.0/',
    'CC BY 4.0': 'https://creativecommons.org/licenses/by/4.0/',
}
SEED = 50027204


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf8'))


def safe_path(root, relative):
    """No traversal, drive letters, URI, backslashes, or escaping symlinks."""
    if not isinstance(relative, str) or not relative or '\\' in relative or ':' in relative:
        raise ValueError(f'Not a portable relative path: {relative!r}')
    p = PurePosixPath(relative)
    if p.is_absolute() or any(x in ('', '.', '..') for x in relative.split('/')):
        raise ValueError(f'Unsafe relative path: {relative!r}')
    root = Path(root).resolve()
    result = root.joinpath(*p.parts).resolve()
    if not result.is_relative_to(root):
        raise ValueError(f'Path escapes fixture root: {relative!r}')
    return result


def verify_license(asset):
    required = ('asset_id', 'title', 'creator', 'source_page', 'download_url',
                'license', 'license_url', 'verified_on', 'attribution',
                'modifications', 'original_sha256', 'sha256')
    if any(not asset.get(key) for key in required):
        raise ValueError(f'Missing provenance in {asset.get("asset_id")}')
    if LICENSES.get(asset['license']) != asset['license_url']:
        raise ValueError(f'Unapproved or inconsistent license: {asset["asset_id"]}')
    for key in ('sha256', 'original_sha256'):
        if not re.fullmatch('[0-9a-f]{64}', asset[key]):
            raise ValueError(f'Invalid {key}: {asset["asset_id"]}')
    for key in ('source_page', 'download_url'):
        u = urllib.parse.urlsplit(asset[key])
        if u.scheme != 'https' or not u.hostname or u.username or u.password:
            raise ValueError(f'Invalid public HTTPS source: {asset["asset_id"]}')


def mixed_order(ids, case_number):
    ordered = list(ids)
    if len(set(ordered)) != len(ordered):
        raise ValueError('Duplicate candidates before shuffle')
    random.Random(SEED + case_number).shuffle(ordered)
    return ordered


def check_input(record, assets):
    """Strict whitelist. Unknown keys are errors, including expected labels."""
    if set(record) != {'case_id', 'audio', 'photos'} or not re.fullmatch(r'case_\d{2}', record['case_id']):
        raise ValueError('Inference input must contain only neutral case_id, audio, photos')
    for entry, kind in [(record['audio'], 'audio')] + [(p, 'image') for p in record['photos']]:
        if set(entry) != {'asset_id', 'path'}:
            raise ValueError('Answer/source metadata leaked into inference input')
        asset = assets.get(entry['asset_id'])
        if not asset or asset['kind'] != kind or entry['path'] != asset['path']:
            raise ValueError('Unknown or mismatched inference asset')
    ids = [p['asset_id'] for p in record['photos']]
    if len(ids) != 20 or len(set(ids)) != 20:
        raise ValueError('Each candidate library requires 20 distinct photographs')


def decode_image(path):
    from PIL import Image
    with Image.open(path) as image:
        if image.format != 'JPEG':
            raise ValueError(f'Not a JPEG: {path.name}')
        image.load()
        if min(image.size) < 512 or max(image.size) > 1600 or image.mode != 'RGB':
            raise ValueError(f'Invalid image dimensions/mode: {path.name}')
        if image.getexif() or image.info.get('exif') or image.info.get('xmp') or 'photoshop' in image.info:
            raise ValueError(f'Unexpected embedded metadata: {path.name}')
        gray = image.convert('L').resize((9, 8))
        values = list(gray.get_flattened_data())
        bits = ''.join('1' if values[y*9+x] > values[y*9+x+1] else '0' for y in range(8) for x in range(8))
        return {'width': image.width, 'height': image.height, 'mode': image.mode,
                'dhash': f'{int(bits, 2):016x}'}


def decode_audio(path):
    import av
    import numpy as np
    from mutagen.mp3 import MP3
    metadata = MP3(path)
    with av.open(str(path)) as container:
        stream = container.streams.audio[0]
        codec = stream.codec_context.name
        rate = stream.codec_context.sample_rate
        channels = stream.codec_context.channels
        if codec not in ('mp3', 'mp3float'):
            raise ValueError(f'Not MP3 audio: {path.name}: {codec}')
        samples = 0
        squares = 0.0
        opening_count = 0
        peak = 0.0
        for frame in container.decode(stream):
            a = frame.to_ndarray()
            normalized = a.astype(np.float64)
            if np.issubdtype(a.dtype, np.integer):
                normalized /= max(abs(np.iinfo(a.dtype).min), np.iinfo(a.dtype).max)
            if not np.isfinite(normalized).all():
                raise ValueError('Non-finite decoded PCM')
            peak = max(peak, float(np.abs(normalized).max()))
            remaining = max(0, 30 * rate - samples)
            if remaining:
                # ffmpeg MP3 decoder returns planar PCM; handle packed too.
                if frame.format.is_planar:
                    head = normalized[:, :remaining]
                else:
                    head = normalized[:, :remaining * channels]
                squares += float(np.square(head).sum())
                opening_count += head.size
            samples += frame.samples
        duration = samples / rate
    if duration < 30 or peak < 0.0001:
        raise ValueError(f'Too short or silent music: {path.name}')
    if abs(duration - metadata.info.length) > 1.0:
        raise ValueError(f'Truncated audio or duration disagreement: {path.name}')
    return {'duration_seconds': round(duration, 6), 'codec': codec, 'sample_rate_hz': rate,
            'channels': channels, 'decoded_samples_per_channel': samples,
            'peak_amplitude': round(peak, 8), 'first_30_seconds_rms': round(math.sqrt(squares / opening_count), 8),
            'embedded_metadata': {k: str(v) for k, v in (metadata.tags or {}).items() if k != 'APIC:'},
            'decode_status': 'PASS', 'listening_review': 'NOT_PERFORMED'}


def normalize_image(data, crop=None):
    from PIL import Image, ImageOps
    with Image.open(io.BytesIO(data)) as im:
        im.load()
        im = ImageOps.exif_transpose(im).convert('RGB')
        if crop is not None:
            if (len(crop) != 4 or any(type(x) is not int for x in crop) or
                    not (0 <= crop[0] < crop[2] <= im.width and 0 <= crop[1] < crop[3] <= im.height)):
                raise ValueError('Invalid documented crop rectangle')
            im = im.crop(crop)
        im.thumbnail((1280, 1280), Image.Resampling.LANCZOS)
        out = io.BytesIO()
        im.save(out, 'JPEG', quality=90, subsampling=2, optimize=True)
        return out.getvalue()


def freeze_members(root):
    root = Path(root)
    paths = [root / 'manifest.json', root / 'evaluation/protocol.json']
    for folder, pattern in [('candidates', '*.json'), ('playlists', '*.json'), ('evaluation', '*.csv')]:
        paths.extend(sorted((root / folder).glob(pattern)))
    return {p.relative_to(root).as_posix(): sha(p) for p in paths}


def freeze(root):
    path = Path(root) / 'evaluation/freeze.json'
    members = freeze_members(root)
    if path.exists():
        if read_json(path)['files'] != members:
            raise ValueError('Frozen inputs/labels changed. Create a new version; do not rewrite history.')
        return read_json(path)
    value = {'version': 1, 'frozen_on': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
             'seed': SEED, 'stage': 'before any model ranking', 'files': members}
    write_json(path, value)
    return value


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []
        self.images = []
    def handle_starttag(self, tag, attrs):
        self.paths.extend(value for name, value in attrs if name in ('src', 'href') and value)
        if tag == 'img': self.images.append(dict(attrs).get('src'))


def checksum_members(root):
    root = Path(root).resolve()
    return {p.relative_to(root).as_posix(): sha(p) for p in sorted(root.rglob('*'))
            if p.is_file() and p != root/'checksums.sha256'}


def write_checksums(root):
    root = Path(root)
    members = checksum_members(root)
    (root/'checksums.sha256').write_text(''.join(f'{digest}  {name}\n' for name, digest in members.items()), encoding='utf8')
    return len(members)


def verify_checksums(root):
    root = Path(root)
    entries = {}
    for line in (root/'checksums.sha256').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1)
        safe_path(root, name)
        if name in entries or not re.fullmatch('[0-9a-f]{64}', digest):
            raise ValueError('Invalid or duplicate checksum entry')
        entries[name] = digest
    if entries != checksum_members(root):
        raise ValueError('Package checksum mismatch, missing or unlisted file')
    return len(entries)


def validate(root, decode=True, frozen=True, checksums=True):
    root = Path(root).resolve()
    manifest = read_json(root / 'manifest.json')
    entries = manifest['assets']
    assets = {a['asset_id']: a for a in entries}
    if len(entries) != len(assets) or len({a['path'] for a in entries}) != len(entries):
        raise ValueError('Duplicate IDs or paths')
    if len({a['sha256'] for a in entries}) != len(entries):
        raise ValueError('Duplicate asset bytes')
    audios = [a for a in entries if a['kind'] == 'audio']
    images = [a for a in entries if a['kind'] == 'image']
    if len(audios) != 20 or not images or len(audios) + len(images) != len(entries):
        raise ValueError('Expected 20 songs and a nonempty photograph pool')
    actual_media = {p.relative_to(root).as_posix() for p in (root/'media').rglob('*') if p.is_file()}
    if actual_media != {a['path'] for a in entries}:
        raise ValueError('Unlisted or missing media files')
    details = {}
    total = 0
    for asset in entries:
        verify_license(asset)
        evidence = read_json(root/f'evidence/licenses/{asset["asset_id"]}.json')
        if evidence['asset'] != asset:
            raise ValueError('Manifest differs from per-item license evidence')
        path = safe_path(root, asset['path'])
        pattern = r'media/audio/audio_\d{4}\.mp3' if asset['kind'] == 'audio' else r'media/images/img_\d{4}\.jpg'
        if not re.fullmatch(pattern, asset['path']):
            raise ValueError('Non-neutral media filename')
        if not path.is_file() or sha(path) != asset['sha256'] or path.stat().st_size != asset['size_bytes']:
            raise ValueError(f'Missing/corrupt asset: {asset["asset_id"]}')
        total += path.stat().st_size
        if decode:
            details[asset['asset_id']] = decode_audio(path) if asset['kind'] == 'audio' else decode_image(path)
            if asset['kind'] == 'audio':
                if (abs(details[asset['asset_id']]['duration_seconds'] - asset['duration_seconds']) > 0.01 or
                        not asset['complete_track'] or asset['original_sha256'] != asset['sha256']):
                    raise ValueError('Inconsistent complete audio metadata')
                h, m, s = map(int, asset['source_duration'].split(':'))
                if abs(details[asset['asset_id']]['duration_seconds'] - (h*3600+m*60+s)) > 2:
                    raise ValueError('Decoded duration differs from official complete track: ' + asset['asset_id'])
            elif any(details[asset['asset_id']][key] != asset[key] for key in ('width', 'height')):
                raise ValueError('Image dimensions differ from manifest')
    if total > 150 * 1024 * 1024:
        raise ValueError('Media exceeds 150 MiB fixture budget')
    with (root / 'evaluation/expected-relevance.csv').open(encoding='utf8', newline='') as f:
        labels = list(csv.DictReader(f))
    with (root / 'evaluation/per-track-overrides.csv').open(encoding='utf8', newline='') as f:
        notes = list(csv.DictReader(f))
    if len(notes) != 20 or {x['track_id'] for x in notes} != {a['asset_id'] for a in audios}:
        raise ValueError('Missing or duplicate per-track difference records')
    if len(labels) != 80 or len({(x['playlist_id'], x['photo_id']) for x in labels}) != 80:
        raise ValueError('Expected exactly 80 unique evaluation pairs')
    all_tracks = []
    all_refs = []
    groups = {}
    for case_number, group in enumerate(PLAYLISTS, 1):
        playlist = read_json(root / f'playlists/{group}.json')
        tracks = playlist['track_ids']
        if playlist['playlist_id'] != group or len(tracks) != 5 or len(set(tracks)) != 5:
            raise ValueError('Invalid five-track playlist')
        if any(t not in assets or assets[t]['kind'] != 'audio' for t in tracks) or playlist['representative_track_id'] not in tracks:
            raise ValueError('Invalid playlist song reference')
        if playlist['paths'] != [assets[t]['path'] for t in tracks]:
            raise ValueError('Playlist paths do not preserve track order')
        all_tracks.extend(tracks)
        for note in (x for x in notes if x['track_id'] in tracks):
            if (note['playlist_id'] != group or note['label_status'] != 'provisional' or
                    note['is_representative'] != str(note['track_id'] == playlist['representative_track_id']).lower() or
                    not note['difference_note'] or note['opening_window_seconds'] != '30'):
                raise ValueError('Inconsistent per-track evaluation scope')
        m3u = (root / f'playlists/{group}.m3u8').read_text(encoding='utf8').splitlines()
        if [x for x in m3u if x and not x.startswith('#')] != ['../' + assets[t]['path'] for t in tracks]:
            raise ValueError('M3U8 order/path mismatch')
        record = read_json(root / f'candidates/case_{case_number:02}.json')
        check_input(record, assets)
        if record['case_id'] != f'case_{case_number:02}':
            raise ValueError('Case ID differs from neutral filename')
        if record['audio']['asset_id'] != playlist['representative_track_id']:
            raise ValueError('Representative input mismatch')
        ids = [p['asset_id'] for p in record['photos']]
        rows = [x for x in labels if x['playlist_id'] == group]
        if {x['photo_id'] for x in rows} != set(ids) or dict(Counter(x['role'] for x in rows)) != ROLES:
            raise ValueError('Candidate/label mismatch or invalid 8/4/4/4 sampling')
        for row in rows:
            if row['label_status'] != 'provisional' or row['expected_relevance'] not in ('0', '1', '2', '3'):
                raise ValueError('Invalid/unreviewed label claim')
            if any(not row.get(k) for k in ('reason', 'label_basis', 'annotator')):
                raise ValueError('Missing annotation provenance')
            if row['representative_track_id'] != playlist['representative_track_id']:
                raise ValueError('Label representative mismatch')
        base_order = [x['photo_id'] for x in rows]
        if mixed_order(base_order, case_number) != ids:
            raise ValueError('Candidate order differs from recorded seeded shuffle')
        roles_by_id = {x['photo_id']: x['role'] for x in rows}
        if len({roles_by_id[x] for x in ids[:8]}) == 1:
            raise ValueError('Candidate roles not mixed')
        all_refs.extend(ids)
        relevant = sum(int(x['expected_relevance']) >= 2 for x in rows)
        groups[group] = {'tracks': 5, 'photos': 20, 'roles': ROLES,
                         'random_expected_precision_at_5': relevant / 20,
                         'random_expected_hard_distractors_at_5': 1.0}
    if len(set(all_tracks)) != 20 or set(all_refs) != {a['asset_id'] for a in images}:
        raise ValueError('Unused assets or shared songs across groups')
    for page in list((root / 'galleries').glob('*.html')) + list((root / 'evaluation').glob('*.html')):
        parser = Links(); parser.feed(page.read_text(encoding='utf8'))
        for href in parser.paths:
            u = urllib.parse.urlsplit(href)
            if u.scheme in ('https', 'http') or href.startswith('#'):
                continue
            dest = (page.parent / urllib.parse.unquote(u.path)).resolve()
            if not dest.is_relative_to(root) or not dest.is_file():
                raise ValueError(f'Broken gallery link: {page.name}: {href}')
    for group in PLAYLISTS:
        if not (root/f'galleries/{group}.html').is_file() or not (root/f'evaluation/{group}.html').is_file():
            raise ValueError('Missing blind gallery or separate answer page')
        record = read_json(root/f'candidates/case_{PLAYLISTS.index(group)+1:02}.json')
        expected = ['../'+p['path'] for p in record['photos']]
        for folder in ('galleries', 'evaluation'):
            content = (root/f'{folder}/{group}.html').read_text(encoding='utf8')
            parser = Links(); parser.feed(content)
            if parser.images != expected:
                raise ValueError('Gallery images differ from candidate membership/order')
            if folder == 'galleries' and any(term in content for term in (*ROLES, 'expected_relevance', '../evaluation/')):
                raise ValueError('Answer material leaked into blind gallery')
    for document in root.rglob('*.md'):
        for href in re.findall(r'\]\(([^)]+)\)', document.read_text(encoding='utf8')):
            u = urllib.parse.urlsplit(href)
            if u.scheme or href.startswith('#'): continue
            dest = (document.parent/urllib.parse.unquote(u.path)).resolve()
            if not dest.is_relative_to(root.parents[1]) or not dest.exists():
                raise ValueError(f'Broken Markdown reference: {document.name}: {href}')
    if frozen and read_json(root / 'evaluation/freeze.json')['files'] != freeze_members(root):
        raise ValueError('Frozen dataset/labels changed')
    checksum_count = verify_checksums(root) if checksums else None
    near = []
    if decode:
        for i, first in enumerate(images):
            for second in images[i+1:]:
                distance = (int(details[first['asset_id']]['dhash'], 16) ^ int(details[second['asset_id']]['dhash'], 16)).bit_count()
                if distance <= 4:
                    near.append({'first': first['asset_id'], 'second': second['asset_id'], 'dhash_distance': distance})
    return {'status': 'PASS', 'audio_files': 20, 'unique_photos': len(images), 'candidate_references': 80,
            'references_beyond_unique_photos': 80 - len(images), 'photo_usage_counts': dict(sorted(Counter(all_refs).items())),
            'media_bytes': total, 'media_mib': round(total/1024**2, 3), 'groups': groups,
            'decoded': decode, 'decode_results': details, 'near_duplicate_candidates': near,
            'checksum_files_verified': checksum_count,
            'near_duplicate_note': 'dHash is an aid only; visual review is recorded separately.',
            'recommendation_status': 'Blocked: no image-content ranking implementation is connected',
            'app_playlists_status': 'APP_PLAYLISTS_PENDING', 'photo_import_status': 'PHOTO_IMPORT_PENDING'}


def checked_copy(source, dest):
    dest = Path(dest)
    if dest.exists():
        if dest.is_file() and sha(source) == sha(dest):
            return
        raise FileExistsError(f'Refusing to overwrite existing user file: {dest}')
    dest.parent.mkdir(parents=True, exist_ok=True)
    with Path(source).open('rb') as src, dest.open('xb') as out:
        shutil.copyfileobj(src, out)


def checked_text(dest, text):
    dest = Path(dest)
    if dest.exists():
        if dest.is_file() and dest.read_text(encoding='utf8') == text: return
        raise FileExistsError(f'Refusing to overwrite {dest}')
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open('x', encoding='utf8') as stream: stream.write(text)


def materialize(root, destination):
    root = Path(root).resolve(); destination = Path(destination).resolve()
    if destination.is_relative_to(root) or root.is_relative_to(destination):
        raise ValueError('Export must be outside the fixture package')
    validate(root, decode=False)
    manifest = read_json(root / 'manifest.json')
    assets = {x['asset_id']: x for x in manifest['assets']}
    export_paths = {key: [] for key in assets}
    for n, group in enumerate(PLAYLISTS, 1):
        playlist = read_json(root / f'playlists/{group}.json')
        record = read_json(root / f'candidates/case_{n:02}.json')
        # Only neutral case IDs and audio/image IDs reach an inference export.
        for track in playlist['track_ids']:
            relative = f'case_{n:02}/audio/{track}.mp3'
            checked_copy(safe_path(root, assets[track]['path']), safe_path(destination, relative))
            export_paths[track].append(relative)
        for photo in record['photos']:
            relative = f'case_{n:02}/images/{photo["asset_id"]}.jpg'
            checked_copy(safe_path(root, photo['path']), safe_path(destination, relative))
            export_paths[photo['asset_id']].append(relative)
        neutral = {'case_id': record['case_id'],
                   'audio': {'asset_id': record['audio']['asset_id'], 'path': f'audio/{record["audio"]["asset_id"]}.mp3'},
                   'photos': [{'asset_id': p['asset_id'], 'path': f'images/{p["asset_id"]}.jpg'} for p in record['photos']]}
        text = json.dumps(neutral, indent=2) + '\n'
        target = safe_path(destination, f'case_{n:02}/input.json')
        checked_text(target, text)
        order = '#EXTM3U\n' + '\n'.join(f'audio/{t}.mp3' for t in playlist['track_ids']) + '\n'
        order_path = safe_path(destination, f'case_{n:02}/order.m3u8')
        checked_text(order_path, order)
    # Attribution travels alongside the export, outside each inference case.
    for filename in ('ATTRIBUTION.md', 'LICENSES.md'):
        checked_copy(root / filename, safe_path(destination, filename))
    for source in (root/'evidence/licenses').glob('*.json'):
        checked_copy(source, safe_path(destination, source.relative_to(root).as_posix()))
    export_manifest = {'schema_version': 1, 'kind': 'materialized-export',
                       'source_dataset_id': manifest['dataset_id'],
                       'source_manifest_sha256': sha(root/'manifest.json'),
                       'note': 'Operator provenance, not inference input. Source-package paths are historical provenance; path and export_paths resolve in this export.',
                       'assets': [dict(asset, path=export_paths[aid][0],
                                       source_package_path=asset['path'], export_paths=export_paths[aid])
                                  for aid, asset in assets.items()]}
    checked_text(safe_path(destination, 'manifest.json'), json.dumps(export_manifest, ensure_ascii=False, indent=2)+'\n')
    return {'status': 'PASS', 'cases': 4, 'audio_copies': 20, 'photo_copies': 80,
            'app_created': False, 'export_is_not_an_app_import': True}


def fetch_resumable(url, partial, expected_hash, max_bytes=32*1024*1024, attempts=3):
    """Preserve a .part file across errors; never bless it without the locked hash."""
    partial = Path(partial); partial.parent.mkdir(parents=True, exist_ok=True)
    if partial.exists() and sha(partial) == expected_hash: return partial
    for attempt in range(attempts):
        offset = partial.stat().st_size if partial.exists() else 0
        headers = {'User-Agent': 'VibePlayerFixtures/1.0 (+https://github.com/Axfff/Vibe-Player)'}
        if offset: headers['Range'] = f'bytes={offset}-'
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
                status = response.status
                if offset and status == 206:
                    if not response.headers.get('Content-Range', '').startswith(f'bytes {offset}-'):
                        raise ValueError('Invalid resume Content-Range')
                    mode = 'ab'
                elif status == 200:
                    mode = 'wb'; offset = 0
                else:
                    raise ValueError(f'Unexpected download status: {status}')
                response_start = offset
                expected_length = response.headers.get('Content-Length')
                with partial.open(mode) as stream:
                    while block := response.read(64*1024):
                        offset += len(block)
                        if offset > max_bytes: raise ValueError('Download exceeds size limit')
                        stream.write(block)
                if expected_length is not None and offset-response_start != int(expected_length):
                    raise OSError('Interrupted response: retained partial bytes for resume')
            if sha(partial) != expected_hash:
                raise ValueError('Upstream changed or corrupt download: locked SHA-256 mismatch')
            return partial
        except urllib.error.HTTPError as error:
            if error.code in (401, 403, 404) or attempt + 1 == attempts: raise
            wait = max(1, int(error.headers.get('Retry-After', '2')))
            if wait > 60: raise  # Respect a longer server cooldown; caller can resume later.
            time.sleep(wait)
        except (TimeoutError, OSError, http.client.IncompleteRead):
            if attempt + 1 == attempts: raise
            time.sleep(2)
    raise RuntimeError('Download retries exhausted')


def download(root, cache, selected=None):
    root = Path(root).resolve(); cache = Path(cache).resolve()
    if cache.is_relative_to(root): raise ValueError('Cache must be outside fixture package')
    entries = read_json(root/'manifest.json')['assets']
    if selected and not set(selected) <= {a['asset_id'] for a in entries}:
        raise ValueError('Unknown requested asset ID')
    for asset in entries:
        if selected and asset['asset_id'] not in selected: continue
        verify_license(asset)
        target = safe_path(root, asset['path'])
        if target.exists():
            if sha(target) != asset['sha256']: raise FileExistsError(f'Refusing to overwrite {target}')
            continue
        original = fetch_resumable(asset['download_url'], cache / (asset['asset_id'] + '.part'), asset['original_sha256'])
        data = original.read_bytes()
        if asset['kind'] == 'image': data = normalize_image(data, asset.get('crop_xyxy'))
        if hashlib.sha256(data).hexdigest() != asset['sha256']:
            raise ValueError('Processed hash differs; use pinned Pillow version, do not silently update hashes')
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as out: out.write(data)
        print(asset['asset_id'], 'downloaded and verified', flush=True)


def main(command=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    if command is None: parser.add_argument('command', choices=['validate', 'download', 'materialize', 'freeze', 'checksums'])
    parser.add_argument('--output', type=Path)
    parser.add_argument('--cache', type=Path)
    parser.add_argument('--asset', action='append', help='Download only these neutral asset IDs')
    args = parser.parse_args()
    action = command or args.command
    if action == 'validate':
        if args.output and args.output.resolve().is_relative_to(args.root.resolve()):
            parser.error('Validation output must be outside the checksummed package')
        value = validate(args.root)
        if args.output: write_json(args.output, value)
        print(json.dumps({k:v for k,v in value.items() if k not in ('decode_results', 'photo_usage_counts')}, indent=2))
    elif action == 'freeze': print(json.dumps(freeze(args.root), indent=2))
    elif action == 'checksums': print(write_checksums(args.root), 'files checksummed')
    elif action == 'materialize':
        if not args.output: parser.error('--output required')
        print(json.dumps(materialize(args.root, args.output), indent=2))
    elif action == 'download':
        if not args.cache: parser.error('--cache required (outside fixture package)')
        download(args.root, args.cache, args.asset)


if __name__ == '__main__':
    main()
