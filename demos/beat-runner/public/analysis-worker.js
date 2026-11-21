import { detectBeats } from './beat.js'
import { loadStyleModel, predictStyle, STYLE_WINDOW_SECONDS } from './style-model.js'

self.addEventListener('message', async ({ data: { channels, sampleRate } }) => {
  try {
    const audio = {
      sampleRate,
      length: channels[0].length,
      duration: channels[0].length / sampleRate,
      numberOfChannels: channels.length,
      getChannelData: index => channels[index],
    }
    const { beats, bpm } = detectBeats(audio)
    const styles = []
    let modelAvailable = false
    try {
      const model = await loadStyleModel()
      for (let start = 0; start < Math.max(1, audio.duration - 2); start += STYLE_WINDOW_SECONDS) {
        styles.push({ start, ...predictStyle(model, audio, start) })
      }
      modelAvailable = true
    } catch (error) {
      console.error('Style inference unavailable', error)
      styles.length = 0
    }
    self.postMessage({ beats, bpm, styles, modelAvailable })
  } catch (error) {
    self.postMessage({ error: error.message })
  }
})
