import { analyzeAudio, AudioTransport } from './audio.js'
import { createDemoTrack, demoTracks } from './demo-tracks.js'
import { createRun, jump, step } from './runner.js'
import { drawScene } from './scene.js'
import { musicFrame, SCENE_LABELS } from './timeline.js'

const ids = ['world', 'stage', 'score', 'best', 'beat-light', 'scene-name', 'game-message',
  'game-title', 'action', 'track-name', 'tempo', 'confidence', 'jump', 'pause', 'mute',
  'audio-file', 'track-list', 'feedback']
const ui = Object.fromEntries(ids.map(id => [id, document.getElementById(id)]))
const context = ui.world.getContext('2d', { alpha: false })
const motion = matchMedia('(prefers-reduced-motion: reduce)')
const transport = new AudioTransport()
const fileLimit = 35 * 1024 * 1024
const durationLimit = 5 * 60
const storageKey = 'raptor-radio-best'
const trackButtons = new Map()
const center = () => new OfflineAudioContext(1, 1, 22050)
const scoreText = value => String(value).padStart(5, '0')
let run = createRun()
let track = null
let loading = null
let starting = null
let muted = false
let best = 0
let lastFrameTime
let lastFrame
try {
  best = Math.max(0, Number(localStorage.getItem(storageKey)) || 0)
} catch {
  // Storage can be unavailable in private or sandboxed browser tabs.
}
ui.best.textContent = scoreText(best)
ui.stage.tabIndex = -1

function feedback(message = '', error = false) {
  ui.feedback.textContent = message
  ui.feedback.dataset.error = String(error)
}

function updateControls() {
  ui.action.disabled = !track || !!loading || starting
  ui.pause.disabled = run.phase !== 'running'
  ui.jump.disabled = run.phase !== 'running'
  ui['game-message'].hidden = run.phase === 'running'
  const [title, action] = {
    ready: ['Ready to run?', 'Start run'],
    running: ['', ''],
    paused: ['Paused', 'Resume run'],
    over: ['Run over', 'Run again'],
  }[run.phase]
  ui['game-title'].textContent = title
  ui.action.textContent = action
}

function updateTrack() {
  for (const [id, button] of trackButtons) {
    button.setAttribute('aria-pressed', String(id === track?.id))
  }
  ui['track-name'].textContent = track?.title ?? 'Choose a track'
  ui['track-name'].title = track?.title ?? ''
  ui.tempo.textContent = track?.analysis.bpm ? `${track.analysis.bpm} BPM` : '–'
  ui.confidence.textContent = track?.analysis.modelAvailable ? 'Ready' : 'Unavailable'
  lastFrame = undefined
}

function pauseRun() {
  if (starting) {
    starting = null
    updateControls()
  }
  if (run.phase !== 'running') return
  transport.pause()
  run.phase = 'paused'
  updateControls()
}

function saveBest() {
  if (run.score <= best) return
  best = run.score
  ui.best.textContent = scoreText(best)
  try {
    localStorage.setItem(storageKey, String(best))
  } catch {
    // Keep the current session's best even when persistence is unavailable.
  }
}

async function loadTrack(meta, getAudio) {
  const request = new AbortController()
  loading?.abort()
  loading = request
  pauseRun()
  feedback(`Analyzing ${meta.title} on this Mac…`)
  updateControls()
  try {
    const audio = await getAudio()
    if (request.signal.aborted) return
    if (!audio.duration || audio.duration > durationLimit) {
      throw new Error('Choose a non-empty track up to 5 minutes long.')
    }
    const analysis = await analyzeAudio(audio, request.signal)
    if (request.signal.aborted) return
    saveBest()
    track = { ...meta, audio, analysis }
    run = createRun()
    updateTrack()
    const notes = []
    if (!analysis.modelAvailable) notes.push('The style model is unavailable.')
    if (!analysis.beats.length) notes.push('No steady beat detected.')
    feedback(notes.join(' '))
  } catch (error) {
    if (request.signal.aborted) return
    if (!track) updateTrack()
    feedback(error.message, true)
  } finally {
    if (loading === request) {
      loading = null
      updateControls()
    }
  }
}

function useDemo(demo) {
  if (track?.id === demo.id && !loading) return
  void loadTrack(demo, () => createDemoTrack(center(), demo.id))
}

