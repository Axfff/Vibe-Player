import assert from 'node:assert/strict'
import test from 'node:test'
import { createRun, GROUND, jump, step } from '../public/runner.js'
import { musicFrame } from '../public/timeline.js'

const tick = 1 / 120

test('the runner starts grounded, counts distance while active, and stops on collision', () => {
  const run = createRun()
  assert.equal(run.player.y + run.player.h, GROUND)
  assert.equal(jump(run), false)
  step(run, tick)
  assert.equal(run.travel, 0)
  run.phase = 'running'
  step(run, 0)
  assert.equal(run.travel, 0)
  for (let i = 0; i < 600 && run.phase === 'running'; i++) step(run, tick)
  assert.equal(run.phase, 'over')
  assert.ok(run.score > 0)
  const finalScore = run.score
  step(run, 1)
  assert.equal(run.score, finalScore)
  assert.equal(createRun().score, 0)
})

test('jump clears a reachable obstacle at both normal and maximum speed', () => {
  for (const time of [0, 75]) {
    const run = createRun()
    run.phase = 'running'
    run.time = time
    const speed = Math.min(224, 118 + time * 2.5)
    run.obstacles[0].x = run.player.x + 22 + speed * 0.24
    assert.equal(jump(run), true)
    step(run, tick)
    assert.equal(jump(run), false)
    for (let i = 0; i < 105; i++) step(run, tick)
    assert.equal(run.phase, 'running')
    assert.equal(run.player.y + run.player.h, GROUND)
    assert.ok(run.obstacles.length > 1)
  }
})

test('long animation frames cannot jump through an obstacle', () => {
  const run = createRun()
  run.phase = 'running'
  run.obstacles[0].x = 105
  step(run, 10)
  assert.equal(run.phase, 'over')
  assert.ok(run.time < 0.04)
})

test('the playback timeline changes scene and pulses at the actual audio positions', () => {
  const analysis = {
    beats: [0.5, 1, 1.5, 8.5, 16.5],
    styles: [
      { start: 0, style: 'drift', confidence: 0.84 },
      { start: 8, style: 'surge', confidence: 0.72 },
      { start: 16, style: 'groove', confidence: 0.44 },
    ],
  }
  assert.deepEqual(musicFrame(analysis, 0), { theme: 'drift', pulse: 0, confidence: 0.84 })
  assert.equal(musicFrame(analysis, 0.5).pulse, 1)
  assert.ok(musicFrame(analysis, 0.6).pulse < 1)
  assert.equal(musicFrame(analysis, 0.74).pulse, 0)
  assert.equal(musicFrame(analysis, 7.99).theme, 'drift')
  assert.equal(musicFrame(analysis, 8).theme, 'drift')
  assert.deepEqual(musicFrame(analysis, 8.5), { theme: 'surge', pulse: 1, confidence: 0.72 })
  assert.equal(musicFrame({ ...analysis, beats: [] }, 8).theme, 'surge')
  assert.equal(musicFrame(analysis, 16).theme, 'surge')
  assert.deepEqual(musicFrame(analysis, 16.5), { theme: 'neutral', pulse: 1, confidence: 0.44 })
  assert.deepEqual(musicFrame({ beats: [], styles: [] }, 0), { theme: 'neutral', pulse: 0, confidence: 0 })
  assert.equal(musicFrame({ beats: [], styles: [{ start: 0, style: 'unknown', confidence: 1 }] }, 0).theme, 'neutral')
})
