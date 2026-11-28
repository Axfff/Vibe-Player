const MIN_PERIOD_SECONDS = 0.31;
const MAX_PERIOD_SECONDS = 0.95;
const HOP_SECONDS = 0.01;

function onsetEnvelope(audio) {
  const channels = Array.from({ length: Math.min(audio.numberOfChannels, 2) }, (_, channel) =>
    audio.getChannelData(channel));
  const stride = Math.max(1, Math.floor(audio.sampleRate / 12000));
  const hop = Math.max(1, Math.round(audio.sampleRate * HOP_SECONDS / stride)) * stride;
  const frameSeconds = hop / audio.sampleRate;
  const frameCount = Math.ceil(audio.length / hop);
  const lowEnergy = new Float32Array(frameCount);
  const midEnergy = new Float32Array(frameCount);
  const highEnergy = new Float32Array(frameCount);
  const lowAlpha = 1 - Math.exp(-2 * Math.PI * 150 * stride / audio.sampleRate);
  const midAlpha = 1 - Math.exp(-2 * Math.PI * 1500 * stride / audio.sampleRate);
  const scale = stride / (hop * channels.length);

  // Filter each channel independently so phase-inverted stereo has the same beat cues.
  for (const samples of channels) {
    let low = 0;
    let smooth = 0;
    for (let frame = 0; frame < frameCount; frame++) {
      let lowSum = 0;
      let midSum = 0;
      let highSum = 0;
      const end = Math.min(audio.length, (frame + 1) * hop);
      for (let i = frame * hop; i < end; i += stride) {
        const sample = samples[i];
        low += lowAlpha * (sample - low);
        smooth += midAlpha * (sample - smooth);
        const mid = smooth - low;
        const high = sample - smooth;
        lowSum += low * low;
        midSum += mid * mid;
        highSum += high * high;
      }
      lowEnergy[frame] += lowSum * scale;
      midEnergy[frame] += midSum * scale;
      highEnergy[frame] += highSum * scale;
    }
  }

  let peakEnergy = 0;
  for (let i = 0; i < frameCount; i++) {
    peakEnergy = Math.max(peakEnergy, lowEnergy[i] + midEnergy[i] + highEnergy[i]);
  }
  const envelope = new Float32Array(frameCount);
  if (peakEnergy < 1e-9) return { envelope, frameSeconds };

  let lastLow = 0;
  let lastMid = 0;
  let lastHigh = 0;
  let previousBass = 0;
  let nextBass = 0;
  const bassWindow = Math.round(0.06 / frameSeconds);
  for (let i = 0; i < bassWindow; i++) nextBass += lowEnergy[i] ?? 0;
  for (let i = 0; i < frameCount; i++) {
    const low = Math.log1p(40 * lowEnergy[i] / peakEnergy);
    const mid = Math.log1p(40 * midEnergy[i] / peakEnergy);
    const high = Math.log1p(40 * highEnergy[i] / peakEnergy);
    // Use wider bass energy windows to reject rises caused by sustained wave cycles.
    const bassAttack = nextBass > 1.3 * previousBass ? Math.max(0, low - lastLow) : 0;
    envelope[i] = 0.62 * bassAttack
      + 0.25 * Math.max(0, mid - lastMid) + 0.13 * Math.max(0, high - lastHigh);
    previousBass += lowEnergy[i] - (lowEnergy[i - bassWindow] ?? 0);
    nextBass += (lowEnergy[i + bassWindow] ?? 0) - lowEnergy[i];
    lastLow = low;
    lastMid = mid;
    lastHigh = high;
  }
  return { envelope, frameSeconds };
}

function collectPeaks(envelope, frameSeconds) {
  let max = 0;
  let sum = 0;
  for (const value of envelope) {
    max = Math.max(max, value);
    sum += value;
  }
  const floor = Math.max(0.025, max * 0.05, 2.8 * sum / envelope.length);
  const minGap = Math.round(0.065 / frameSeconds);
  const peaks = [];
  for (let frame = 0; frame < envelope.length; frame++) {
    const strength = envelope[frame];
    if (strength < floor || strength < (envelope[frame - 1] ?? 0)
      || strength <= (envelope[frame + 1] ?? 0)) continue;
    const previous = peaks.at(-1);
    if (!previous || frame - previous.frame >= minGap) {
      peaks.push({ frame, strength });
    } else if (strength > previous.strength) {
      peaks[peaks.length - 1] = { frame, strength };
    }
  }
  return peaks;
}

