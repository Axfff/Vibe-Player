"""Run a supplied ffprobe on every locked MP3; full decoding is in validate.

The output must stay outside the checksummed dataset. No executable is downloaded
or installed by this command. Nonzero ffprobe exits and duration mismatch fail.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
from fixture_tools import ROOT, read_json, safe_path, sha, write_json


def probe(root, executable):
    resolved = shutil.which(str(executable))
    if not resolved: raise FileNotFoundError('ffprobe executable not found')
    version = subprocess.run([resolved, '-version'], capture_output=True, text=True, check=True).stdout.splitlines()[0]
    results = []
    for asset in read_json(root/'manifest.json')['assets']:
        if asset['kind'] != 'audio': continue
        path = safe_path(root, asset['path'])
        if sha(path) != asset['sha256']: raise ValueError('Corrupt audio: '+asset['asset_id'])
        command = [resolved, '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(path)]
        run = subprocess.run(command, capture_output=True, text=True, encoding='utf8', check=True)
        import json
        data = json.loads(run.stdout)
        if run.stderr or abs(float(data['format']['duration']) - asset['duration_seconds']) > 1:
            raise ValueError('ffprobe error or duration disagreement: '+asset['asset_id'])
        audio_streams = [s for s in data['streams'] if s['codec_type'] == 'audio']
        other_streams = [s for s in data['streams'] if s['codec_type'] != 'audio']
        if (len(audio_streams) != 1 or audio_streams[0]['codec_name'] != 'mp3' or
                any(not s.get('disposition', {}).get('attached_pic') for s in other_streams)):
            raise ValueError('Expected one MP3 stream: '+asset['asset_id'])
        data['format']['filename'] = asset['path']
        results.append({'asset_id': asset['asset_id'], 'exit_code': run.returncode,
                        'stderr': run.stderr, 'sha256': asset['sha256'], 'ffprobe': data})
    return {'status': 'PASS', 'audio_files': len(results), 'ffprobe_version': version,
            'executable_sha256': sha(resolved),
            'arguments': ['-v', 'error', '-show_format', '-show_streams', '-of', 'json', '<relative-audio-path>'],
            'results': results}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--ffprobe', default='ffprobe')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.root.resolve()):
        parser.error('Output must be outside the checksummed fixture package')
    value = probe(args.root, args.ffprobe)
    write_json(args.output, value)
    print(value['status'], value['audio_files'], 'MP3 streams probed')
