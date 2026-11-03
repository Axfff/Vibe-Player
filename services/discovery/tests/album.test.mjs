import test from "node:test";
import assert from "node:assert/strict";
import { AlbumController } from "../static/album.mjs";

const limits = { albumPhotos: 500 };
const files = Array.from({ length: 4 }, (_, i) => ({
  name: `private-file-${i}.jpg`,
}));
function fixture({ prepare, paused = false, onUpdate } = {}) {
  const calls = [];
  const outcomes = [];
  let total = 0;
  const snapshot = (state = "processing") => ({
    albumId: "test-album",
    total,
    processed: outcomes.length,
    items: [...outcomes],
    state,
  });
  const api = async (path, options) => {
    calls.push({ path, ...options });
    if (path === "/v1/albums") {
      total = JSON.parse(options.body).photoCount;
      return snapshot("ready");
    }
    if (options.method === "DELETE") return snapshot("cancelled");
    outcomes.push({
      index: Number(path.split("/").at(-1)),
      state: path.includes("failures") ? "failed" : "succeeded",
    });
    return snapshot(
      paused
        ? "paused"
        : outcomes.length === total
          ? "completed"
          : "processing",
    );
  };
  const controller = new AlbumController({
    api,
    prepare: prepare || (async () => ({ blob: "cleaned" })),
    onUpdate,
  });
  return { controller, calls, outcomes };
}

test("all selected images are sent sequentially with per-image consent and no filenames", async () => {
  let active = 0,
    maximum = 0;
  const { controller, calls, outcomes } = fixture({
    prepare: async () => {
      active++;
      maximum = Math.max(maximum, active);
      await Promise.resolve();
      active--;
      return { blob: "cleaned" };
    },
  });
  const result = await controller.run(files, { consented: () => true, limits });
  assert.equal(result.processed, files.length);
  assert.equal(maximum, 1);
  assert.deepEqual(
    outcomes.map((item) => item.index),
    [0, 1, 2, 3],
  );
  assert.equal(calls.filter((call) => call.path.includes("photos")).length, 4);
  assert.ok(calls.every((call) => call.headers["X-Cloud-Consent"] === "true"));
  assert.equal(JSON.stringify(calls).includes("private-file"), false);
});

test("no consent and excessive selection make zero API calls", async () => {
  const { controller, calls } = fixture();
  await assert.rejects(
    controller.run(files, { consented: () => false, limits }),
    /同意/,
  );
  await assert.rejects(
    controller.run(files, {
      consented: () => true,
      limits: { albumPhotos: 2 },
    }),
    /1–2/,
  );
  assert.equal(calls.length, 0);
});

test("bad local image is recorded without uploading bytes and remaining images continue", async () => {
  const { controller, calls, outcomes } = fixture({
    prepare: async (file) => {
      if (file === files[1])
        throw Object.assign(new Error("PRIVATE"), {
          code: "unsupported_image",
        });
      return { blob: "cleaned" };
    },
  });
  await controller.run(files, { consented: () => true, limits });
  assert.equal(outcomes.length, 4);
  const failure = calls.find((call) => call.path.includes("failures"));
  assert.deepEqual(JSON.parse(failure.body), {
    errorCode: "unsupported_image",
  });
  assert.equal(calls.filter((call) => call.path.includes("photos")).length, 3);
});

test("cancel during decode sends no image and ignores late completion", async () => {
  let release, entered;
  const started = new Promise((resolve) => (entered = resolve));
  const { controller, calls } = fixture({
    prepare: () =>
      new Promise((resolve) => {
        entered();
        release = resolve;
      }),
  });
  const pending = controller.run(files, { consented: () => true, limits });
  await started;
  await controller.cancel();
  release({ blob: "late" });
  assert.equal(await pending, null);
  assert.equal(calls.filter((call) => call.path.includes("photos")).length, 0);
  assert.equal(calls.at(-1).method, "DELETE");
});

test("cancel in flight aborts the request and prevents remaining images", async () => {
  let entered, release;
  const started = new Promise((resolve) => (entered = resolve));
  const updates = [],
    calls = [];
  const controller = new AlbumController({
    prepare: async () => ({ blob: "cleaned" }),
    onUpdate: (data) => updates.push(data.state),
    api: async (path, options) => {
      calls.push(options);
      if (path === "/v1/albums") return { albumId: "x", state: "ready" };
      if (options.method === "DELETE")
        return { albumId: "x", state: "cancelled" };
      return new Promise((resolve) => {
        entered();
        release = resolve;
      });
    },
  });
  const pending = controller.run(files, { consented: () => true, limits });
  await started;
  await controller.cancel(true);
  release({ state: "late" });
  await pending;
  assert.equal(calls[1].signal.aborted, true);
  assert.equal(calls.length, 3);
  assert.deepEqual(updates, ["ready", "cancelled"]);
});

test("provider pause stops the batch without sending subsequent images", async () => {
  const { controller, calls } = fixture({ paused: true });
  const data = await controller.run(files, { consented: () => true, limits });
  assert.equal(data.state, "paused");
  assert.equal(calls.length, 2);
});
