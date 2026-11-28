const WIDTH = 480;
const HEIGHT = 222;
const GROUND = 177;

const palettes = {
  neutral: {
    sky: ['#100e27', '#302149', '#9a4e78'],
    sun: ['#ffd1a1', '#f988ad', '#b94e9b'],
    glow: 'rgba(243, 106, 169, .27)',
    ridge: ['#5c3c6c', '#3c2857', '#271a3b'],
    floor: ['#25152f', '#120d22'],
    grid: '#523060',
    rim: '#e381bd',
    beat: '#ffb6d7',
    crystal: ['#38234a', '#825899', '#d6a0ed', '#f7cdf8'],
    spike: ['#613052', '#ba5f89', '#f29bb4'],
  },
  drift: {
    sky: ['#0a152a', '#203650', '#457e89'],
    sun: ['#d8ffea', '#83dfeb', '#528eae'],
    glow: 'rgba(114, 230, 232, .26)',
    ridge: ['#365675', '#25445f', '#16273f'],
    floor: ['#162a41', '#0d1528'],
    grid: '#285070',
    rim: '#7ac9d7',
    beat: '#b3f3eb',
    crystal: ['#183b53', '#427d9b', '#74c6db', '#ccfdff'],
    spike: ['#30627a', '#538ba2', '#a6d7e0'],
  },
  groove: {
    sky: ['#1c1029', '#4a2351', '#ab5b6a'],
    sun: ['#ffecae', '#ffb477', '#f2728d'],
    glow: 'rgba(248, 126, 128, .28)',
    ridge: ['#73445e', '#512b53', '#2b1b3e'],
    floor: ['#342038', '#180f25'],
    grid: '#65364d',
    rim: '#ee956f',
    beat: '#ffcf94',
    crystal: ['#522849', '#9b537c', '#e7809e', '#ffd8b6'],
    spike: ['#673050', '#c36772', '#ffab8f'],
  },
  surge: {
    sky: ['#0c0e2a', '#342258', '#6d438b'],
    sun: ['#f5d6ff', '#c69aff', '#8c62ea'],
    glow: 'rgba(189, 117, 255, .32)',
    ridge: ['#533573', '#382757', '#211936'],
    floor: ['#272043', '#110f26'],
    grid: '#483878',
    rim: '#b387ff',
    beat: '#e6c3ff',
    crystal: ['#342358', '#7a5ab9', '#c194f7', '#efddff'],
    spike: ['#573579', '#9a5fce', '#e0a1ff'],
  },
};

const contours = {
  neutral: [
    [0, 15, 31, 26, 43, 49, 34, 47, 24, 38, 33, 13, 0],
    [12, 24, 15, 6, 26, 31, 19, 31, 37, 20, 7, 20, 12],
  ],
  drift: [
    [12, 17, 22, 19, 16, 35, 56, 33, 20, 16, 22, 24, 12],
    [10, 18, 28, 24, 17, 9, 8, 15, 19, 12, 8, 6, 10],
  ],
  groove: [
    [24, 24, 36, 36, 12, 12, 24, 42, 42, 15, 24, 24, 24],
    [8, 8, 10, 30, 30, 24, 13, 8, 27, 27, 29, 21, 8],
  ],
  surge: [
    [10, 52, 24, 51, 12, 40, 16, 58, 25, 28, 48, 15, 10],
    [13, 30, 9, 17, 38, 20, 31, 13, 9, 22, 15, 32, 13],
  ],
};

const stars = Array.from({ length: 52 }, (_, index) => ({
  x: (index * 73 + index * index * 11 + 41) % WIDTH,
  y: 13 + (index * 29 + index * index * 3) % 115,
  bright: index % 9 === 0,
  dim: index % 4 === 0,
}));

