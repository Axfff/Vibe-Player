import assert from 'node:assert/strict'
import test from 'node:test'

const flush = () => new Promise(resolve => setImmediate(resolve))
const analysis = { bpm: 86, beats: [0, 0.7], styles: [{ start: 0, style: 'drift', confidence: 0.85 }], modelAvailable: true }
const gradient = () => ({ addColorStop() {} })
const canvas = new Proxy({ createLinearGradient: gradient, createRadialGradient: gradient }, {
  get(target, property) { return target[property] ?? (() => {}) },
})

class Element {
  children = []
  listeners = {}
  attributes = {}
  classes = new Set()
  classList = { toggle: (name, enabled) => enabled ? this.classes.add(name) : this.classes.delete(name) }
  dataset = {}

  addEventListener(name, fn) { this.listeners[name] = fn }
  getContext() { return canvas }
  setAttribute(key, value) { this.attributes[key] = value }
  append(...children) { this.children.push(...children) }
  focus() {}
  matches() { return false }
  closest() { return null }
}

async function fixture(context) {
  const elements = new Map()
  const document = new Element()
  const element = id => {
    if (!elements.has(id)) elements.set(id, new Element())
    return elements.get(id)
  }
  document.hidden = false
  document.getElementById = element
  document.createElement = () => new Element()
  const window = new Element()
  const unlock = Promise.withResolvers()
  const stored = new Map()
  const workers = []
  let animationFrame
  let outputTime = 0
  let starts = 0
  let stops = 0

  const globals = {
    document, window, Element,
    matchMedia: () => ({ matches: false }),
    requestAnimationFrame: callback => { animationFrame = callback },
    localStorage: { getItem: key => stored.get(key), setItem: (key, value) => stored.set(key, value) },
    OfflineAudioContext: class {
      createBuffer(channels, length, sampleRate) {
        const pcm = Array.from({ length: channels }, () => new Float32Array(length))
        return { duration: length / sampleRate, sampleRate, numberOfChannels: channels, getChannelData: i => pcm[i] }
      }
    },
    AudioContext: class {
      state = 'suspended'
      get currentTime() { return outputTime }
      async resume() { await unlock.promise; this.state = 'running' }
      createGain() { return { gain: {}, connect() {} } }
      createBufferSource() {
        return {
          connect() {}, disconnect() {},
          start() { starts++ },
          stop() { stops++ },
        }
      }
    },
    Worker: class {
      constructor() { workers.push(this) }
      postMessage() {
        if (workers.length === 1) queueMicrotask(() => this.onmessage({ data: analysis }))
      }
      terminate() { this.terminated = true }
    },
  }
  const previous = Object.fromEntries(Object.keys(globals).map(name => [name, Object.getOwnPropertyDescriptor(globalThis, name)]))
  for (const [name, value] of Object.entries(globals)) {
    Object.defineProperty(globalThis, name, { value, configurable: true, writable: true })
  }
  context.after(() => {
    for (const [name, descriptor] of Object.entries(previous)) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor)
      else delete globalThis[name]
    }
  })

  await import('../public/app.js')
  await flush()
  return {
    element, document, unlock, workers, stored,
    starts: () => starts,
    stops: () => stops,
    frame(ms) { outputTime = ms / 1000; animationFrame(ms) },
  }
}

test('browser controller integration', async context => {
  const game = await fixture(context)

  await context.test('hiding the page cancels startup while the audio context unlocks', async () => {
    const starting = game.element('action').listeners.click()
    game.document.hidden = true
    game.document.listeners.visibilitychange()
    game.unlock.resolve()
    await starting
    assert.equal(game.starts(), 0)
    assert.equal(game.element('game-title').textContent, 'Ready to run?')
    game.document.hidden = false
  })

  await context.test('an oversized local file supersedes pending analysis without replacing the loaded track', async () => {
    game.element('track-list').children[1].listeners.click()
    await flush()
    assert.equal(game.workers.length, 2)
    game.element('audio-file').listeners.change({
      target: { files: [{ name: 'oversized.wav', size: 36 * 1024 * 1024 }] },
    })
    await flush()
    assert.ok(game.workers[1].terminated)
    assert.equal(game.element('track-name').textContent, 'Afterglow')
    assert.match(game.element('feedback').textContent, /up to 35 MB/)
  })

  await context.test('real entry point starts, pauses, resumes and retries the selected track', async () => {
    const { element, document } = game
    assert.equal(element('track-name').textContent, 'Afterglow')
    assert.equal(element('tempo').textContent, '86 BPM')
    game.frame(0)
    assert.equal(element('scene-name').textContent, 'Drift')
    assert.equal(element('confidence').textContent, '85%')

    await element('action').listeners.click()
    assert.equal(game.starts(), 1)
    assert.equal(element('game-message').hidden, true)
    game.frame(0)
    game.frame(16)
    assert.ok(element('beat-light').classes.has('pulse'))
    document.listeners.keydown({ code: 'ArrowUp', target: element('stage'), preventDefault() {} })
    element('mute').listeners.click()
    assert.equal(element('mute').attributes['aria-pressed'], 'true')
    element('pause').listeners.click()
    assert.equal(game.stops(), 1)
    assert.equal(element('game-title').textContent, 'Paused')

    await element('action').listeners.click()
    for (let now = 32; now < 4000; now += 16) game.frame(now)
    assert.equal(element('game-title').textContent, 'Run over')
    assert.equal(element('best').textContent, element('score').textContent)
    assert.ok(Number(game.stored.get('raptor-radio-best')) > 0)
    assert.equal(game.starts(), 2)
    assert.equal(game.stops(), 2)
    await element('action').listeners.click()
    game.frame(4000)
    assert.equal(element('score').textContent, '00000')
  })
})
