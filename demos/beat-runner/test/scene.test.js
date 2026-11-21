import assert from 'node:assert/strict';
import test from 'node:test';
import { drawScene } from '../public/scene.js';

const player = { x: 84, y: 148, w: 25, h: 29 };

function render(overrides = {}) {
  const rectangles = [];
  const gradients = [];
  const paths = [];
  let points;

  function gradient(kind, coordinates) {
    const stops = [];
    const record = { kind, coordinates, stops };
    gradients.push(record);
    return { record, addColorStop: (offset, color) => stops.push({ offset, color }) };
  }

  const ctx = {
    globalAlpha: 1,
    createLinearGradient: (...coordinates) => gradient('linear', coordinates),
    createRadialGradient: (...coordinates) => gradient('radial', coordinates),
    fillRect(x, y, w, h) {
      rectangles.push({ x, y, w, h, paint: this.fillStyle.record ?? this.fillStyle, alpha: this.globalAlpha });
    },
    beginPath() { points = []; },
    moveTo(x, y) { points.push([x, y]); },
    lineTo(x, y) { points.push([x, y]); },
    closePath() {},
    fill() { paths.push(points); },
    stroke() { paths.push(points); },
  };

  drawScene(ctx, { phase: 'ready', theme: 'neutral', pulse: 0, travel: 0, player, obstacles: [], ...overrides });
  assert.equal(ctx.globalAlpha, 1);
  return { rectangles, gradients, paths };
}

test('identical frames are deterministic and unknown scenes use the neutral fallback', () => {
  const neutral = render({ travel: 26_425 });
  assert.deepEqual(render({ travel: 26_425 }), neutral);
  assert.deepEqual(render({ theme: 'unknown', travel: 26_425 }), neutral);
});

test('every scene changes the sky and the terrain silhouette', () => {
  const themes = ['neutral', 'drift', 'groove', 'surge'];
  const frames = themes.map((theme) => render({ theme }));
  const skies = new Set(frames.map(({ gradients }) => JSON.stringify(gradients[0].stops)));
  const ridges = new Set(frames.map(({ paths }) => JSON.stringify(paths[0])));
  assert.equal(skies.size, themes.length);
  assert.equal(ridges.size, themes.length);
});

test('a beat strengthens the horizon and expands the halo without moving the raptor', () => {
  const resting = render();
  const onBeat = render({ pulse: 1 });
  const halo = ({ gradients }) => gradients.find(({ kind }) => kind === 'radial');
  const horizon = ({ rectangles }) => rectangles.find(({ y, w, h }) => y === 176 && w === 480 && h === 1);
  const raptor = ({ rectangles }) => rectangles.filter(({ paint }) => paint === '#dcff73');

  assert.ok(halo(onBeat).coordinates.at(-1) > halo(resting).coordinates.at(-1));
  assert.ok(horizon(onBeat).alpha > horizon(resting).alpha);
  assert.ok(raptor(resting).length > 0);
  assert.deepEqual(raptor(onBeat), raptor(resting));
});

test('draws full-stage sky and ground and uses valid geometry for both obstacle types', () => {
  const obstacles = [
    { x: 287.2, y: 151, w: 20, h: 26, kind: 'crystal' },
    { x: 392, y: 162, w: 16, h: 15, kind: 'spike' },
    { x: -8, y: 162, w: 16, h: 15, kind: 'spike' },
  ];
  const { rectangles, paths } = render({ obstacles, travel: 99_999 });

  const geometry = ({ x, y, w, h }) => [x, y, w, h];
  assert.deepEqual(geometry(rectangles[0]), [0, 0, 480, 177]);
  assert.ok(rectangles.some(({ x, y, w, h }) => x === 0 && y === 177 && w === 480 && h === 45));
  assert.ok(rectangles.every((rect) => geometry(rect).every(Number.isFinite) && rect.w > 0 && rect.h > 0));
  assert.ok(paths.flat(2).every(Number.isFinite));

  for (const { x, y, w, h } of obstacles) {
    const top = rectangles.find((rect) => rect.y === y && rect.x >= Math.round(x) && rect.x < x + w);
    assert.ok(top, `Missing obstacle top at ${x}, ${y}`);
    assert.ok(top.w < w && top.y + top.h <= y + h);
  }
});

test('distance controls the grounded pose, with a separate jump and crash pose', () => {
  const paused = render({ phase: 'paused', travel: 21 });
  const running = render({ phase: 'running', travel: 21 });
  const alternate = render({ phase: 'running', travel: 14 });
  const airborne = render({ phase: 'running', travel: 21, player: { ...player, y: 86 } });
  const crashed = render({ phase: 'over', travel: 21 });
  const raptor = ({ rectangles }) => rectangles.filter(({ paint }) => paint === '#dcff73' || paint === '#241837');

  assert.deepEqual(raptor(paused), raptor(running));
  assert.notDeepEqual(raptor(alternate), raptor(running));
  assert.ok(raptor(airborne).every(({ y }) => y < player.y));
  assert.notDeepEqual(raptor(crashed), raptor(paused));
});
