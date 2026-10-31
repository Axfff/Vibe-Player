import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const markup = await readFile(new URL('../static/index.html', import.meta.url), 'utf8');
const intent = {sceneTags: ['nature'], musicDescriptors: ['piano'], visualMoodSuggestions: ['calm'], uncertainty: 'high'};
const provider = (mode = 'mock') => ({provider: 'vision', mode, state: 'ready', requestsMade: 0});
const settings = (mode = 'mock') => ({demoToken: 'fixture-token', vision: provider(mode), catalogs: [],
  cloudRecipient: 'fixture-only', visionModel: 'fixture-model',
  limits: {albumPhotos: 2, uploadBytes: 5000, pixels: 10000, imageEdge: 50, visionCallsRemaining: 2}});
const track = (id = '1', preview = false) => ({ref: {provider: 'jamendo', providerTrackId: id}, title: `Track ${id}`, artist: 'Fixture',
  recommendationReason: 'matched tags', officialUrl: `https://www.jamendo.com/track/${id}`,
  capability: {mode: preview ? 'IN_APP_PREVIEW' : 'EXTERNAL_ONLY', reasonCode: 'fixture', attribution: 'Fixture attribution',
    licenseUrl: 'https://creativecommons.org/licenses/by/4.0/', previewUrl: 'https://prod-1.storage.jamendo.com/?trackid=1', expiresAt: '2099-01-01T00:00:00Z'}});
const discovery = (tracks = [track()], extras = {}) => ({sessionId: 'fixture-session', tracks, returned: tracks.length, inputMode: 'manual', ...extras});
const turn = () => new Promise(resolve => setImmediate(resolve));
let counter = 0;
async function page(mode, handler, run) {
  const dom = new JSDOM(markup, {url: 'http://localhost/', pretendToBeVisual: true});
  const originals = Object.fromEntries(['window','document','fetch','Audio','createImageBitmap'].map(k => [k, globalThis[k]]));
  const createUrl = URL.createObjectURL, revokeUrl = URL.revokeObjectURL;
  const calls = [];
  globalThis.window = dom.window; globalThis.document = dom.window.document;
  URL.createObjectURL = () => 'blob:fixture'; URL.revokeObjectURL = () => {};
  globalThis.createImageBitmap = async () => ({width: 80, height: 40, close() {}});
  dom.window.HTMLCanvasElement.prototype.getContext = () => ({fillRect() {}, drawImage() {}});
  dom.window.HTMLCanvasElement.prototype.toBlob = callback => callback(new Blob(['image'], {type: 'image/jpeg'}));
  globalThis.Audio = function () {const audio = dom.window.document.createElement('audio');
    audio.play = async () => {}; audio.pause = () => {}; audio.load = () => {}; return audio;};
  globalThis.fetch = async (path, options = {}) => {
    calls.push({path, options});
    const data = path === '/v1/providers/status' ? settings(mode) : await handler(path, options);
    return {ok: !data.error, status: data.error ? 503 : 200, json: async () => data};
  };
  try {
    await import(new URL(`../static/app.js?fixture=${++counter}`, import.meta.url));
    await turn();
    await run(id => dom.window.document.getElementById(id), calls, dom.window);
  } finally {
    dom.window.dispatchEvent(new dom.window.Event('pagehide'));
    await turn(); dom.window.close();
    for (const [key, value] of Object.entries(originals)) globalThis[key] = value;
    URL.createObjectURL = createUrl; URL.revokeObjectURL = revokeUrl;
  }
}
function confirm($) { $('mood').value = 'calm'; $('confirmed').checked = true; }
function files($, id, value) {Object.defineProperty($(id), 'files', {value, configurable: true});}
const photo = {name: 'fixture.png', size: 10, type: 'image/png'};

test('manual UI validates intent, renders real response identity, replaces, previews, handles empty/error and invalidates edits', async () => {
  let response = discovery([track('1', true)]);
  await page('unconfigured', async path => path.includes('/capabilities') ? {track: track('1', true)} : response, async ($, calls, window) => {
    assert.match($('analysisStatus').textContent, /未配置/);
    await $('discover').onclick(); assert.match($('resultStatus').textContent, /选择目标/);
    confirm($);
    $('musicTags').querySelectorAll('input').forEach(e => e.checked = true);
    await $('discover').onclick(); assert.match($('resultStatus').textContent, /最多选择 8/);
    $('musicTags').querySelectorAll('input').forEach(e => e.checked = e.value === 'piano');
    $('vocals').value = 'instrumental';
    await $('discover').onclick();
    assert.equal(document.querySelectorAll('.track').length, 1);
    assert.match($('results').textContent, /Track 1/);
    assert.equal(document.querySelector('.track a').rel, 'noopener noreferrer');
    const payload = JSON.parse(calls.find(c => c.path === '/v1/discover').options.body);
    assert.equal(payload.intent.instrumental, true); assert.deepEqual(payload.intent.musicTags, ['piano']);
    assert.equal(calls.find(c => c.path === '/v1/discover').options.headers['X-Discovery-Token'], 'fixture-token');
    await document.querySelector('.track button').onclick();
    assert.equal($('playerPanel').hidden, false);
    $('stop').onclick(); assert.equal($('playerPanel').hidden, true);
    response = discovery([track('2')]);
    await document.querySelector('.track button:last-child').onclick();
    assert.match($('results').textContent, /Track 2/);
    assert.equal(JSON.parse(calls.find(c => c.path.endsWith('/replace')).options.body).sessionId, 'fixture-session');
    response = discovery([]); await document.querySelector('.track button:last-child').onclick();
    assert.equal(document.querySelectorAll('.track').length, 0);
    await $('discover').onclick(); assert.match($('results').textContent, /0 首/);
    response = {error: 'provider unavailable'}; await $('discover').onclick();
    assert.match($('resultStatus').textContent, /provider unavailable/); assert.equal($('discover').disabled, false);
    for (const id of ['mood','searchText','energy','vocals','market']) {confirm($); $(id).oninput(); assert.equal($('confirmed').checked, false);}
    $('scenes').onchange(); $('musicTags').onchange();
    window.document.dispatchEvent(new window.Event('visibilitychange'));
  });
});

