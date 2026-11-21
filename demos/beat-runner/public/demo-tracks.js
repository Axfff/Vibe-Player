export const demoTracks = [
    { id: 'afterglow', title: 'Afterglow', subtitle: 'Downtempo keys', bpm: 86 },
    { id: 'signal-hop', title: 'Signal Hop', subtitle: 'Electro funk', bpm: 113 },
    { id: 'meteor-fm', title: 'Meteor FM', subtitle: 'Arcade pulse', bpm: 145 },
];

const sampleRate = 24000;
const cycle = 2 * Math.PI;

function createSynth(context, bpm, bars) {
    const secondsPerBeat = 60 / bpm;
    const length = Math.round(sampleRate * secondsPerBeat * 4 * bars);
    const buffer = context.createBuffer(2, length, sampleRate);
    const left = buffer.getChannelData(0);
    const right = buffer.getChannelData(1);
    let seed = 42173;

    function noise() {
        seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
        return seed / 2147483648 - 1;
    }

    function voice(beat, duration, volume, pan, wave, attack = 0.004) {
        const start = Math.round(beat * secondsPerBeat * sampleRate);
        const samples = Math.round(duration * sampleRate);
        const leftGain = volume * Math.sqrt((1 - pan) / 2);
        const rightGain = volume * Math.sqrt((1 + pan) / 2);
        const release = Math.min(0.025, duration / 4);
        for (let i = 0; i < samples; i += 1) {
            const time = i / sampleRate;
            const envelope = Math.min(1, time / attack, (duration - time) / release);
            const value = wave(time) * envelope;
            // Let sustaining notes and echoes wrap across the loop boundary.
            const index = (start + i) % length;
            left[index] += value * leftGain;
            right[index] += value * rightGain;
        }
    }

    const instruments = {
        keys: (phase, time) => (
            Math.sin(phase + Math.sin(phase * 2) * 0.75 * Math.exp(-time * 4))
            + Math.sin(phase * 4.01) * 0.13 * Math.exp(-time * 10)
        ) * Math.exp(-time * 1.9),
        bass: (phase, time) => (
            0.78 * Math.sin(phase) + 0.22 * Math.sin(phase * 2) + 0.11 * Math.sin(phase * 3)
        ) * Math.exp(-time * 3),
        pluck: (phase, time) => (
            Math.sin(phase) + 0.5 * Math.sin(phase * 2) + 0.24 * Math.sin(phase * 3)
            + Math.sin(phase * 5) * 0.16 * Math.exp(-time * 20)
        ) * Math.exp(-time * 8),
        pulse: (phase, time) => (
            Math.sin(phase) + 0.35 * Math.sin(phase * 3) + 0.18 * Math.sin(phase * 5)
            + 0.2 * Math.sin(phase * 1.006)
        ) * Math.exp(-time * 3.5),
    };

    return {
        note(instrument, pitch, beat, beats, volume, pan = 0) {
            const phase = cycle * 440 * 2 ** ((pitch - 69) / 12);
            const wave = instruments[instrument];
            voice(beat, beats * secondsPerBeat, volume, pan, (time) => wave(time * phase, time));
        },
        kick(beat, volume) {
            voice(beat, 0.28, volume, 0, (time) => {
                const pitchSweep = 49 * time + (83 / 35) * (1 - Math.exp(-time * 35));
                return Math.sin(cycle * pitchSweep) * Math.exp(-time * 14)
                    + noise() * 0.09 * Math.exp(-time * 130);
            }, 0.0015);
        },
        snare(beat, volume) {
            voice(beat, 0.17, volume, 0.08, (time) => (
                noise() * Math.exp(-time * 24)
                + 0.32 * Math.sin(cycle * 183 * time) * Math.exp(-time * 36)
            ));
        },
        hat(beat, volume, open = false) {
            let previous = 0;
            voice(beat, open ? 0.16 : 0.045, volume, -0.24, (time) => {
                const next = noise();
                const high = (next - previous) / 2;
                previous = next;
                return high * Math.exp(-time * (open ? 20 : 75));
            }, 0.001);
        },
        finish(targetPeak) {
            let peak = 0.01;
            for (let i = 0; i < length; i += 1) {
                peak = Math.max(peak, Math.abs(left[i]), Math.abs(right[i]));
            }
            const gain = Math.min(1.6, targetPeak / peak);
            for (let i = 0; i < length; i += 1) {
                left[i] *= gain;
                right[i] *= gain;
            }
            return buffer;
        },
    };
}

function playPhrase(synth, instrument, beat, phrase, volume) {
    for (const [offset, pitch, duration] of phrase) {
        synth.note(instrument, pitch, beat + offset, duration, volume, 0.1);
        synth.note(instrument, pitch, beat + offset + 0.375, duration, volume * 0.22, -0.62);
    }
}

