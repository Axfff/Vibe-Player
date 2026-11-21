export const GROUND = 177
const WORLD_WIDTH = 480
const BASE_SPEED = 118
const MAX_SPEED = 224
const JUMP_SPEED = 267
const GRAVITY = 730
const OBSTACLES = [
  { w: 23, h: 28, kind: 'crystal', gap: 181 },
  { w: 29, h: 23, kind: 'spike', gap: 205 },
  { w: 32, h: 32, kind: 'crystal', gap: 213 },
  { w: 22, h: 21, kind: 'spike', gap: 174 },
  { w: 27, h: 31, kind: 'crystal', gap: 198 },
]

function addObstacle(run, x) {
  const { w, h, kind } = OBSTACLES[run.nextObstacle % OBSTACLES.length]
  run.obstacles.push({ x, y: GROUND - h, w, h, kind })
  run.nextObstacle++
}

export function createRun() {
  const run = {
    phase: 'ready',
    travel: 0,
    time: 0,
    score: 0,
    velocity: 0,
    player: { x: 84, y: GROUND - 29, w: 25, h: 29 },
    obstacles: [],
    nextObstacle: 0,
  }
  addObstacle(run, 370)
  return run
}

export function jump(run) {
  if (run.phase !== 'running' || run.player.y + run.player.h < GROUND) return false
  run.velocity = -JUMP_SPEED
  return true
}

function collides(player, obstacle) {
  return player.x + player.w - 4 > obstacle.x + 3 &&
    player.x + 5 < obstacle.x + obstacle.w - 3 &&
    player.y + player.h - 3 > obstacle.y + 3 &&
    player.y + 6 < obstacle.y + obstacle.h
}

export function step(run, seconds) {
  if (run.phase !== 'running' || seconds <= 0) return
  const dt = Math.min(seconds, 1 / 30)
  run.time += dt
  const distance = Math.min(MAX_SPEED, BASE_SPEED + run.time * 2.5) * dt
  run.travel += distance
  run.score = Math.floor(run.travel / 8)
  const player = run.player
  run.velocity += GRAVITY * dt
  player.y += run.velocity * dt
  if (player.y + player.h >= GROUND) {
    player.y = GROUND - player.h
    run.velocity = 0
  }

  for (const obstacle of run.obstacles) obstacle.x -= distance
  const last = run.obstacles.at(-1)
  const nextX = last.x + OBSTACLES[run.nextObstacle % OBSTACLES.length].gap
  if (nextX <= WORLD_WIDTH + 20) addObstacle(run, nextX)
  if (run.obstacles[0].x + run.obstacles[0].w < 0) run.obstacles.shift()
  if (run.obstacles.some(obstacle => collides(player, obstacle))) run.phase = 'over'
}
