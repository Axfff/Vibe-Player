import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { detectBeats } from '../public/beat.js';
import { createDemoTrack, demoTracks } from '../public/demo-tracks.js';
import { predictStyle, STYLE_WINDOW_SECONDS } from '../public/style-model.js';

// Regenerate the Android copies from the original browser synth with Node and FFmpeg.
const destination = fileURLToPath(new URL('../../../project/app/src/main/res/raw/', import.meta.url));
const weights = JSON.parse(readFileSync(new URL('../public/model.json', import.meta.url), 'utf8'));
const expectedStyles = { afterglow: 'drift', 'signal-hop': 'groove', 'meteor-fm': 'surge' };
const sampleBytes = 4;
const context = {
    createBuffer(numberOfChannels, length, sampleRate) {
        const channels = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
        return { numberOfChannels, length, sampleRate, duration: length / sampleRate, getChannelData: index => channels[index] };
    },
};

function ffmpeg(args, input) {
    const result = spawnSync('ffmpeg', ['-nostdin', '-hide_banner', '-loglevel', 'error', ...args], {
        input,
        maxBuffer: 16 * 1024 * 1024,
    });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(result.stderr.toString() || `FFmpeg exited with ${result.status}`);
    return result.stdout;
}

function packSamples(audio) {
    const channels = [audio.getChannelData(0), audio.getChannelData(1)];
    const pcm = Buffer.alloc(audio.length * channels.length * sampleBytes);
    for (let frame = 0; frame < audio.length; frame++) {
        pcm.writeFloatLE(channels[0][frame], (frame * 2) * sampleBytes);
        pcm.writeFloatLE(channels[1][frame], (frame * 2 + 1) * sampleBytes);
    }
    return pcm;
}

function unpackSamples(pcm, sampleRate) {
    const audio = context.createBuffer(2, pcm.length / (2 * sampleBytes), sampleRate);
    const left = audio.getChannelData(0);
    const right = audio.getChannelData(1);
    for (let frame = 0; frame < audio.length; frame++) {
        left[frame] = pcm.readFloatLE((frame * 2) * sampleBytes);
        right[frame] = pcm.readFloatLE((frame * 2 + 1) * sampleBytes);
    }
    return audio;
}

const staging = mkdtempSync(join(tmpdir(), 'raptor-android-audio-'));
try {
    const outputs = [];
    for (const track of demoTracks) {
        const original = createDemoTrack(context, track.id);
        const sampleRate = String(original.sampleRate);
        const name = `raptor_${track.id.replaceAll('-', '_')}.m4a`;
        const path = join(staging, name);
        ffmpeg([
            '-f', 'f32le', '-ar', sampleRate, '-ac', '2', '-i', 'pipe:0', '-map_metadata', '-1',
            '-c:a', 'aac', '-b:a', '96k', '-fflags', '+bitexact', '-flags:a', '+bitexact',
            '-movflags', '+faststart', path,
        ], packSamples(original));

        const pcm = ffmpeg(['-i', path, '-f', 'f32le', '-ar', sampleRate, '-ac', '2', 'pipe:1']);
        const decoded = unpackSamples(pcm, original.sampleRate);
        const { bpm, beats } = detectBeats(decoded);
        assert.equal(bpm, track.bpm, `${track.title} tempo after encoding`);
        assert.equal(beats.length, Math.round(original.duration * bpm / 60), `${track.title} beat count after encoding`);
        const beatError = Math.max(...beats.map((seconds, index) => Math.abs(seconds - index * 60 / bpm)));
        assert.ok(beatError < 0.015, `${track.title} beat alignment after encoding`);
        assert.ok(Math.abs(decoded.duration - original.duration) < 0.05, `${track.title} duration after encoding`);
        for (let start = 0; start < decoded.duration - 2; start += STYLE_WINDOW_SECONDS) {
            const scene = predictStyle(weights, decoded, start);
            assert.equal(scene.style, expectedStyles[track.id], `${track.title} scene at ${start}s after encoding`);
            assert.ok(scene.confidence >= 0.45, `${track.title} scene confidence at ${start}s after encoding`);
        }
        console.log(`${track.title}: ${bpm} BPM, ${beats.length} beats, ${expectedStyles[track.id]}, max ${(beatError * 1e3).toFixed(1)} ms drift, ${statSync(path).size} bytes`);
        outputs.push(name);
    }
    mkdirSync(destination, { recursive: true });
    for (const name of outputs) copyFileSync(join(staging, name), join(destination, name));
} finally {
    rmSync(staging, { recursive: true, force: true });
}