function afterglow(context, bpm) {
    const bars = 8;
    const synth = createSynth(context, bpm, bars);
    const harmony = [
        [45, 57, 60, 64, 67], [41, 57, 60, 64, 65],
        [48, 55, 59, 62, 64], [43, 55, 57, 62, 65],
    ];
    const first = [[0.75, 76, 0.75], [2.55, 74, 0.75], [3.5, 72, 0.43]];
    const second = [[0.55, 69, 0.95], [2.5, 72, 0.7], [3.5, 76, 0.62]];
    for (let bar = 0; bar < bars; bar += 1) {
        const beat = bar * 4;
        const [root, ...chord] = harmony[bar % harmony.length];
        chord.forEach((pitch, index) => {
            synth.note('keys', pitch, beat + index * 0.035, 2.8, 0.079, (index - 1.5) * 0.32);
            synth.note('keys', pitch, beat + 2.54 + index * 0.025, 1.35, 0.044, (index - 1.5) * 0.32);
        });
        synth.note('bass', root - 12, beat, 1.4, 0.22);
        synth.note('bass', root - 5, beat + 2.55, 0.64, 0.15);
        for (let step = 0; step < 4; step += 1) {
            synth.kick(beat + step, step % 2 === 0 ? 0.26 : 0.172);
            synth.hat(beat + step + 0.55, step % 2 === 0 ? 0.014 : 0.02);
        }
        synth.snare(beat + 1, 0.017);
        synth.snare(beat + 3, 0.02);
        playPhrase(synth, 'keys', beat, bar % 2 === 0 ? first : second, 0.095);
    }
    return synth.finish(0.34);
}

function signalHop(context, bpm) {
    const bars = 12;
    const synth = createSynth(context, bpm, bars);
    const harmony = [
        [38, 62, 65, 69, 72], [43, 59, 62, 65, 69],
        [36, 60, 64, 67, 71], [45, 57, 61, 67, 70],
    ];
    const phrase = [[0.25, 77, 0.42], [0.82, 74, 0.4], [1.5, 72, 0.27], [3.25, 74, 0.5]];
    const reply = [[0.5, 76, 0.3], [1.05, 79, 0.4], [2.25, 77, 0.65], [3.55, 73, 0.43]];
    for (let bar = 0; bar < bars; bar += 1) {
        const beat = bar * 4;
        const [root, ...chord] = harmony[bar % harmony.length];
        for (let step = 0; step < 4; step += 1) {
            synth.kick(beat + step, step === 0 ? 0.68 : 0.52);
            synth.hat(beat + step, 0.14);
            synth.hat(beat + step + 0.53, 0.24, step % 2 === 1);
        }
        synth.snare(beat + 1, 0.28);
        synth.snare(beat + 3, 0.33);
        for (const [offset, interval, duration] of [[0, 0, 0.5], [0.75, 0, 0.45], [1.55, 12, 0.38], [2, 7, 0.7], [3.25, 0, 0.48]]) {
            synth.note('bass', root + interval, beat + offset, duration, 0.24);
        }
        for (const offset of [0.53, 1.79, 2.53, 3.79]) {
            chord.forEach((pitch, index) => {
                synth.note('pluck', pitch, beat + offset + index * 0.008, 0.34, 0.057, (index - 1.5) * 0.37);
            });
        }
        playPhrase(synth, 'pluck', beat, bar % 4 === 3 ? reply : phrase, 0.13);
    }
    return synth.finish(0.76);
}

function meteorFm(context, bpm) {
    const bars = 16;
    const synth = createSynth(context, bpm, bars);
    const harmony = [
        [45, 57, 60, 64, 69], [41, 57, 60, 65, 72],
        [48, 55, 60, 64, 67], [43, 55, 59, 62, 67],
    ];
    const arp = [0, 2, 1, 3, 0, 1, 2, 3];
    const hook = [[0, 81, 0.7], [0.75, 79, 0.65], [1.5, 76, 0.75], [2.5, 72, 0.45], [3, 76, 0.85]];
    const turnaround = [[0, 79, 0.72], [1, 74, 0.45], [1.5, 76, 0.7], [2.25, 71, 0.7], [3.25, 74, 0.6]];
    for (let bar = 0; bar < bars; bar += 1) {
        const beat = bar * 4;
        const [root, ...chord] = harmony[Math.floor(bar / 2) % harmony.length];
        for (let step = 0; step < 4; step += 1) {
            synth.kick(beat + step, step === 0 ? 0.8 : 0.66);
            synth.hat(beat + step + 0.5, 0.32, true);
            synth.hat(beat + step + 0.75, 0.11);
        }
        synth.snare(beat + 1, 0.37);
        synth.snare(beat + 3, 0.4);
        if (bar % 4 === 3) {
            synth.snare(beat + 3.5, 0.19);
            synth.snare(beat + 3.75, 0.23);
        }
        for (let step = 0; step < arp.length; step += 1) {
            synth.note('pulse', root - 12, beat + step / 2 + 0.08, 0.34, 0.21);
            const pan = step % 2 === 0 ? -0.36 : 0.36;
            synth.note('pulse', chord[arp[step]] + 12, beat + step / 2, 0.39, 0.092, pan);
        }
        if (bar % 2 === 0) {
            playPhrase(synth, 'pulse', beat, bar % 4 === 2 ? turnaround : hook, 0.102);
        }
    }
    return synth.finish(0.88);
}

export function createDemoTrack(context, id) {
    const track = demoTracks.find((entry) => entry.id === id);
    switch (track?.id) {
        case 'afterglow': return afterglow(context, track.bpm);
        case 'signal-hop': return signalHop(context, track.bpm);
        case 'meteor-fm': return meteorFm(context, track.bpm);
        default: throw new RangeError(`Unknown demo track: ${id}`);
    }
}
