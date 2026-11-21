import assert from 'node:assert/strict';
import test from 'node:test';
import { createDemoTrack, demoTracks } from '../public/demo-tracks.js';

const context = {
  createBuffer(numberOfChannels, length, sampleRate) {
    const channels = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
    return { numberOfChannels, length, sampleRate, getChannelData: index => channels[index] };
  },
};

for (const track of demoTracks) {
  test(`${track.title} renders a deterministic, unclipped stereo loop`, () => {
    const buffer = createDemoTrack(context, track.id);
    const copy = createDemoTrack(context, track.id);
    const seconds = buffer.length / buffer.sampleRate;
    const barCount = seconds * track.bpm / 240;

    assert.equal(buffer.sampleRate, 24000);
    assert.equal(buffer.numberOfChannels, 2);
    assert.ok(seconds >= 20 && seconds <= 30);
    assert.ok(Math.abs(barCount - Math.round(barCount)) < 0.0001);
    assert.notDeepEqual(buffer.getChannelData(0), buffer.getChannelData(1));

    for (let channel = 0; channel < buffer.numberOfChannels; channel += 1) {
      const samples = buffer.getChannelData(channel);
      assert.deepEqual(samples, copy.getChannelData(channel));

      let peak = 0;
      let energy = 0;
      for (const sample of samples) {
        peak = Math.max(peak, Math.abs(sample));
        energy += sample * sample;
      }
      const rms = Math.sqrt(energy / samples.length);
      assert.ok(peak <= 0.9 && rms > 0.02, `peak ${peak}, RMS ${rms}`);
      assert.ok(Math.abs(samples[0] - samples.at(-1)) < 0.02);
    }
  });
}

test('rejects a missing demo track ID', () => {
  assert.throws(() => createDemoTrack(context, 'missing'), RangeError);
});