function drawSky(ctx, palette, travel) {
  const sky = ctx.createLinearGradient(0, 0, 0, GROUND);
  sky.addColorStop(0, palette.sky[0]);
  sky.addColorStop(0.58, palette.sky[1]);
  sky.addColorStop(1, palette.sky[2]);
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, WIDTH, GROUND);

  ctx.fillStyle = palette.beat;
  for (const { x, y, bright, dim } of stars) {
    const px = Math.round((x + WIDTH - (travel * 0.028) % WIDTH) % WIDTH);
    ctx.globalAlpha = dim ? 0.22 : 0.54;
    ctx.fillRect(px, y, 1, 1);
    if (bright) {
      ctx.globalAlpha = 0.3;
      ctx.fillRect(px - 2, y, 5, 1);
      ctx.fillRect(px, y - 2, 1, 5);
      ctx.globalAlpha = 0.9;
      ctx.fillRect(px, y, 1, 1);
    }
  }
  ctx.globalAlpha = 1;
}

function drawSun(ctx, palette, pulse, theme) {
  const x = theme === 'drift' ? 135 : 347;
  const y = 95;
  const radius = 42;

  const halo = ctx.createRadialGradient(x, y, 15, x, y, 74 + pulse * 9);
  halo.addColorStop(0, palette.glow);
  halo.addColorStop(1, 'transparent');
  ctx.fillStyle = halo;
  ctx.fillRect(x - 88, y - 88, 176, 166);

  const sun = ctx.createLinearGradient(0, y - radius, 0, y + radius);
  sun.addColorStop(0, palette.sun[0]);
  sun.addColorStop(0.52, palette.sun[1]);
  sun.addColorStop(1, palette.sun[2]);
  ctx.fillStyle = sun;
  for (let offset = -radius; offset <= radius; offset += 2) {
    if (offset > 3 && offset % (offset > 23 ? 8 : 6) !== 0) continue;
    const half = Math.floor(Math.sqrt(radius * radius - offset * offset));
    ctx.fillRect(x - half, y + offset, 2 * half + 1, 2);
  }
}

function ridgeHeight(profile, x, offset) {
  const wrapped = ((x + offset) % WIDTH + WIDTH) % WIDTH;
  const point = wrapped * (profile.length - 1) / WIDTH;
  const left = Math.floor(point);
  return Math.round(profile[left] + (profile[left + 1] - profile[left]) * (point - left));
}

function drawRidge(ctx, profile, baseline, offset, color) {
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(0, GROUND);
  for (let x = 0; x < WIDTH; x += 3) {
    const y = baseline - ridgeHeight(profile, x, offset);
    ctx.lineTo(x, y);
    ctx.lineTo(x + 3, y);
  }
  ctx.lineTo(WIDTH, GROUND);
  ctx.closePath();
  ctx.fill();
}

function drawTerrain(ctx, theme, palette, travel, pulse) {
  const [back, front] = contours[theme];
  drawRidge(ctx, back, 161, travel * 0.065, palette.ridge[0]);
  drawRidge(ctx, front, 177, travel * 0.135, palette.ridge[1]);

  // A low third silhouette gives the larger ridges a clear horizon.
  ctx.fillStyle = palette.ridge[2];
  for (let x = 0; x < WIDTH; x += 4) {
    const y = 11 + Math.floor(ridgeHeight(back, x, travel * 0.21 + 113) / 7);
    ctx.fillRect(x, GROUND - y, 4, y);
  }

  ctx.fillStyle = palette.beat;
  ctx.globalAlpha = 0.12 + pulse * 0.38;
  for (let index = 0; index < 28; index += 1) {
    const x = Math.round((index * 19 + WIDTH - (travel * 0.31) % WIDTH) % WIDTH);
    const h = 2 + (index * 11 % 7) * pulse;
    ctx.fillRect(x, GROUND - 3 - Math.round(h), 1, Math.round(h));
    if (index % 4 === 0) ctx.fillRect(x + 3, GROUND - 4, 3, 1);
  }
  ctx.globalAlpha = 1;
}

