export const STYLE_WINDOW_SECONDS = 8;

const SAMPLE_RATE = 8000;
const FFT_SIZE = 512;
const HOP = 256;
const MIN_SECONDS = 0.65;
const STYLES = ['drift', 'groove', 'surge'];
const BAND_LIMITS = [4, 11, 32, 80, 128, FFT_SIZE / 2];
const FEATURE_COUNT = 17;

const hann = Float32Array.from({ length: FFT_SIZE }, (_, i) => (
  0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1))
));
const reverseBits = Uint16Array.from({ length: FFT_SIZE }, (_, index) => {
  let reversed = 0;
  for (let bit = 1; bit < FFT_SIZE; bit *= 2) {
    reversed = (reversed << 1) | (index & 1);
    index >>= 1;
  }
  return reversed;
});
const twiddleReal = Float64Array.from({ length: FFT_SIZE / 2 }, (_, index) => (
  Math.cos(-2 * Math.PI * index / FFT_SIZE)
));
const twiddleImaginary = Float64Array.from({ length: FFT_SIZE / 2 }, (_, index) => (
  Math.sin(-2 * Math.PI * index / FFT_SIZE)
));

function monoSamples(buffer, startSeconds) {
  if (!buffer || !Number.isFinite(startSeconds) || buffer.sampleRate <= 0 || buffer.numberOfChannels < 1) {
    return new Float32Array();
  }
  const start = Math.floor(Math.max(0, startSeconds) * buffer.sampleRate);
  const stride = buffer.sampleRate / SAMPLE_RATE;
  const count = Math.floor(Math.min(
    (buffer.length - start) / stride, STYLE_WINDOW_SECONDS * SAMPLE_RATE
  ));
  if (count < MIN_SECONDS * SAMPLE_RATE) return new Float32Array();

  const channels = Array.from({ length: buffer.numberOfChannels }, (_, index) => (
    buffer.getChannelData(index)
  ));
  const samples = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    const first = start + Math.floor(i * stride);
    const next = start + Math.floor((i + 1) * stride);
    const last = Math.min(buffer.length, Math.max(next, first + 1));
    let sum = 0;
    for (const channel of channels) {
      for (let j = first; j < last; j++) sum += channel[j];
    }
    samples[i] = sum / ((last - first) * channels.length);
  }
  return samples;
}

function fft(real, imaginary) {
  for (let length = 2; length <= FFT_SIZE; length *= 2) {
    const half = length / 2;
    const step = FFT_SIZE / length;
    for (let start = 0; start < FFT_SIZE; start += length) {
      for (let i = 0; i < half; i++) {
        const angle = i * step;
        const left = start + i;
        const right = left + half;
        const offsetReal = real[right] * twiddleReal[angle] - imaginary[right] * twiddleImaginary[angle];
        const offsetImaginary = real[right] * twiddleImaginary[angle] + imaginary[right] * twiddleReal[angle];
        real[right] = real[left] - offsetReal;
        imaginary[right] = imaginary[left] - offsetImaginary;
        real[left] += offsetReal;
        imaginary[left] += offsetImaginary;
      }
    }
  }
}

function quantile(ordered, fraction) {
  return ordered[Math.round(fraction * (ordered.length - 1))];
}

function moments(values) {
  const mean = values.reduce((total, value) => total + value, 0) / values.length;
  const variance = values.reduce((total, value) => total + (value - mean) ** 2, 0) / values.length;
  return [mean, Math.sqrt(variance)];
}