function estimatePeriod(peaks, frameCount, frameSeconds) {
  const peakTrain = new Float32Array(frameCount);
  const strengths = peaks.map(peak => peak.strength).sort((a, b) => a - b);
  const cap = strengths[Math.floor((strengths.length - 1) * 0.9)];
  for (const { frame, strength } of peaks) {
    for (let offset = -2; offset <= 2; offset++) {
      const index = frame + offset;
      if (index >= 0 && index < frameCount) {
        peakTrain[index] = Math.min(strength, cap) * (1 - Math.abs(offset) / 3);
      }
    }
  }

  const min = Math.round(MIN_PERIOD_SECONDS / frameSeconds);
  const max = Math.round(MAX_PERIOD_SECONDS / frameSeconds);
  const correlations = new Float64Array(2 * max + 1);
  let sumSquares = 0;
  for (const value of peakTrain) sumSquares += value * value;
  for (let lag = min; lag <= 2 * max; lag++) {
    let dot = 0;
    for (let frame = lag; frame < frameCount; frame++) {
      dot += peakTrain[frame] * peakTrain[frame - lag];
    }
    correlations[lag] = dot / sumSquares;
  }

  let best = 0;
  let bestScore = 0;
  const scores = new Float64Array(max + 1);
  for (let lag = min; lag <= max; lag++) {
    const harmonicSupport = 0.2 * correlations[2 * lag];
    // Prefer the first lag when a periodic pulse also correlates at its multiples.
    const longPeriodPenalty = 0.15 * (lag - min) / (max - min);
    scores[lag] = correlations[lag] + harmonicSupport - longPeriodPenalty;
    if (scores[lag] > bestScore) {
      best = lag;
      bestScore = scores[lag];
    }
  }
  if (bestScore < 0.2) return null;
  if (best === min || best === max) return best;
  const before = scores[best - 1];
  const after = scores[best + 1];
  // Interpolate between adjacent lag bins to reduce drift from the 10 ms hop.
  return best + 0.5 * (before - after) / (before + after - 2 * bestScore);
}

function followBeatPath(peaks, period) {
  const scores = new Float64Array(peaks.length);
  const previous = new Int32Array(peaks.length).fill(-1);
  const strengths = peaks.map(peak => peak.strength).sort((a, b) => a - b);
  const typical = strengths[Math.floor(strengths.length * 0.75)];
  let best = 0;
  for (let i = 0; i < peaks.length; i++) {
    let continuation = 0;
    for (let j = i - 1; j >= 0; j--) {
      const elapsed = (peaks[i].frame - peaks[j].frame) / period;
      if (elapsed > 3.25) break;
      const intervals = Math.round(elapsed);
      const error = Math.abs(elapsed - intervals);
      if (intervals === 0 || error > 0.23) continue;
      const score = scores[j] - 0.38 * (intervals - 1) - 8 * error ** 2;
      if (score > continuation) {
        continuation = score;
        previous[i] = j;
      }
    }
    scores[i] = continuation + Math.min(1.5, peaks[i].strength / typical);
    if (scores[i] > scores[best]) best = i;
  }

  const anchors = [];
  for (let i = best; i >= 0; i = previous[i]) anchors.push(peaks[i].frame);
  anchors.reverse();
  if (anchors.length < 3) return [];

  const frames = [anchors[0]];
  for (let i = 1; i < anchors.length; i++) {
    const gap = anchors[i] - anchors[i - 1];
    const intervals = Math.round(gap / period);
    for (let step = 1; step <= intervals; step++) {
      frames.push(anchors[i - 1] + step * gap / intervals);
    }
  }
  return frames;
}

// Estimate audible beat times from PCM using multi-band onsets, autocorrelation
// and an onset path constrained by tempo. See Ellis (2007), DOI 10.1080/09298210701653344.
export function detectBeats(audio) {
  const noBeats = { beats: [], bpm: null };
  if (audio.duration < 1.2) return noBeats;
  const { envelope, frameSeconds } = onsetEnvelope(audio);
  const peaks = collectPeaks(envelope, frameSeconds);
  if (peaks.length < 3) return noBeats;
  const period = estimatePeriod(peaks, envelope.length, frameSeconds);
  if (period === null) return noBeats;
  const frames = followBeatPath(peaks, period);
  if (!frames.length) return noBeats;
  const secondsPerBeat = (frames.at(-1) - frames[0]) * frameSeconds / (frames.length - 1);
  return {
    beats: frames.map(frame => frame * frameSeconds),
    bpm: Math.round(60 / secondsPerBeat),
  };
}
