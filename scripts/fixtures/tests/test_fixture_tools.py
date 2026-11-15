"""Offline boundary tests. Tiny generated test bytes are NOT dataset assets."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fixture_tools as f


class Response(io.BytesIO):
    def __init__(self, data, status=200, headers=None):
        super().__init__(data)
        self.status = status
        self.headers = headers or {}


class FixtureToolsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
    def tearDown(self):
        self.temp.cleanup()

    def test_external_paths_are_rejected(self):
        for path in ('../secret', '/tmp/data', 'C:/secret', 'media/../secret', 'https://x', 'a\\b', 'a//b', 'a/./b'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                f.safe_path(self.root, path)
        self.assertEqual(f.safe_path(self.root, 'media/images/img_0001.jpg'), (self.root/'media/images/img_0001.jpg').resolve())

    def test_existing_unequal_export_file_is_preserved(self):
        source = self.root/'source'; dest = self.root/'dest'
        source.write_bytes(b'fixture'); dest.write_bytes(b'user data')
        with self.assertRaises(FileExistsError): f.checked_copy(source, dest)
        self.assertEqual(dest.read_bytes(), b'user data')

    def test_identical_export_is_idempotent(self):
        source = self.root/'source'; dest = self.root/'nested/dest'
        source.write_bytes(b'fixture')
        f.checked_copy(source, dest); before = dest.stat().st_mtime_ns
        f.checked_copy(source, dest)
        self.assertEqual(dest.stat().st_mtime_ns, before)

    def test_export_json_preserves_existing_user_content(self):
        dest=self.root/'input.json'
        f.checked_text(dest,'original\n');before=dest.stat().st_mtime_ns
        f.checked_text(dest,'original\n')
        self.assertEqual(dest.stat().st_mtime_ns,before)
        with self.assertRaises(FileExistsError):f.checked_text(dest,'replacement\n')
        self.assertEqual(dest.read_text(encoding='utf8'),'original\n')

    def test_seeded_shuffle_reproducible_and_keeps_membership(self):
        ids = [f'img_{i:04}' for i in range(20)]
        first = f.mixed_order(ids, 1)
        self.assertEqual(first, f.mixed_order(ids, 1))
        self.assertEqual(set(first), set(ids))
        self.assertNotEqual(first, ids)
        self.assertNotEqual(first, f.mixed_order(ids, 2))
        with self.assertRaises(ValueError): f.mixed_order(['same', 'same'], 1)

    def test_v2_order_is_independent_of_annotation_row_permutations(self):
        ids = [f'img_{i:04}' for i in range(20)]
        first = f.independent_order(ids,1)
        self.assertEqual(first,f.independent_order(reversed(ids),1))
        self.assertEqual(first,f.independent_order(f.mixed_order(ids,4),1))
        self.assertEqual(set(first),set(ids))
        self.assertNotEqual(first,f.independent_order(ids,2))
        with self.assertRaises(ValueError): f.independent_order(['same','same'],1)

    def input(self):
        assets = {'audio_0001': {'kind':'audio','path':'media/audio/audio_0001.mp3'}}
        for n in range(1,21): assets[f'img_{n:04}']={'kind':'image','path':f'media/images/img_{n:04}.jpg'}
        record={'case_id':'case_01','audio':{'asset_id':'audio_0001','path':assets['audio_0001']['path']},
                'photos':[{'asset_id':key,'path':value['path']} for key,value in assets.items() if key.startswith('img_')]}
        return assets, record

    def test_answer_fields_and_semantic_case_ids_are_rejected(self):
        for field in ('expected_relevance','role','positive','expectedMood','source_description'):
            assets, record = self.input(); record['photos'][0][field]='answer'
            with self.subTest(field=field), self.assertRaises(ValueError): f.check_input(record,assets)
        assets, record=self.input();record['case_id']='bright'
        with self.assertRaises(ValueError): f.check_input(record,assets)

    def test_wrong_or_duplicate_candidate_reference_is_rejected(self):
        assets, record=self.input();f.check_input(record,assets)
        record['photos'][1]=record['photos'][0]
        with self.assertRaises(ValueError): f.check_input(record,assets)
        assets, record=self.input();record['photos'][0]['path']='../private.jpg'
        with self.assertRaises(ValueError): f.check_input(record,assets)

    def licensed(self):
        return {key:'value' for key in ('asset_id','title','creator','verified_on','attribution','modifications')} | {
            'license':'CC BY 4.0','license_url':f.LICENSES['CC BY 4.0'],
            'source_page':'https://example.org/work','download_url':'https://example.org/file',
            'sha256':'a'*64,'original_sha256':'b'*64}

    def test_missing_or_unapproved_license_is_rejected(self):
        good=self.licensed();f.verify_license(good)
        for key in ('creator','license_url','attribution','original_sha256'):
            invalid=dict(good);invalid.pop(key)
            with self.subTest(key=key), self.assertRaises(ValueError):f.verify_license(invalid)
        for license in ('CC BY-SA 4.0','CC BY-NC 4.0','Public domain','Unknown'):
            invalid=dict(good,license=license)
            with self.assertRaises(ValueError):f.verify_license(invalid)

    def test_credential_urls_are_rejected(self):
        invalid=self.licensed();invalid['download_url']='https://user:secret@example.org/file'
        with self.assertRaises(ValueError):f.verify_license(invalid)

    def test_freeze_detects_label_changes_without_overwriting(self):
        f.write_json(self.root/'manifest.json', {'assets':[]})
        f.write_json(self.root/'evaluation/protocol.json', {'seed':f.SEED})
        labels=self.root/'evaluation/a.csv';labels.write_text('before',encoding='utf8')
        original=f.freeze(self.root);self.assertEqual(original,f.freeze(self.root))
        labels.write_text('after',encoding='utf8')
        with self.assertRaises(ValueError):f.freeze(self.root)
        self.assertEqual(original,f.read_json(self.root/'evaluation/freeze.json'))

    def test_full_download_verifies_hash(self):
        data=b'public asset';part=self.root/'file.part'
        with patch.object(f.urllib.request,'urlopen',return_value=Response(data)):
            result=f.fetch_resumable('https://example.org/file',part,hashlib.sha256(data).hexdigest())
        self.assertEqual(result.read_bytes(),data)

    def test_resume_checks_offset_and_preserves_partial(self):
        part=self.root/'file.part';part.write_bytes(b'abc');data=b'abcdef'
        with patch.object(f.urllib.request,'urlopen',return_value=Response(b'def',206,{'Content-Range':'bytes 3-5/6'})) as op:
            f.fetch_resumable('https://example.org/file',part,hashlib.sha256(data).hexdigest())
        self.assertEqual(op.call_args.args[0].headers['Range'],'bytes=3-')
        self.assertEqual(part.read_bytes(),data)

    def test_server_ignoring_range_restarts_without_duplicate_bytes(self):
        part=self.root/'file.part';part.write_bytes(b'ab')
        with patch.object(f.urllib.request,'urlopen',return_value=Response(b'abcdef')):
            f.fetch_resumable('https://example.org/file',part,hashlib.sha256(b'abcdef').hexdigest())
        self.assertEqual(part.read_bytes(),b'abcdef')

    def test_wrong_range_response_is_rejected(self):
        part=self.root/'file.part';part.write_bytes(b'abc')
        with patch.object(f.urllib.request,'urlopen',return_value=Response(b'def',206,{'Content-Range':'bytes 2-4/6'})):
            with self.assertRaises(ValueError): f.fetch_resumable('https://example.org/file',part,'a'*64)
        self.assertEqual(part.read_bytes(),b'abc')

    def test_corrupt_or_html_download_is_not_accepted(self):
        for data in (b'<html>Error</html>',b'truncated'):
            with self.subTest(data=data), patch.object(f.urllib.request,'urlopen',return_value=Response(data)):
                with self.assertRaisesRegex(ValueError,'SHA-256'):f.fetch_resumable('https://example.org/file',self.root/'file.part','a'*64)

    def test_network_failure_has_bounded_retries(self):
        with patch.object(f.urllib.request,'urlopen',side_effect=TimeoutError('disconnected')) as op, patch.object(f.time,'sleep'):
            with self.assertRaises(TimeoutError): f.fetch_resumable('https://example.org/file',self.root/'file.part','a'*64,attempts=2)
        self.assertEqual(op.call_count,2)

    def test_access_denied_is_not_retried_or_bypassed(self):
        error=urllib.error.HTTPError('https://example.org/file',403,'Forbidden',{},None)
        with patch.object(f.urllib.request,'urlopen',side_effect=error) as op:
            with self.assertRaises(urllib.error.HTTPError):f.fetch_resumable(error.url,self.root/'file.part','a'*64)
        self.assertEqual(op.call_count,1)

    def test_oversize_download_fails(self):
        with patch.object(f.urllib.request,'urlopen',return_value=Response(b'12345')):
            with self.assertRaises(ValueError):f.fetch_resumable('https://example.org/file',self.root/'file.part','a'*64,max_bytes=4)

    def test_bad_image_signature_cannot_be_a_photo(self):
        path=self.root/'img_0001.jpg';path.write_bytes(b'<html>404</html>')
        with self.assertRaises(Exception):f.decode_image(path)

    def test_normalization_strips_exif_and_preserves_orientation(self):
        from PIL import Image
        im=Image.new('RGB',(1800,1200),(20,80,180));exif=Image.Exif();exif[274]=6;exif[270]='positive secret label'
        stream=io.BytesIO();im.save(stream,'JPEG',exif=exif)
        data=f.normalize_image(stream.getvalue());path=self.root/'img_0001.jpg';path.write_bytes(data)
        info=f.decode_image(path)
        self.assertEqual((info['width'],info['height']),(853,1280))
        self.assertNotIn(b'positive',data)

    def test_crop_bounds_and_documented_crop(self):
        from PIL import Image
        stream=io.BytesIO();Image.new('RGB',(1000,800)).save(stream,'JPEG')
        for crop in ([-1,0,800,600],[0,0,1001,800],[0,700,1000,600],[0,0,500.5,800]):
            with self.assertRaises(ValueError):f.normalize_image(stream.getvalue(),crop)
        with Image.open(io.BytesIO(f.normalize_image(stream.getvalue(),[0,0,800,600]))) as im:
            self.assertEqual(im.size,(800,600))

    def test_short_response_resumes_instead_of_accepting_truncation(self):
        responses=[Response(b'abc',200,{'Content-Length':'6'}),
                   Response(b'def',206,{'Content-Length':'3','Content-Range':'bytes 3-5/6'})]
        with patch.object(f.urllib.request,'urlopen',side_effect=responses) as op, patch.object(f.time,'sleep'):
            part=f.fetch_resumable('https://example.org/file',self.root/'file.part',hashlib.sha256(b'abcdef').hexdigest())
        self.assertEqual(part.read_bytes(),b'abcdef');self.assertEqual(op.call_count,2)
        self.assertEqual(op.call_args.args[0].headers['Range'],'bytes=3-')

    def test_unknown_download_id_fails_before_network(self):
        f.write_json(self.root/'data/manifest.json',{'assets':[]})
        with patch.object(f.urllib.request,'urlopen') as op:
            with self.assertRaises(ValueError):f.download(self.root/'data',self.root/'cache',['img_unknown'])
        op.assert_not_called()

    def test_verified_cache_needs_no_network(self):
        part=self.root/'asset.part';part.write_bytes(b'complete')
        with patch.object(f.urllib.request,'urlopen') as op:
            self.assertEqual(f.fetch_resumable('https://example.org/file',part,f.sha(part)),part)
        op.assert_not_called()

    def test_checksums_reject_change_missing_and_unlisted_files(self):
        a=self.root/'a';a.write_bytes(b'original');f.write_checksums(self.root)
        self.assertEqual(f.verify_checksums(self.root),1)
        a.write_bytes(b'changed')
        with self.assertRaises(ValueError):f.verify_checksums(self.root)
        a.unlink()
        with self.assertRaises(ValueError):f.verify_checksums(self.root)
        a.write_bytes(b'original');(self.root/'extra').write_bytes(b'unlisted')
        with self.assertRaises(ValueError):f.verify_checksums(self.root)

    def test_package_rejects_duplicate_assets_and_missing_media(self):
        manifest=f.read_json(f.ROOT/'manifest.json')
        f.write_json(self.root/'manifest.json',manifest)
        with self.assertRaisesRegex(ValueError,'Unlisted or missing'):f.validate(self.root,decode=False,frozen=False,checksums=False)
        manifest['assets'].append(manifest['assets'][0])
        f.write_json(self.root/'manifest.json',manifest)
        with self.assertRaisesRegex(ValueError,'Duplicate IDs'):f.validate(self.root,decode=False,frozen=False,checksums=False)

    def test_bad_audio_signature_cannot_be_music(self):
        path=self.root/'audio.mp3';path.write_bytes(b'<html>404</html>')
        with self.assertRaises(Exception):f.decode_audio(path)

    def test_ffprobe_allows_original_album_art_but_rejects_extra_video(self):
        import probe_audio_fixtures as probe
        from types import SimpleNamespace
        source=self.root/'audio.mp3';source.write_bytes(b'locked test bytes')
        f.write_json(self.root/'manifest.json',{'assets':[{'asset_id':'audio_0001','kind':'audio',
                     'path':'audio.mp3','sha256':f.sha(source),'duration_seconds':60}]})
        info={'format':{'duration':'60','filename':'local'},'streams':[
            {'codec_type':'audio','codec_name':'mp3'},
            {'codec_type':'video','codec_name':'mjpeg','disposition':{'attached_pic':1}}]}
        def run(command,**kwargs):
            return SimpleNamespace(stdout='ffprobe test' if command[-1]=='-version' else json.dumps(info),stderr='',returncode=0)
        with patch.object(probe.shutil,'which',return_value=str(source)),patch.object(probe.subprocess,'run',side_effect=run):
            self.assertEqual(probe.probe(self.root,source)['audio_files'],1)
            info['streams'][1]['disposition']['attached_pic']=0
            with self.assertRaises(ValueError):probe.probe(self.root,source)

    def test_evaluation_counts_real_candidate_ids_and_rejects_bad_results(self):
        import evaluate_rankings as e
        predictions={'source_sha':'a'*40,'cases':[]}
        for n in range(1,5):
            case=f.read_json(f.ROOT/f'candidates/case_{n:02}.json')
            predictions['cases'].append({'case_id':case['case_id'],'audio_id':case['audio']['asset_id'],
                                        'photo_ids':[p['asset_id'] for p in case['photos'][:5]]})
        original_read=e.read_json
        def read(path):
            return {'files':{}} if path.name=='freeze.json' else original_read(path)
        # Synthetic results are only an offline metric test, never run evidence.
        with patch.object(e,'validate'),patch.object(e,'read_json',side_effect=read):
            result=e.evaluate(f.ROOT,predictions)
            self.assertEqual(len(result['cases']),4)
            first=result['cases'][0]
            with (f.ROOT/'evaluation/expected-relevance.csv').open(encoding='utf8') as stream:
                labels={r['photo_id']:r for r in f.csv.DictReader(stream) if r['playlist_id']=='bright'}
            selected=predictions['cases'][0]['photo_ids']
            relevant=sum(int(labels[x]['expected_relevance'])>=2 for x in selected)
            self.assertEqual(first['provisional_precision_at_5'],relevant/5)
            self.assertEqual(first['hard_distractors_at_5'],sum(labels[x]['role']=='hard_distractor' for x in selected))
            self.assertEqual(first['uniform_random_expected_precision_at_5'],12/20)
            predictions['cases'][0]['photo_ids'][1]=predictions['cases'][0]['photo_ids'][0]
            with self.assertRaises(ValueError):e.evaluate(f.ROOT,predictions)
            predictions['cases'][0]['photo_ids']=selected[:1]
            short=e.evaluate(f.ROOT,predictions)['cases'][0]
            self.assertEqual(short['returned'],1)
            self.assertEqual(short['provisional_precision_at_5'],int(int(labels[selected[0]]['expected_relevance'])>=2)/5)
            predictions['cases'][0]['photo_ids']=[]
            empty=e.evaluate(f.ROOT,predictions)['cases'][0]
            self.assertEqual(empty['provisional_precision_at_5'],0)
            self.assertIsNone(empty['precision_at_returned'])
            predictions['cases'][0]['photo_ids']=['outside']
            with self.assertRaises(ValueError):e.evaluate(f.ROOT,predictions)
            predictions['source_sha']='not-a-commit'
            with self.assertRaises(ValueError):e.evaluate(f.ROOT,predictions)


if __name__=='__main__':unittest.main()