function drawGround(ctx, palette, travel, pulse) {
  const floor = ctx.createLinearGradient(0, GROUND, 0, HEIGHT);
  floor.addColorStop(0, palette.floor[0]);
  floor.addColorStop(1, palette.floor[1]);
  ctx.fillStyle = floor;
  ctx.fillRect(0, GROUND, WIDTH, HEIGHT - GROUND);

  ctx.fillStyle = palette.grid;
  for (const row of [6, 13, 25, 42]) ctx.fillRect(0, GROUND + row, WIDTH, 1);

  ctx.strokeStyle = palette.grid;
  ctx.lineWidth = 1;
  ctx.beginPath();
  const slide = travel % 58;
  for (let index = -2; index <= 10; index += 1) {
    const start = index * 58 - slide;
    ctx.moveTo(start, GROUND + 2);
    ctx.lineTo(WIDTH / 2 + (start - WIDTH / 2) * 1.5, HEIGHT);
  }
  ctx.stroke();

  ctx.fillStyle = palette.rim;
  ctx.globalAlpha = 0.28 + pulse * 0.32;
  const streakOffset = Math.round((travel * 1.2) % 120);
  for (let index = 0; index < 9; index += 1) {
    const x = (index * 31 + WIDTH - streakOffset) % WIDTH;
    const y = GROUND + 5 + (index * 11) % 39;
    ctx.fillRect(x, y, 4 + index % 4, 1);
  }

  ctx.globalAlpha = 0.6 + pulse * 0.3;
  ctx.fillRect(0, GROUND - 1, WIDTH, 1);
  ctx.globalAlpha = 0.24 + pulse * 0.21;
  ctx.fillRect(0, GROUND, WIDTH, 2);
  ctx.globalAlpha = 1;
}

function drawObstacle(ctx, { x, y, w, h, kind }, palette) {
  x = Math.round(x);
  y = Math.round(y);
  ctx.fillStyle = palette.floor[1];
  ctx.fillRect(x - 2, y + h - 1, w + 5, 3);
  if (kind === 'crystal') {
    const [shadow, body, face, glint] = palette.crystal;
    const left = Math.round(w * 0.24);
    const right = Math.round(w * 0.65);
    const heel = Math.round(h * 0.48);
    ctx.fillStyle = shadow;
    ctx.fillRect(x, y + heel, w, h - heel);
    ctx.fillStyle = body;
    ctx.fillRect(x + 1, y + heel, left, h - heel - 2);
    ctx.fillRect(x + right, y + 5, w - right - 1, h - 6);
    ctx.fillRect(x + left, y + 2, right - left + 1, h - 3);
    ctx.fillStyle = face;
    ctx.fillRect(x + left + 2, y, Math.max(1, right - left - 1), h - 3);
    ctx.fillRect(x + right, y + 5, 2, h - 8);
    ctx.fillStyle = glint;
    ctx.fillRect(x + left + 2, y + 2, 1, h - 9);
    return;
  }

  const [shadow, body, face] = palette.spike;
  for (let row = 0; row < h; row += 1) {
    const left = Math.round((1 - row / h) * w * 0.46);
    const right = Math.round((1 - row / h) * w * 0.35);
    ctx.fillStyle = shadow;
    ctx.fillRect(x + left, y + row, w - left - right, 1);
    ctx.fillStyle = body;
    ctx.fillRect(x + left, y + row, Math.ceil((w - left - right) * 0.68), 1);
    ctx.fillStyle = face;
    ctx.fillRect(x + left, y + row, 1 + Math.floor(row / 10), 1);
  }
}

const raptorColors = {
  D: '#466135',
  G: '#87ad4b',
  L: '#dcff73',
  H: '#f4ffb0',
  E: '#241837',
  S: '#ee82a8',
  V: '#b34b82',
};

