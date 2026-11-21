export class AudioTransport {
  #context
  #gain
  #source
  #buffer
  #offset = 0
  #startedAt = 0
  #muted = false

  async unlock() {
    if (!this.#context) {
      this.#context = new AudioContext()
      this.#gain = this.#context.createGain()
      this.#gain.gain.value = this.#muted ? 0 : 0.78
      this.#gain.connect(this.#context.destination)
    }
    if (this.#context.state !== 'running') await this.#context.resume()
  }

  play(buffer, { restart = false } = {}) {
    this.pause()
    if (restart || this.#buffer !== buffer) this.#offset = 0
    this.#buffer = buffer
    this.#source = this.#context.createBufferSource()
    this.#source.buffer = buffer
    this.#source.loop = true
    this.#source.connect(this.#gain)
    this.#startedAt = this.#context.currentTime
    this.#source.start(this.#startedAt, this.#offset)
  }

  get time() {
    if (!this.#source) return this.#offset
    const output = this.#context.getOutputTimestamp?.().contextTime
    const clock = output > 0 ? output : this.#context.currentTime
    const elapsed = Math.max(0, clock - this.#startedAt)
    return (this.#offset + elapsed) % this.#buffer.duration
  }

  pause() {
    if (!this.#source) return
    this.#offset = this.time
    this.#source.stop()
    this.#source.disconnect()
    this.#source = undefined
  }

  set muted(muted) {
    this.#muted = muted
    if (this.#gain) this.#gain.gain.value = muted ? 0 : 0.78
  }
}

export function analyzeAudio(audio, signal) {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(signal.reason)
      return
    }
    const worker = new Worker(new URL('./analysis-worker.js', import.meta.url), { type: 'module' })
    const cleanup = () => {
      worker.terminate()
      signal.removeEventListener('abort', abort)
    }
    const abort = () => {
      cleanup()
      reject(signal.reason)
    }
    worker.onmessage = ({ data }) => {
      cleanup()
      if (data.error) reject(new Error(data.error))
      else resolve(data)
    }
    worker.onerror = () => {
      cleanup()
      reject(new Error('The audio analyzer could not start.'))
    }
    signal.addEventListener('abort', abort, { once: true })
    try {
      const channels = Array.from({ length: Math.min(2, audio.numberOfChannels) }, (_, index) => audio.getChannelData(index).slice())
      worker.postMessage({ channels, sampleRate: audio.sampleRate }, channels.map(channel => channel.buffer))
    } catch (error) {
      cleanup()
      reject(error)
    }
  })
}
