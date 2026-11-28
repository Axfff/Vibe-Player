import assert from 'node:assert/strict'
import test from 'node:test'
import { analyzeAudio, AudioTransport } from '../public/audio.js'

test('transport loops on the output clock, pauses in place, resumes and restarts', async context => {
  const buffer = { duration: 8 }
  const sources = []
  const gain = { gain: { value: 1 }, connect() {} }
  let audio
  class TestContext {
    state = 'suspended'
    destination = {}
    currentTime = 10
    outputTime = 10

    constructor() { audio = this }
    createGain() { return gain }
    async resume() { this.state = 'running' }
    getOutputTimestamp() { return { contextTime: this.outputTime } }
    createBufferSource() {
      const source = {
        connect() {},
        disconnect() { this.disconnected = true },
        start(time, offset) { this.startArgs = [time, offset] },
        stop() { this.stopped = true },
      }
      sources.push(source)
      return source
    }
  }
  const previous = globalThis.AudioContext
  globalThis.AudioContext = TestContext
  context.after(() => { globalThis.AudioContext = previous })
  const transport = new AudioTransport()
  transport.muted = true
  assert.equal(transport.time, 0)
  transport.pause()
  await transport.unlock()
  assert.equal(audio.state, 'running')
  assert.equal(gain.gain.value, 0)
  transport.muted = false
  assert.equal(gain.gain.value, 0.78)

  transport.play(buffer)
  assert.equal(sources[0].loop, true)
  assert.deepEqual(sources[0].startArgs, [10, 0])
  audio.currentTime = 21
  audio.outputTime = 20
  assert.equal(transport.time, 2)
  transport.pause()
  assert.ok(sources[0].stopped && sources[0].disconnected)
  audio.outputTime = 21
  assert.equal(transport.time, 2)
  transport.play(buffer)
  assert.deepEqual(sources[1].startArgs, [21, 2])
  transport.play(buffer, { restart: true })
  assert.deepEqual(sources[2].startArgs, [21, 0])
  audio.outputTime = 0
  audio.currentTime = 22
  assert.equal(transport.time, 1)
  await transport.unlock()
  transport.play({ duration: 2 })
  assert.deepEqual(sources[3].startArgs, [22, 0])
})

test('analysis copies PCM and terminates workers after success, failure and cancellation', async context => {
  const created = []
  const previous = globalThis.Worker
  globalThis.Worker = class TestWorker {
    constructor(url, options) {
      assert.ok(url.href.endsWith('/analysis-worker.js'))
      assert.equal(options.type, 'module')
      created.push(this)
    }
    postMessage(data, transfers) {
      this.data = data
      this.transfers = transfers
    }
    terminate() { this.terminated = true }
  }
  context.after(() => { globalThis.Worker = previous })
  const pcm = new Float32Array([0, 1, 0])
  const audio = { numberOfChannels: 4, sampleRate: 22050, getChannelData: () => pcm }
  const request = new AbortController()
  const promise = analyzeAudio(audio, request.signal)
  assert.equal(created[0].data.channels.length, 2)
  assert.notEqual(created[0].transfers[0], pcm.buffer)
  assert.deepEqual(created[0].data.channels[0], pcm)
  const result = { beats: [0], bpm: 90, styles: [] }
  created[0].onmessage({ data: result })
  assert.equal(await promise, result)
  assert.equal(created[0].terminated, true)

  const failed = analyzeAudio(audio, request.signal)
  created[1].onmessage({ data: { error: 'bad PCM' } })
  await assert.rejects(failed, /bad PCM/)
  assert.equal(created[1].terminated, true)

  const crashed = analyzeAudio(audio, request.signal)
  created[2].onerror()
  await assert.rejects(crashed, /analyzer could not start/)
  assert.equal(created[2].terminated, true)

  const canceled = analyzeAudio(audio, request.signal)
  request.abort()
  await assert.rejects(canceled, { name: 'AbortError' })
  assert.equal(created[3].terminated, true)
  await assert.rejects(analyzeAudio(audio, request.signal), { name: 'AbortError' })
  assert.equal(created.length, 4)

  const brokenAudio = { ...audio, getChannelData() { throw new Error('cannot copy') } }
  await assert.rejects(analyzeAudio(brokenAudio, new AbortController().signal), /cannot copy/)
  assert.equal(created[4].terminated, true)
})