async function startRun() {
  if (!track || loading || starting || run.phase === 'running') return
  const selected = track
  const resume = run.phase === 'paused'
  const request = {}
  starting = request
  updateControls()
  try {
    await transport.unlock()
    if (starting !== request || document.hidden || track !== selected || loading) return
    transport.play(selected.audio, { restart: !resume })
    if (!resume) run = createRun()
    run.phase = 'running'
    ui.stage.focus({ preventScroll: true })
    lastFrameTime = undefined
  } catch {
    if (starting === request) feedback('Audio playback could not start. Try pressing Start again.', true)
  } finally {
    if (starting === request) {
      starting = null
      updateControls()
    }
  }
}

function jumpOrStart() {
  if (run.phase === 'running') jump(run)
  else void startRun()
}

for (const demo of demoTracks) {
  const button = document.createElement('button')
  const art = document.createElement('span')
  const labels = document.createElement('span')
  const title = document.createElement('strong')
  const subtitle = document.createElement('small')
  button.type = 'button'
  button.className = 'track-choice'
  button.setAttribute('aria-pressed', 'false')
  button.setAttribute('aria-label', demo.title)
  art.className = 'track-cover'
  art.dataset.track = demo.id
  title.textContent = demo.title
  subtitle.textContent = demo.subtitle
  labels.append(title, subtitle)
  button.append(art, labels)
  button.addEventListener('click', () => useDemo(demo))
  ui['track-list'].append(button)
  trackButtons.set(demo.id, button)
}

ui['audio-file'].addEventListener('change', event => {
  const file = event.target.files[0]
  event.target.value = ''
  if (!file) return
  void loadTrack({ id: 'local', title: file.name }, async () => {
    if (file.size > fileLimit) throw new Error('Choose an audio file up to 35 MB.')
    try {
      return await center().decodeAudioData(await file.arrayBuffer())
    } catch {
      throw new Error('This file could not be decoded. Try a supported audio format such as MP3 or WAV.')
    }
  })
})

ui.action.addEventListener('click', startRun)
ui.jump.addEventListener('click', () => {
  jump(run)
  ui.stage.focus({ preventScroll: true })
})
ui.pause.addEventListener('click', () => {
  pauseRun()
  if (!ui.action.disabled) ui.action.focus({ preventScroll: true })
})
ui.mute.addEventListener('click', () => {
  muted = !muted
  transport.muted = muted
  ui.mute.setAttribute('aria-pressed', String(muted))
  ui.mute.setAttribute('aria-label', muted ? 'Unmute audio' : 'Mute audio')
  ui.mute.title = muted ? 'Unmute audio' : 'Mute audio'
})
ui.world.addEventListener('click', jumpOrStart)
document.addEventListener('keydown', event => {
  const target = event.target instanceof Element ? event.target : null
  if (event.repeat || target?.matches('input')) return
  if (event.code === 'ArrowUp' || (event.code === 'Space' && !target?.closest('button, label'))) {
    event.preventDefault()
    jumpOrStart()
  }
  if (event.code === 'KeyP') {
    if (run.phase === 'running') pauseRun()
    else if (run.phase === 'paused') void startRun()
  }
  if (event.code === 'Escape') pauseRun()
})
document.addEventListener('visibilitychange', () => {
  if (document.hidden) pauseRun()
})
window.addEventListener('pagehide', saveBest)

function frame(now) {
  const previous = lastFrameTime ?? now
  lastFrameTime = now
  if (run.phase === 'running') {
    step(run, (now - previous) / 1000)
    if (run.phase === 'over') {
      transport.pause()
      saveBest()
      updateControls()
    }
  }
  const position = run.phase === 'ready' ? 0 : transport.time
  const signal = track ? musicFrame(track.analysis, position) : { theme: 'neutral', pulse: 0, confidence: 0 }
  const pulse = run.phase === 'running' && !motion.matches ? signal.pulse : 0
  const theme = signal.theme
  const count = scoreText(run.score)
  if (ui.score.textContent !== count) ui.score.textContent = count
  if (lastFrame?.theme !== theme) ui['scene-name'].textContent = SCENE_LABELS[theme]
  if (track?.analysis.modelAvailable) {
    const confidence = signal.confidence ? `${Math.round(signal.confidence * 100)}%` : 'Low signal'
    if (ui.confidence.textContent !== confidence) ui.confidence.textContent = confidence
  }
  ui['beat-light'].classList.toggle('pulse', pulse > 0.5)
  // Leave ready and paused frames intact until the track or game state changes.
  if (lastFrame?.run !== run || lastFrame.phase !== run.phase || lastFrame.theme !== theme || run.phase === 'running') {
    drawScene(context, { ...run, theme, pulse })
    lastFrame = { run, phase: run.phase, theme }
  }
  requestAnimationFrame(frame)
}

requestAnimationFrame(frame)
useDemo(demoTracks[0])
