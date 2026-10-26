// No catalog or audio content is sent to a model. Playback is directly from the reviewed host.
export class PreviewController {
  constructor({
    fetchCapability,
    createAudio,
    onStart,
    onStop,
    onError,
    now = () => Date.now(),
    schedule = (fn, ms) => setTimeout(fn, ms),
    cancel = (id) => clearTimeout(id),
  }) {
    Object.assign(this, {
      fetchCapability,
      createAudio,
      onStart,
      onStop,
      onError,
      now,
      schedule,
      cancel,
    });
    this.version = 0;
    this.audio = null;
    this.timer = null;
  }
  stop() {
    ++this.version;
    this.cancel(this.timer);
    if (this.audio) {
      const old = this.audio;
      this.audio = null;
      old.onplay =
        old.onpause =
        old.ontimeupdate =
        old.onseeking =
        old.onended =
        old.onerror =
          null;
      old.pause();
      old.removeAttribute("src");
      old.load();
      old.remove();
    }
    this.onStop();
  }
  async play(track) {
    this.stop();
    const version = this.version;
    try {
      const data = await this.fetchCapability(track);
      if (version !== this.version) return;
      const cap = data.track?.capability;
      if (
        !cap ||
        cap.mode !== "IN_APP_PREVIEW" ||
        !cap.previewUrl ||
        !(Date.parse(cap.expiresAt) > this.now())
      ) {
        throw new Error(
          cap?.reasonCode || data.capability?.reasonCode || "试听资格已变化",
        );
      }
      const url = new URL(cap.previewUrl);
      if (
        url.protocol !== "https:" ||
        url.hostname !== "prod-1.storage.jamendo.com" ||
        url.username ||
        url.password ||
        (url.port && url.port !== "443")
      ) {
        throw new Error("试听地址校验失败");
      }
      const audio = this.createAudio();
      this.audio = audio;
      let remaining = Math.min(30, cap.maxPreviewSeconds || 30) * 1000;
      let started = null;
      const consume = () => {
        if (started !== null) remaining -= Math.max(0, this.now() - started);
        started = null;
        this.cancel(this.timer);
      };
      audio.preload = "none";
      audio.controls = true;
      audio.controlsList = "nodownload noplaybackrate";
      audio.src = cap.previewUrl;
      audio.onplay = () => {
        if (version !== this.version) return;
        consume();
        if (remaining <= 0) return this.stop();
        started = this.now();
        this.timer = this.schedule(() => {
          if (version === this.version) this.stop();
        }, remaining);
      };
      audio.onpause = consume;
      audio.ontimeupdate = audio.onseeking = () => {
        if (version === this.version && audio.currentTime >= 30) this.stop();
      };
      audio.onended = () => {
        if (version === this.version) this.stop();
      };
      audio.onerror = () => {
        if (version === this.version) {
          this.stop();
          this.onError("试听流暂不可用，可打开官方页面。");
        }
      };
      this.onStart(track, audio);
      await audio.play();
    } catch (error) {
      if (version === this.version) {
        this.stop();
        this.onError(error.message);
      }
    }
  }
}
