import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { createDemoTrack } from '../public/demo-tracks.js';
import { extractStyleFeatures, loadStyleModel, predictStyle, STYLE_WINDOW_SECONDS } from '../public/style-model.js';

const modelUrl = new URL('../public/model.json', import.meta.url);
const modelData = JSON.parse(await readFile(modelUrl, 'utf8'));

function mockAudio(seconds, sampleRate, ...channelFunctions) {
  const length = Math.round(seconds * sampleRate);
  const channels = channelFunctions.map(render => Float32Array.from(
    { length }, (_, index) => render(index / sampleRate)
  ));
  return { length, sampleRate, numberOfChannels: channels.length, getChannelData: index => channels[index] };
}

function dataUrl(model) {
  return `data:application/json,${encodeURIComponent(JSON.stringify(model))}`;
}

const note = time => 0.12 * Math.sin(2 * Math.PI * 250 * time);

test('scene model validates its serialized format and inference normalizes all probabilities', async () => {
  const model = await loadStyleModel(dataUrl(modelData));
  const audio = mockAudio(8, 24000, note, note);
  const result = predictStyle(model, audio);
  assert.deepEqual(Object.keys(result.probabilities), ['drift', 'groove', 'surge']);
  const probabilities = Object.values(result.probabilities);
  assert.ok(probabilities.every(value => value >= 0 && value <= 1));
  assert.ok(Math.abs(probabilities.reduce((sum, value) => sum + value, 0) - 1) < 1e-12);
  assert.equal(result.confidence, Math.max(...probabilities));
  assert.equal(result.probabilities[result.style], result.confidence);
});

test('invalid version, class order, dimensions, or scale reject before inference', async () => {
  for (const fixture of [
    null,
    { ...modelData, format: 'v2' },
    { ...modelData, styleNames: ['surge', 'groove', 'drift'] },
    { ...modelData, weights: [modelData.weights[0]] },
    { ...modelData, scale: modelData.scale.map(() => 0) },
  ]) {
    await assert.rejects(loadStyleModel(dataUrl(fixture)), /Scene model format is invalid/);
  }
});

test('short, silent, absent, or out-of-range audio falls back to the groove scene', () => {
  const fixtures = [
    [modelData, null, 0],
    [modelData, mockAudio(0.3, 12000, note), 0],
    [modelData, mockAudio(2, 8000, () => 0), 0],
    [modelData, mockAudio(2, 48000, note), 4],
    [null, mockAudio(2, 16000, note), 0],
  ];
  for (const [model, audio, start] of fixtures) {
    const result = predictStyle(model, audio, start);
    assert.equal(result.style, 'groove');
    assert.equal(result.confidence, 0);
  }
});

test('features follow each 8-second audio section and match for duplicated mono channels', () => {
  const low = time => 0.04 * Math.sin(2 * Math.PI * 100 * time);
  const sections = time => time < 8 ? low(time) : note(time);
  const mono = mockAudio(16, 16000, sections);
  const stereo = mockAudio(16, 16000, sections, sections);
  const first = extractStyleFeatures(stereo, 0);
  const second = extractStyleFeatures(stereo, 8);
  assert.deepEqual(first, extractStyleFeatures(mono, 0));
  assert.ok(first[0] < second[0] - 5);
  assert.ok(first[4] < second[4]);
});

test('exported standardization and softmax scores agree with a direct calculation', () => {
  const audio = mockAudio(8, 24000, note);
  const features = extractStyleFeatures(audio);
  const normalized = features.map((value, index) => Math.max(-5, Math.min(5, (
    (value - modelData.mean[index]) / modelData.scale[index]
  ))));
  const scores = modelData.weights.map((row, index) => Math.exp(
    row.reduce((sum, weight, column) => sum + weight * normalized[column], modelData.bias[index])
  ));
  const total = scores.reduce((sum, score) => sum + score, 0);
  const actual = predictStyle(modelData, audio).probabilities;
  for (const [index, style] of modelData.styleNames.entries()) {
    assert.ok(Math.abs(actual[style] - scores[index] / total) < 1e-12);
  }
});

test('demo audio selects all three scenes and can change scene within one buffer', () => {
  const context = {
    createBuffer(numberOfChannels, length, sampleRate) {
      const data = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
      return { length, sampleRate, numberOfChannels, getChannelData: channel => data[channel] };
    },
  };
  const afterglow = createDemoTrack(context, 'afterglow');
  const signalHop = createDemoTrack(context, 'signal-hop');
  const meteor = createDemoTrack(context, 'meteor-fm');
  for (const [buffer, style] of [[afterglow, 'drift'], [signalHop, 'groove'], [meteor, 'surge']]) {
    const prediction = predictStyle(modelData, buffer, 0);
    assert.equal(prediction.style, style);
    assert.ok(prediction.confidence >= 0.45);
  }

  const windowLength = afterglow.sampleRate * STYLE_WINDOW_SECONDS;
  const joined = context.createBuffer(2, 2 * windowLength, afterglow.sampleRate);
  for (let channel = 0; channel < joined.numberOfChannels; channel++) {
    joined.getChannelData(channel).set(afterglow.getChannelData(channel).subarray(0, windowLength));
    joined.getChannelData(channel).set(meteor.getChannelData(channel).subarray(0, windowLength), windowLength);
  }
  assert.equal(predictStyle(modelData, joined, 0).style, 'drift');
  assert.equal(predictStyle(modelData, joined, STYLE_WINDOW_SECONDS).style, 'surge');
});
