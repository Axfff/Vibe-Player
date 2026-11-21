import assert from 'node:assert/strict';
import test from 'node:test';
import { detectBeats } from '../public/beat.js';
import { createDemoTrack, demoTracks } from '../public/demo-tracks.js';

function audioBuffer(numberOfChannels, length, sampleRate) {
  const channels = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
  return {
    duration: length / sampleRate,
    length,
    sampleRate,
    numberOfChannels,
    getChannelData: index => channels[index],
  };
}

function audioFixture(duration, accents, { sampleRate = 24000, gain = 1, stereo = false } = {}) {
  const buffer = audioBuffer(stereo ? 2 : 1, Math.round(duration * sampleRate), sampleRate);
  const signal = buffer.getChannelData(0);
  for (const { time, strength = 1, hz = 100 } of accents) {
    const first = Math.round(time * sampleRate);
    const size = Math.round(0.07 * sampleRate);
    for (let i = 0; i < size && first + i < signal.length; i++) {
      const offset = i / sampleRate;
      signal[first + i] += gain * strength * Math.sin(2 * Math.PI * hz * offset)
        * Math.exp(-55 * offset);
    }
  }
  if (stereo) buffer.getChannelData(1).set(signal.map(value => -value));
  return buffer;
}

function evenlySpacedBeats(bpm, start, count) {
  return Array.from({ length: count }, (_, i) => start + i * 60 / bpm);
}

function assertAlignment(actual, expected, tolerance = 0.025) {
  assert.equal(actual.length, expected.length);
  expected.forEach((time, i) => {
    assert.ok(Math.abs(actual[i] - time) < tolerance,
      `beat ${i + 1}: expected ${time.toFixed(3)}s, got ${actual[i].toFixed(3)}s`);
  });
}

test('tracks regular accents at the three demo tempos with an off-grid intro', () => {
  for (const bpm of [86, 113, 145]) {
    const expected = evenlySpacedBeats(bpm, 0.237, 24);
    const audio = audioFixture(expected.at(-1) + 0.34, expected.map(time => ({ time })));
    const result = detectBeats(audio);
    assert.equal(result.bpm, bpm);
    assertAlignment(result.beats, expected);
  }
});

test('retains the beat grid across seeded tempo, cue strength and start time variations', () => {
  let seed = 1234567;
  function random() {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 2 ** 32;
  }

  const sampleRates = [12000, 24000, 44100, 48000];
  for (let trial = 0; trial < 100; trial++) {
    const bpm = 65 + Math.floor(random() * 120);
    const expected = evenlySpacedBeats(bpm, random() * 1.2, 24);
    const sampleRate = sampleRates[Math.floor(random() * sampleRates.length)];
    const accents = expected.map(time => ({ time, strength: 0.1 + 0.3 * random(), hz: 90 }));
    const result = detectBeats(audioFixture(expected.at(-1) + 0.2, accents, { sampleRate }));
    assert.ok(Math.abs(result.bpm - bpm) <= 1, `trial ${trial}: expected ${bpm} BPM, got ${result.bpm}`);
    assertAlignment(result.beats, expected);
  }
});

test('detects every composed beat in all three bundled soundtracks', () => {
  for (const track of demoTracks) {
    const buffer = createDemoTrack({ createBuffer: audioBuffer }, track.id);
    const expected = evenlySpacedBeats(track.bpm, 0, Math.round(buffer.duration * track.bpm / 60));
    const result = detectBeats(buffer);
    assert.equal(result.bpm, track.bpm, track.title);
    assertAlignment(result.beats, expected, 0.012);
  }
});

test('retains tempo and alignment under volume, sample rate and stereo polarity changes', () => {
  const expected = evenlySpacedBeats(111, 0, 26);
  const accents = expected.map(time => ({ time }));
  const baseline = detectBeats(audioFixture(expected.at(-1) + 0.3, accents));
  const transformed = detectBeats(audioFixture(expected.at(-1) + 0.3, accents, {
    sampleRate: 44100,
    gain: 0.08,
    stereo: true,
  }));
  assert.equal(baseline.bpm, 111);
  assert.equal(transformed.bpm, baseline.bpm);
  assertAlignment(transformed.beats, baseline.beats, 0.018);
});

test('keeps the main pulse over syncopated accents and two missed kicks', () => {
  const expected = evenlySpacedBeats(108, 0.56, 28);
  const accents = expected.flatMap((time, i) => {
    const offBeat = { time: time + 0.19, strength: 0.21, hz: 2400 };
    return i === 13 || i === 14 ? [offBeat] : [{ time }, offBeat];
  });
  const result = detectBeats(audioFixture(expected.at(-1) + 0.3, accents));
  assert.equal(result.bpm, 108);
  assertAlignment(result.beats, expected);
});

test('does not report tempos for silence or a clip with a single onset', () => {
  const empty = { beats: [], bpm: null };
  assert.deepEqual(detectBeats(audioFixture(0.9, [{ time: 0 }])), empty);
  assert.deepEqual(detectBeats(audioFixture(4, [])), empty);
  assert.deepEqual(detectBeats(audioFixture(4, [{ time: 0.72 }])), empty);
});

test('does not treat sustained bass oscillation as a beat', () => {
  const sampleRate = 24000;
  const buffer = audioBuffer(1, 8 * sampleRate, sampleRate);
  buffer.getChannelData(0).forEach((_, i, samples) => {
    const time = i / sampleRate;
    samples[i] = 0.3 * Math.min(1, time / 0.1) * Math.sin(2 * Math.PI * 26 * time);
  });
  assert.deepEqual(detectBeats(buffer), { beats: [], bpm: null });
});
