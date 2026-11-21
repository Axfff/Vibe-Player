import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { musicFrame } from '../public/timeline.js'

const modelUrl = new URL('../public/model.json', import.meta.url)
const model = JSON.parse(await readFile(modelUrl, 'utf8'))
const sampleRate = 12000
let receive

function delayedPulseTrack() {
  const audio = new Float32Array(18 * sampleRate)
  for (let beat = 8; beat < 18; beat += 0.5) {
    const start = beat * sampleRate
    for (let i = 0; i < 0.07 * sampleRate; i++) {
      const time = i / sampleRate
      audio[start + i] = 0.45 * Math.sin(2 * Math.PI * 100 * time) * Math.exp(-55 * time)
    }
  }
  return audio
}

async function analyzeInWorker(t, channels, { modelExists = true } = {}) {
  const messages = []
  const previousSelf = Object.getOwnPropertyDescriptor(globalThis, 'self')
  Object.defineProperty(globalThis, 'self', {
    configurable: true,
    value: {
      addEventListener(event, callback) {
        assert.equal(event, 'message')
        receive = callback
      },
      postMessage(message) {
        messages.push(message)
      },
    },
  })
  t.after(() => {
    if (previousSelf) Object.defineProperty(globalThis, 'self', previousSelf)
    else delete globalThis.self
  })
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url.href, modelUrl.href)
    return { ok: modelExists, json: async () => model }
  })
  if (!receive) await import('../public/analysis-worker.js')
  await receive({ data: { channels, sampleRate } })
  assert.equal(messages.length, 1)
  return messages[0]
}

test('worker returns aligned beats and local model predictions for successive audio sections', async t => {
  const result = await analyzeInWorker(t, [delayedPulseTrack()])
  assert.equal(result.bpm, 120)
  assert.equal(result.beats.length, 20)
  assert.ok(result.beats.every((beat, index) => Math.abs(beat - (8 + index * 0.5)) < 0.015))
  assert.equal(result.modelAvailable, true)
  assert.deepEqual(result.styles.map(window => window.start), [0, 8])
  assert.equal(musicFrame(result, 0).theme, 'neutral')
  const prediction = result.styles[1]
  assert.ok(model.styleNames.includes(prediction.style))
  assert.equal(prediction.confidence, prediction.probabilities[prediction.style])
  assert.ok(prediction.confidence > 1 / 3)
})

test('worker keeps a silent track neutral with no derived beats', async t => {
  const result = await analyzeInWorker(t, [new Float32Array(3 * sampleRate)])
  assert.equal(result.modelAvailable, true)
  assert.equal(result.bpm, null)
  assert.deepEqual(result.beats, [])
  assert.deepEqual(musicFrame(result, 0), { theme: 'neutral', pulse: 0, confidence: 0 })
})

test('worker preserves the beat analysis when model loading fails', async t => {
  const log = t.mock.method(console, 'error', () => {})
  const result = await analyzeInWorker(t, [delayedPulseTrack()], { modelExists: false })
  assert.equal(result.modelAvailable, false)
  assert.deepEqual(result.styles, [])
  assert.equal(result.bpm, 120)
  assert.equal(result.beats.length, 20)
  assert.equal(log.mock.callCount(), 1)
})

test('worker reports a malformed PCM request as a failure message', async t => {
  const result = await analyzeInWorker(t, [])
  assert.deepEqual(Object.keys(result), ['error'])
  assert.equal(typeof result.error, 'string')
  assert.ok(result.error.length)
})