test('photo flow gates consent, applies suggestions, preserves user edits and exposes model/decode failures', async () => {
  let response = {vision: provider(), intent};
  await page('mock', async () => response, async ($) => {
    $('photoMode').onclick(); assert.equal($('photoPanel').hidden, false);
    files($, 'photo', [photo]); await $('photo').onchange();
    assert.equal($('photoPreview').hidden, false); assert.equal($('analyze').disabled, true);
    $('consent').checked = true; $('consent').onchange(); assert.equal($('analyze').disabled, false);
    await $('analyze').onclick(); assert.match($('suggestions').textContent, /平静/);
    assert.equal($('scenes').querySelector('input[value=nature]').checked, true);
    $('scenes').querySelector('input[value=city]').checked = true; $('scenes').onchange();
    $('musicTags').querySelector('input[value=rock]').checked = true; $('musicTags').onchange();
    await $('analyze').onclick(); assert.equal($('scenes').querySelector('input[value=city]').checked, true);
    response = {error: 'model failed'}; await $('analyze').onclick(); assert.match($('analysisStatus').textContent, /model failed/);
    $('consent').checked = false; $('consent').onchange(); await turn(); assert.equal($('analyze').disabled, true);
    files($, 'photo', [{...photo, type: 'text/plain'}]); await $('photo').onchange(); assert.equal($('analysisStatus').classList.contains('error'), true);
    $('cancelPhoto').onclick(); assert.equal($('photoPreview').hidden, true);
    $('manualMode').onclick(); assert.equal($('photoPanel').hidden, true);
  });
});

test('album UI validates selection, renders partial outcomes and cancels without more uploads', async () => {
  const snapshot = {albumId: 'album-fixture', total: 2, processed: 0, succeeded: 0, failed: 0, pending: 2, items: [], vision: provider(), state: 'processing'};
  let failStart = false;
  await page('live', async (path, options) => {
    if (path === '/v1/albums') return failStart ? {error: 'budget denied'} : {...snapshot};
    if (options.method === 'DELETE') return {...snapshot, state: 'cancelled'};
    if (path.includes('/photos/0')) Object.assign(snapshot, {processed: 1, succeeded: 1, pending: 1, items: [{index: 0, state: 'succeeded', intent}]});
    else Object.assign(snapshot, {processed: 2, failed: 1, pending: 0, state: 'completed_with_errors', intent,
      frequencies: {sceneTags: [{tag: 'nature', photos: 1}]}, items: [...snapshot.items, {index: 1, state: 'failed', errorCode: 'unsupported_image'}]});
    return {...snapshot};
  }, async ($) => {
    $('albumMode').onclick(); files($, 'albumFiles', [photo, photo, photo]); $('albumFiles').onchange();
    assert.match($('analysisStatus').textContent, /单批最多 2/);
    files($, 'albumFolder', [photo, {...photo, name: 'bad.txt', type: 'text/plain'}]); $('albumFolder').onchange();
    assert.equal($('albumItems').children.length, 2);
    $('consent').checked = true; $('consent').onchange(); await $('analyze').onclick();
    assert.equal($('albumProgress').value, 2); assert.match($('analysisStatus').textContent, /成功 1，失败 1/);
    assert.match($('suggestions').textContent, /相册覆盖 1/); assert.equal($('consent').checked, false);
    await $('stopAlbum').onclick(); assert.equal($('stopAlbum').hidden, true);
    files($, 'albumFiles', [photo]); $('albumFiles').onchange(); failStart = true;
    $('consent').checked = true; $('consent').onchange(); await $('analyze').onclick();
    assert.match($('analysisStatus').textContent, /budget denied/);
  });
});

test('late discovery results cannot overwrite the screen after intent edits', async () => {
  let resolve;
  await page('mock', () => new Promise(r => resolve = r), async ($) => {
    confirm($); const pending = $('discover').onclick();
    await turn(); $('searchText').value = 'changed'; $('searchText').oninput();
    resolve(discovery([track('stale')])); await pending;
    assert.doesNotMatch($('results').textContent, /Track stale/);
    assert.equal($('discover').disabled, false);
  });
});
