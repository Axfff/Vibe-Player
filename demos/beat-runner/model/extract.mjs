// Decode corpus excerpts only on the training machine. The browser uses AudioBuffer.
import { readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { extractStyleFeatures, STYLE_WINDOW_SECONDS } from '../public/style-model.js';

const SAMPLE_RATE = 44100;
const CHANNELS = 2;
const catalogPath = process.argv[2];
const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
const output = [];
for (const { song } of catalog) {
  const decoded = spawnSync('ffmpeg', [
    '-v', 'error', '-i', join(dirname(catalogPath), `${song}.mp3`),
    '-t', String(2 * STYLE_WINDOW_SECONDS), '-f', 'f32le', '-ar', String(SAMPLE_RATE),
    '-ac', String(CHANNELS), '-',
  ], { maxBuffer: 8 * 1024 * 1024 });
  if (decoded.error) throw decoded.error;
  const samples = Math.floor(decoded.stdout.length / (4 * CHANNELS));
  const channels = Array.from({ length: CHANNELS }, () => new Float32Array(samples));
  for (let i = 0; i < samples; i++) {
    for (let channel = 0; channel < CHANNELS; channel++) {
      channels[channel][i] = decoded.stdout.readFloatLE(4 * (i * CHANNELS + channel));
    }
  }
  const buffer = {
    sampleRate: SAMPLE_RATE,
    length: samples,
    numberOfChannels: CHANNELS,
    getChannelData: channel => channels[channel],
  };
  const windows = [0, STYLE_WINDOW_SECONDS]
    .filter(start => samples / SAMPLE_RATE >= start + STYLE_WINDOW_SECONDS - 0.3)
    .map(start => extractStyleFeatures(buffer, start))
    .filter(Boolean);
  output.push(windows);
}
console.log(JSON.stringify(output));
