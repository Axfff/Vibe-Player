import test from "node:test";
import assert from "node:assert/strict";
import { PreviewController } from "../static/preview.mjs";

const valid = () => ({
  track: {
    capability: {
      mode: "IN_APP_PREVIEW",
      previewUrl: "https://prod-1.storage.jamendo.com/?trackid=201",
      expiresAt: new Date(60000).toISOString(),
      maxPreviewSeconds: 30,
    },
  },
});
function fixture(fetchCapability = async () => valid()) {
  const state = {
    time: 0,
    audios: [],
    errors: [],
    delay: null,
    callback: null,
  };
  const controller = new PreviewController({
    fetchCapability,
    createAudio: () => {
      const a = {
        currentTime: 0,
        play: async () => {
          a.played = true;
          a.onplay?.();
        },
        pause: () => {
          a.paused = true;
          a.onpause?.();
        },
        removeAttribute: () => {
          a.released = true;
        },
        load: () => {},
        remove: () => {},
      };
      state.audios.push(a);
      return a;
    },
    onStart: () => {},
    onStop: () => {},
    onError: (e) => state.errors.push(e),
    now: () => state.time,
    schedule: (fn, delay) => {
      state.callback = fn;
      state.delay = delay;
      return 1;
    },
    cancel: () => {
      state.callback = null;
    },
  });
  return { controller, state };
}
test("click starts one stream; switch releases the old stream", async () => {
  const { controller, state } = fixture();
  assert.equal(state.audios.length, 0);
  await controller.play({});
  await controller.play({});
  assert.equal(state.audios.length, 2);
  assert.equal(state.audios[0].released, true);
  assert.equal(state.audios[0].onended, null);
  controller.stop();
  assert.equal(state.audios[1].released, true);
});
test("late capability response after stop never starts audio", async () => {
  let resolve;
  const { controller, state } = fixture(
    () => new Promise((r) => (resolve = r)),
  );
  const work = controller.play({});
  controller.stop();
  resolve(valid());
  await work;
  assert.equal(state.audios.length, 0);
});
test("pause/resume preserves the total 30-second budget", async () => {
  const { controller, state } = fixture();
  await controller.play({});
  assert.equal(state.delay, 30000);
  state.time = 12000;
  state.audios[0].pause();
  state.time = 50000;
  await state.audios[0].play();
  assert.equal(state.delay, 18000);
  state.callback();
  assert.equal(state.audios[0].released, true);
});
test("seeking past the preview boundary stops and releases", async () => {
  const { controller, state } = fixture();
  await controller.play({});
  state.audios[0].currentTime = 31;
  state.audios[0].onseeking();
  assert.equal(state.audios[0].released, true);
});
for (const mode of ["UNKNOWN", "EXTERNAL_ONLY", "UNAVAILABLE"])
  test(`${mode} never plays`, async () => {
    const { controller, state } = fixture(async () => {
      const d = valid();
      d.track.capability.mode = mode;
      return d;
    });
    await controller.play({});
    assert.equal(state.audios.length, 0);
    assert.equal(state.errors.length, 1);
  });
test("expired capability is rejected", async () => {
  const { controller, state } = fixture();
  state.time = 60001;
  await controller.play({});
  assert.equal(state.audios.length, 0);
});
test("unapproved audio host is rejected", async () => {
  const { controller, state } = fixture(async () => {
    const d = valid();
    d.track.capability.previewUrl = "https://evil.test/audio";
    return d;
  });
  await controller.play({});
  assert.equal(state.audios.length, 0);
});
test("late rejection from previous play cannot stop current audio", async () => {
  let reject;
  const { controller, state } = fixture();
  let number = 0;
  controller.createAudio = () => {
    const a = {
      play: () =>
        ++number === 1
          ? new Promise((_, r) => (reject = r))
          : Promise.resolve(),
      pause: () => {},
      removeAttribute: () => {
        a.released = true;
      },
      load: () => {},
      remove: () => {},
    };
    state.audios.push(a);
    return a;
  };
  const first = controller.play({});
  await Promise.resolve();
  await controller.play({});
  reject(new Error("late"));
  await first;
  assert.equal(controller.audio, state.audios[1]);
  assert.equal(state.errors.length, 0);
});
test("default timers preserve the browser function receiver", () => {
  const oldSet = globalThis.setTimeout,
    oldClear = globalThis.clearTimeout;
  globalThis.setTimeout = function () {
    assert.equal(this, undefined);
    return 1;
  };
  globalThis.clearTimeout = function () {
    assert.equal(this, undefined);
  };
  try {
    const controller = new PreviewController({
      fetchCapability: () => {},
      createAudio: () => {},
      onStart: () => {},
      onStop: () => {},
      onError: () => {},
    });
    controller.schedule(() => {}, 30);
    controller.stop();
  } finally {
    globalThis.setTimeout = oldSet;
    globalThis.clearTimeout = oldClear;
  }
});