export function extractStyleFeatures(buffer, startSeconds = 0) {
  const samples = monoSamples(buffer, startSeconds);
  if (!samples.length) return null;
  const power = samples.reduce((total, value) => total + value ** 2, 0) / samples.length;
  if (!Number.isFinite(power) || power < 2.5e-7) return null;

  const levels = [];
  const centroids = [];
  const fluxes = [];
  const flatnesses = [];
  const changes = [];
  const bandTotals = new Float64Array(BAND_LIMITS.length);
  const real = new Float64Array(FFT_SIZE);
  const imaginary = new Float64Array(FFT_SIZE);
  const magnitude = new Float64Array(FFT_SIZE / 2);
  const lastMagnitude = new Float64Array(FFT_SIZE / 2);
  let lastLevel = null;
  let quietFrames = 0;

  for (let start = 0; start + FFT_SIZE <= samples.length; start += HOP) {
    imaginary.fill(0);
    let energy = 0;
    for (let i = 0; i < FFT_SIZE; i++) {
      const value = samples[start + i];
      energy += value * value;
      real[reverseBits[i]] = value * hann[i];
    }
    const level = Math.max(-80, 10 * Math.log10(energy / FFT_SIZE + 1e-12));
    const silent = level < -65;
    if (silent) quietFrames++;
    if (lastLevel !== null) changes.push(Math.abs(level - lastLevel));
    levels.push(level);
    lastLevel = level;
    if (silent) continue;

    fft(real, imaginary);
    let magnitudeTotal = 0;
    let spectralLog = 0;
    let weighted = 0;
    let spectralPower = 0;
    for (let bin = 1; bin < magnitude.length; bin++) {
      const amplitude = Math.hypot(real[bin], imaginary[bin]);
      magnitude[bin] = amplitude;
      magnitudeTotal += amplitude;
      spectralLog += Math.log(amplitude + 1e-8);
      weighted += bin * amplitude;
      spectralPower += amplitude ** 2;
    }

    centroids.push(weighted / magnitudeTotal / magnitude.length);
    flatnesses.push(Math.exp(spectralLog / (magnitude.length - 1)) / (magnitudeTotal / (magnitude.length - 1)));
    const couldMeasureFlux = centroids.length > 1;
    let band = 0;
    let flux = 0;
    for (let bin = 1; bin < magnitude.length; bin++) {
      if (bin >= BAND_LIMITS[band]) band++;
      bandTotals[band] += magnitude[bin] ** 2 / spectralPower;
      const normalized = magnitude[bin] / magnitudeTotal;
      if (couldMeasureFlux) flux += Math.max(0, normalized - lastMagnitude[bin]);
      lastMagnitude[bin] = normalized;
    }
    if (couldMeasureFlux) fluxes.push(flux);
  }

  if (centroids.length < 3) return null;
  const sortedLevels = levels.sort((a, b) => a - b);
  const sortedFluxes = fluxes.sort((a, b) => a - b);
  const sortedChanges = changes.sort((a, b) => a - b);
  const [meanCentroid, spreadCentroid] = moments(centroids);
  const [meanFlatness, spreadFlatness] = moments(flatnesses);
  const [meanFlux] = moments(fluxes);
  const [meanChange] = moments(changes);
  return [
    10 * Math.log10(power),
    quantile(sortedLevels, 0.5),
    quantile(sortedLevels, 0.9) - quantile(sortedLevels, 0.1),
    quietFrames / levels.length,
    meanCentroid,
    spreadCentroid,
    meanFlatness,
    spreadFlatness,
    meanFlux,
    quantile(sortedFluxes, 0.9),
    meanChange,
    quantile(sortedChanges, 0.9),
    ...bandTotals.slice(1).map(total => Math.log10(total / centroids.length + 1e-6)),
  ];
}

function finiteVector(value, length) {
  return Array.isArray(value) && value.length === length && value.every(Number.isFinite);
}

export async function loadStyleModel(url = new URL('./model.json', import.meta.url)) {
  const response = await fetch(url);
  if (!response.ok) throw new Error('Scene model unavailable');
  const weights = await response.json();
  if (weights?.format !== 'beat-runner-style-v1'
      || weights.windowSeconds !== STYLE_WINDOW_SECONDS
      || !Array.isArray(weights.styleNames)
      || weights.styleNames.length !== STYLES.length
      || !STYLES.every((style, index) => weights.styleNames[index] === style)
      || !finiteVector(weights.mean, FEATURE_COUNT)
      || !finiteVector(weights.scale, FEATURE_COUNT)
      || !weights.scale.every(value => value > 0)
      || !finiteVector(weights.bias, STYLES.length)
      || !Array.isArray(weights.weights)
      || weights.weights.length !== STYLES.length
      || !weights.weights.every(row => finiteVector(row, FEATURE_COUNT))) {
    throw new Error('Scene model format is invalid');
  }
  return weights;
}

export function predictStyle(model, buffer, startSeconds = 0) {
  const features = model && extractStyleFeatures(buffer, startSeconds);
  if (!features) {
    return { style: 'groove', confidence: 0, probabilities: { drift: 1 / 3, groove: 1 / 3, surge: 1 / 3 } };
  }
  const standardized = features.map((feature, index) => (
    Math.min(5, Math.max(-5, (feature - model.mean[index]) / model.scale[index]))
  ));
  const logits = model.weights.map((row, index) => (
    standardized.reduce((total, feature, column) => total + feature * row[column], model.bias[index])
  ));
  const peak = Math.max(...logits);
  const likelihoods = logits.map(value => Math.exp(value - peak));
  const total = likelihoods.reduce((sum, value) => sum + value, 0);
  const distribution = likelihoods.map(value => value / total);
  const winner = logits.indexOf(peak);
  return {
    style: STYLES[winner],
    confidence: distribution[winner],
    probabilities: Object.fromEntries(STYLES.map((style, index) => [style, distribution[index]])),
  };
}