const raptorRows = [
  '...............HHHHHH....',
  '.............HHLLLLLLHH..',
  '............HLLLLLLLLLLH.',
  '...........HLLLLLLLLLLLL.',
  '...........HLLHEHLLLLLLLL',
  '...........HLLHEHLLLLLLLL',
  '...........HLLLLLLLLLLLLL',
  '...........HLLLLLLLLLLLLL',
  '...........HLLLLLGGGGGGG.',
  '..........HLLLLLLGEEL....',
  '..........HLLLLLLLLLLL...',
  '..........SSSSSSVVVVV....',
  '.........SSVVSSVG........',
  'H......HHLLLLLGLG........',
  'HL...HLLLLLLGLGGD........',
  'HLLLLLLLLLLGLGGGGD.......',
  '.HLLLLLLLLLGLGGGGGD......',
  '..HLLLLLLLLLGGGGDD.......',
  '....HLLLLLLLGGGD.........',
  '......DGGGGGGGD..........',
  '........DDGGGD...........',
].map((pixels) => [...pixels.matchAll(/([A-Z])\1*/g)].map((run) => ({
  x: run.index,
  width: run[0].length,
  color: raptorColors[run[1]],
})));

function drawRaptor(ctx, player, travel, phase, palette) {
  const x = Math.round(player.x);
  const lift = Math.max(0, GROUND - player.y - player.h);
  const stride = phase === 'ready' ? 0 : Math.floor(travel / 7) % 4;
  const y = Math.round(player.y) + (lift === 0 && stride % 2 === 1 ? 1 : 0);
  const airborne = lift > 0;

  ctx.fillStyle = palette.floor[1];
  ctx.globalAlpha = 0.65 - Math.min(lift, 55) / 100;
  ctx.fillRect(x + 5, GROUND + 1, player.w - 2, 2);
  ctx.globalAlpha = 1;

  if (phase === 'running' && !airborne) {
    ctx.fillStyle = palette.rim;
    ctx.globalAlpha = 0.5;
    ctx.fillRect(x + 3 - stride * 2, GROUND - 3, 2, 1);
    ctx.fillRect(x + 1 - stride * 2, GROUND - 6, 1, 1);
    ctx.globalAlpha = 1;
  }

  for (const [row, strips] of raptorRows.entries()) {
    for (const { x: offset, width, color } of strips) {
      ctx.fillStyle = color;
      ctx.fillRect(x + offset, y + row, width, 1);
    }
  }

  // The scarf and feet move with distance, so pausing also freezes their pose.
  ctx.fillStyle = raptorColors.S;
  ctx.fillRect(x + 5, y + 12 + (stride % 2), 7, 2);
  ctx.fillRect(x + 2, y + 11 + (stride % 3), 5, 1);
  ctx.fillStyle = raptorColors.V;
  ctx.fillRect(x + 3, y + 13 + (stride % 2), 4, 1);

  const back = airborne ? 8 : [9, 11, 13, 10][stride];
  const front = airborne ? 16 : [15, 13, 10, 13][stride];
  const backHeight = airborne || stride === 2 ? 4 : 7 - stride % 2;
  const frontHeight = airborne || stride === 0 ? 5 : 7 - stride % 2;
  ctx.fillStyle = raptorColors.G;
  ctx.fillRect(x + back, y + 20, 3, backHeight);
  ctx.fillRect(x + back, y + 20 + backHeight, 5, 2);
  ctx.fillStyle = raptorColors.L;
  ctx.fillRect(x + front, y + 20, 3, frontHeight);
  ctx.fillRect(x + front, y + 20 + frontHeight, 5, 2);

  if (phase === 'over') {
    ctx.fillStyle = raptorColors.E;
    ctx.fillRect(x + 13, y + 3, 1, 1);
    ctx.fillRect(x + 16, y + 3, 1, 1);
    ctx.fillRect(x + 13, y + 6, 1, 1);
    ctx.fillRect(x + 16, y + 6, 1, 1);
  }
}

export function drawScene(ctx, frame) {
  const { pulse, travel, player, obstacles, phase } = frame;
  const theme = Object.hasOwn(palettes, frame.theme) ? frame.theme : 'neutral';
  const palette = palettes[theme];

  drawSky(ctx, palette, travel);
  drawSun(ctx, palette, pulse, theme);
  drawTerrain(ctx, theme, palette, travel, pulse);
  drawGround(ctx, palette, travel, pulse);
  for (const obstacle of obstacles) drawObstacle(ctx, obstacle, palette);
  drawRaptor(ctx, player, travel, phase, palette);
}
