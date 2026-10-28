const LOCAL_ERRORS = new Set([
  "unsupported_image",
  "image_byte_limit",
  "image_pixel_limit",
  "client_decode_error",
]);

export class AlbumController {
  constructor({ api, prepare, onUpdate = () => {} }) {
    Object.assign(this, { api, prepare, onUpdate });
    this.version = 0;
    this.running = false;
    this.albumId = null;
    this.snapshot = null;
  }

  async run(files, { consented, limits }) {
    if (this.running) throw new Error("已有相册正在分析。");
    if (!consented()) throw new Error("请先同意本批图片处理。");
    if (!files.length || files.length > limits.albumPhotos)
      throw new Error(`请选择 1–${limits.albumPhotos} 张图片。`);
    const version = ++this.version;
    const controller = new AbortController();
    this.controller = controller;
    this.running = true;
    const active = () =>
      version === this.version && consented() && !controller.signal.aborted;
    const send = (path, options) =>
      this.api(path, { ...options, signal: controller.signal });
    const update = (data) => {
      this.snapshot = data;
      this.onUpdate(data);
    };
    try {
      const created = await send("/v1/albums", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Cloud-Consent": "true",
        },
        body: JSON.stringify({ photoCount: files.length }),
      });
      if (!active()) {
        await this.api(`/v1/albums/${created.albumId}`, { method: "DELETE" });
        return null;
      }
      this.albumId = created.albumId;
      update(created);
      for (let index = 0; index < files.length; index++) {
        if (!active()) break;
        let image, errorCode;
        try {
          image = await this.prepare(files[index], limits);
        } catch (error) {
          errorCode = LOCAL_ERRORS.has(error.code)
            ? error.code
            : "client_decode_error";
        }
        if (!active()) break;
        const prefix = `/v1/albums/${created.albumId}`;
        const data = errorCode
          ? await send(`${prefix}/failures/${index}`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ errorCode }),
            })
          : await send(`${prefix}/photos/${index}`, {
              method: "POST",
              body: image.blob,
              headers: {
                "Content-Type": "image/jpeg",
                "X-Cloud-Consent": "true",
              },
            });
        image = null;
        if (!active()) break;
        update(data);
        if (["paused", "cancelled"].includes(data.state)) break;
      }
      return active() ? this.snapshot : null;
    } finally {
      if (version === this.version) this.running = false;
    }
  }

  async cancel(notify = false) {
    const version = ++this.version;
    this.controller?.abort();
    this.running = false;
    const identifier = this.albumId;
    this.albumId = null;
    if (!identifier) return null;
    try {
      const data = await this.api(`/v1/albums/${identifier}`, {
        method: "DELETE",
        keepalive: true,
      });
      if (notify && version === this.version) {
        this.snapshot = data;
        this.onUpdate(data);
        return data;
      }
    } catch {
      // Client cancellation already prevents every subsequent upload.
    }
    return null;
  }
}
