import { PreviewController } from "./preview.mjs";
import { preparePhoto } from "./images.mjs";
import { AlbumController } from "./album.mjs";
const $ = (id) => document.getElementById(id);
const moods = {
  calm: "平静",
  bright: "明亮",
  energetic: "活力",
  melancholic: "忧郁",
  dreamy: "梦幻",
  focused: "专注",
};
const sceneNames = {
  nature: "自然",
  water: "水面",
  city: "城市",
  indoors: "室内",
  night: "夜色",
  daylight: "日光",
  warm_colors: "暖色",
  cool_colors: "冷色",
  open_space: "开阔",
  abstract: "抽象",
  other: "其他",
};
const musicNames = {
  ambient: "氛围",
  acoustic: "原声",
  piano: "钢琴",
  electronic: "电子",
  jazz: "爵士",
  classical: "古典",
  rock: "摇滚",
  pop: "流行",
  chillout: "休闲",
  instrumental: "器乐",
  folk: "民谣",
  dance: "舞曲",
};
let config,
  mode = "manual",
  blob,
  objectUrl,
  albumFiles = [],
  visionResult = null,
  sessionId = null,
  exclusions = [];
let photoVersion = 0,
  resultVersion = 0,
  photoRequest,
  resultRequest;
let sceneDirty = false,
  musicDirty = false;
function node(tag, text, cls) {
  const e = document.createElement(tag);
  if (text) e.textContent = text;
  if (cls) e.className = cls;
  return e;
}
function chips(id, entries) {
  for (const [value, label] of Object.entries(entries)) {
    const box = node("input");
    box.type = "checkbox";
    box.value = value;
    const labelEl = node("label");
    labelEl.append(box, document.createTextNode(label));
    $(id).append(labelEl);
  }
}
chips("scenes", sceneNames);
chips("musicTags", musicNames);
const chosen = (id) =>
  Array.from($(id).querySelectorAll("input:checked")).map((e) => e.value);
function setChosen(id, values) {
  $(id)
    .querySelectorAll("input")
    .forEach((e) => (e.checked = values.includes(e.value)));
}
function status(id, text, error = false) {
  $(id).textContent = text;
  $(id).classList.toggle("error", error);
}
function renderProviders(data) {
  if (!data.vision) return;
  $("providers").replaceChildren();
  for (const p of [data.vision, ...(data.catalogs || [])]) {
    const item = node(
      "div",
      `${p.provider} · ${p.mode} · ${p.state}${p.errorCode ? " / " + p.errorCode : ""} · ${p.requestsMade} 次请求`,
      "provider",
    );
    if (p.mode === "mock") item.classList.add("mock");
    $("providers").append(item);
  }
}
async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "X-Discovery-Token": config.demoToken, ...options.headers },
  });
  const data = await response.json();
  renderProviders(data);
  if (!response.ok) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}
function analyzeEnabled() {
  $("analyze").disabled = !(
    (mode === "album" ? albumFiles.length && !albumController.running : blob) &&
    $("consent").checked &&
    config &&
    config.vision.mode !== "unconfigured"
  );
}
const previewController = new PreviewController({
  fetchCapability: (track) => api(capPath(track)),
  createAudio: () => new Audio(),
  onStart: (track, audio) => {
    $("audioSlot").replaceChildren(audio);
    $("playing").textContent = `${track.title} — ${track.artist} · 最多 30 秒`;
    $("playerPanel").hidden = false;
  },
  onStop: () => {
    $("playerPanel").hidden = true;
  },
  onError: (message) => status("resultStatus", `试听状态：${message}`, true),
});
const albumController = new AlbumController({
  api,
  prepare: preparePhoto,
  onUpdate: renderAlbum,
});
function applyVision(intent) {
  visionResult = intent;
  if (!intent) return;
  if (!sceneDirty) setChosen("scenes", intent.sceneTags);
  if (!musicDirty) setChosen("musicTags", intent.musicDescriptors);
  invalidateIntent();
  $("suggestions").textContent =
    `视觉建议：${intent.visualMoodSuggestions.map((m) => moods[m]).join(" / ")}；不确定性：${intent.uncertainty}。请在下方选择目标氛围。`;
}
function renderAlbum(data) {
  $("albumProgress").max = data.total;
  $("albumProgress").value = data.processed;
  const prefix = data.vision.mode === "mock" ? "MOCK 示例 · " : "";
  status(
    "analysisStatus",
    `${prefix}已处理 ${data.processed} / ${data.total}：成功 ${data.succeeded}，失败 ${data.failed}，待处理 ${data.pending}${data.pauseReason ? `；已暂停：${data.pauseReason}` : ""}${data.state === "cancelled" ? "；已停止本批" : ""}`,
  );
  for (const item of data.items) {
    const row = $("albumItems").children[item.index];
    if (!row) continue;
    const detail =
      item.state === "succeeded"
        ? `完成 · ${item.intent.sceneTags.map((tag) => sceneNames[tag]).join(" / ")}`
        : `失败 · ${item.errorCode}`;
    row.textContent = `${albumFiles[item.index].name} · ${detail}`;
    row.classList.toggle("error", item.state === "failed");
  }
}
function applyAlbum(data) {
  applyVision(data.intent);
  if (data.intent) {
    const scenes = data.frequencies.sceneTags
      .map((entry) => `${sceneNames[entry.tag]} ${entry.photos} 张`)
      .join("、");
    $("suggestions").textContent +=
      ` 相册覆盖 ${data.succeeded} / ${data.total} 张；场景分布：${scenes}。`;
  }
}
function selectAlbum(files) {
  clearPhoto();
  if (!config) {
    status("analysisStatus", "正在读取服务配置，请稍后重新选择。", true);
    return;
  }
  if (!files.length) return;
  if (files.length > config.limits.albumPhotos) {
    status(
      "analysisStatus",
      `选中 ${files.length} 个文件，单批最多 ${config.limits.albumPhotos} 个，请分批选择。`,
      true,
    );
    return;
  }
  albumFiles = files;
  $("albumItems").replaceChildren(
    ...files.map((file) => node("li", `${file.name} · 待处理`)),
  );
  $("albumSelection").textContent =
    `已选 ${files.length} 个文件，将逐一检查与分析。非图片和不支持的格式会列为失败，不上传。${config.vision.mode === "live" ? ` 最多 ${files.length} 次模型调用；当前进程预算余量 ${config.limits.visionCallsRemaining} 次（开始前再次校验）。` : ""}`;
  $("consentText").textContent =
    `我同意将本批 ${files.length} 个文件中的有效图片逐张处理后发送给上述接收者分析`;
  status("analysisStatus", "已在本机选中相册；勾选同意并点击分析后开始处理。");
  analyzeEnabled();
}
$("albumFiles").onchange = () => selectAlbum(Array.from($("albumFiles").files));
$("albumFolder").onchange = () =>
  selectAlbum(Array.from($("albumFolder").files));
async function analyzeAlbum() {
  if (!albumFiles.length || !$("consent").checked) return;
  const version = ++photoVersion;
  $("analyze").disabled = true;
  $("stopAlbum").hidden = false;
  invalidateIntent();
  try {
    await albumController.cancel();
    if (version !== photoVersion || !$("consent").checked) return;
    const pending = albumController.run(albumFiles, {
      consented: () => $("consent").checked && mode === "album",
      limits: config.limits,
    });
    analyzeEnabled();
    const data = await pending;
    if (data && version === photoVersion) applyAlbum(data);
  } catch (error) {
    if (version === photoVersion && error.name !== "AbortError")
      status(
        "analysisStatus",
        `本批停止：${error.message}。已处理数量见进度条。`,
        true,
      );
  } finally {
    if (version === photoVersion) {
      $("stopAlbum").hidden = true;
      $("consent").checked = false;
      analyzeEnabled();
    }
  }
}
$("stopAlbum").onclick = async () => {
  const version = ++photoVersion;
  $("consent").checked = false;
  const data = await albumController.cancel(true);
  if (version !== photoVersion) return;
  if (data) applyAlbum(data);
  $("stopAlbum").hidden = true;
  analyzeEnabled();
};
function stop() {
  previewController.stop();
}
function clearPhoto(resetSelection = true) {
  ++photoVersion;
  photoRequest?.abort();
  albumController.cancel();
  albumFiles = [];
  $("albumFiles").value = "";
  $("albumFolder").value = "";
  $("albumItems").replaceChildren();
  $("albumSelection").textContent =
    "逐张处理所选文件，单批最多 500 张。失败项会列出。";
  $("albumProgress").value = 0;
  $("stopAlbum").hidden = true;
  blob = null;
  visionResult = null;
  if (resetSelection) $("photo").value = "";
  $("consent").checked = false;
  $("photoPreview").hidden = true;
  $("photoPreview").removeAttribute("src");
  if (objectUrl) URL.revokeObjectURL(objectUrl);
  objectUrl = null;
  $("suggestions").textContent = "";
  invalidateIntent();
  analyzeEnabled();
  status("analysisStatus", "已清除照片；可继续手动选择标签。");
}
function selectMode(value) {
  if (mode !== value) clearPhoto();
  mode = value;
  $("photoPanel").hidden = value === "manual";
  $("singlePicker").hidden = value !== "photo";
  $("albumPicker").hidden = value !== "album";
  $("manualMode").setAttribute("aria-pressed", String(value === "manual"));
  $("photoMode").setAttribute("aria-pressed", String(value === "photo"));
  $("albumMode").setAttribute("aria-pressed", String(value === "album"));
  $("analyze").textContent = value === "album" ? "分析整个相册" : "分析照片";
  $("consentText").textContent =
    value === "album"
      ? "我同意将本批选中的图片逐张处理后发送给上述接收者分析"
      : "我同意将这张处理后的图片发送给上述接收者分析";
  analyzeEnabled();
}
$("manualMode").onclick = () => selectMode("manual");
$("photoMode").onclick = () => selectMode("photo");
$("albumMode").onclick = () => selectMode("album");
$("cancelPhoto").onclick = clearPhoto;
$("consent").onchange = () => {
  if (!$("consent").checked) {
    ++photoVersion;
    photoRequest?.abort();
    albumController.cancel(true).then((data) => {
      if (data && mode === "album") applyAlbum(data);
      analyzeEnabled();
    });
  }
  analyzeEnabled();
};
$("photo").onchange = async () => {
  const file = $("photo").files[0];
  clearPhoto(false);
  if (!file) return;
  const version = photoVersion;
  try {
    const image = await preparePhoto(file, config.limits);
    if (version !== photoVersion) return;
    blob = image.blob;
    objectUrl = URL.createObjectURL(blob);
    $("photoPreview").src = objectUrl;
    $("photoPreview").hidden = false;
    status(
      "analysisStatus",
      `本机处理完成 · ${image.width} × ${image.height}。勾选同意后可分析。`,
    );
    analyzeEnabled();
  } catch (error) {
    if (version === photoVersion) status("analysisStatus", error.message, true);
  }
};
$("analyze").onclick = async () => {
  if (mode === "album") return analyzeAlbum();
  if (!blob || !$("consent").checked) return;
  const version = ++photoVersion;
  photoRequest?.abort();
  photoRequest = new AbortController();
  $("analyze").disabled = true;
  status(
    "analysisStatus",
    config.vision.mode === "mock"
      ? "生成本地 MOCK 示例…"
      : "正在分析处理后的照片…",
  );
  try {
    const data = await api("/v1/photo-intent", {
      method: "POST",
      body: blob,
      signal: photoRequest.signal,
      headers: { "Content-Type": "image/jpeg", "X-Cloud-Consent": "true" },
    });
    if (version !== photoVersion) return;
    applyVision(data.intent);
    status(
      "analysisStatus",
      data.vision.mode === "mock"
        ? "MOCK 示例标签 · 本地示例模式"
        : "分析完成 · 标签可修改",
    );
  } catch (error) {
    if (version === photoVersion && error.name !== "AbortError")
      status(
        "analysisStatus",
        `分析失败：${error.message}。可使用手动入口。`,
        true,
      );
  } finally {
    if (version === photoVersion) analyzeEnabled();
  }
};
function invalidateIntent() {
  $("confirmed").checked = false;
  ++resultVersion;
  resultRequest?.abort();
  $("discover").disabled = false;
  document
    .querySelectorAll(".track button")
    .forEach((b) => (b.disabled = false));
}
$("scenes").onchange = () => {
  sceneDirty = true;
  invalidateIntent();
};
$("musicTags").onchange = () => {
  musicDirty = true;
  invalidateIntent();
};
for (const id of ["mood", "searchText", "energy", "vocals", "market"])
  $(id).oninput = invalidateIntent;
function requestBody() {
  if (!$("mood").value || !$("confirmed").checked)
    throw new Error("请先选择目标氛围并勾选确认。");
  if (chosen("musicTags").length > 8)
    throw new Error("音乐标签最多选择 8 项。");
  return {
    intent: {
      targetMood: $("mood").value,
      musicTags: chosen("musicTags"),
      text: $("searchText").value,
      energy: $("energy").value,
      instrumental:
        $("vocals").value === "any"
          ? null
          : $("vocals").value === "instrumental",
    },
    confirmed: true,
    sceneTags: chosen("scenes"),
    visualSuggestions: visionResult,
    inputMode: mode !== "manual" && visionResult ? mode : "manual",
    market: $("market").value,
    limit: 8,
    exclude: exclusions,
  };
}
function capPath(track) {
  return `/v1/providers/${track.ref.provider}/tracks/${track.ref.providerTrackId}/capabilities?sessionId=${encodeURIComponent(sessionId)}`;
}
async function preview(track) {
  await previewController.play(track);
}
function card(track) {
  const article = node("article", null, "track"),
    art = node("div", "♫", "art"),
    info = node("div");
  info.append(
    node(
      "p",
      `${track.ref.provider.toUpperCase()} / ${track.ref.providerTrackId}`,
      "meta",
    ),
    node("h3", track.title),
    node("p", track.artist),
  );
  info.append(
    node("p", track.recommendationReason, "small"),
    node(
      "p",
      `${track.capability.mode} · ${track.capability.reasonCode}`,
      "meta",
    ),
  );
  if (track.capability.attribution)
    info.append(node("p", track.capability.attribution, "small"));
  const links = node("div", null, "links"),
    link = node("a", "打开官方页面 ↗");
  link.href = track.officialUrl;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  links.append(link);
  if (track.capability.licenseUrl) {
    const license = node("a", "曲目许可");
    license.href = track.capability.licenseUrl;
    license.target = "_blank";
    license.rel = "noopener noreferrer";
    links.append(license);
  }
  if (track.capability.mode === "IN_APP_PREVIEW") {
    const play = node("button", "试听 · 最多 30 秒");
    play.onclick = () => preview(track);
    links.append(play);
  }
  const replace = node("button", "按当前条件换一首");
  replace.onclick = () => runDiscovery(track, article);
  links.append(replace);
  info.append(links);
  article.append(art, info);
  return article;
}
async function runDiscovery(replacing, oldCard) {
  let body;
  try {
    body = requestBody();
  } catch (error) {
    status("resultStatus", error.message, true);
    return;
  }
  stop();
  resultRequest?.abort();
  resultRequest = new AbortController();
  const version = ++resultVersion;
  $("discover").disabled = true;
  document
    .querySelectorAll(".track button")
    .forEach((b) => (b.disabled = true));
  if (replacing) {
    exclusions.push(replacing.ref);
    body = { ...body, exclude: exclusions, sessionId, replacing };
  } else {
    exclusions = [];
    body.exclude = [];
  }
  status(
    "resultStatus",
    replacing ? "正在按当前条件寻找替换曲目…" : "正在检索目录并校验官方链接…",
  );
  try {
    const data = await api(
      replacing ? "/v1/discover/replace" : "/v1/discover",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        signal: resultRequest.signal,
      },
    );
    if (version !== resultVersion) return;
    sessionId = data.sessionId;
    exclusions.push(...data.tracks.map((t) => t.ref));
    if (replacing) {
      if (data.tracks[0]) oldCard.replaceWith(card(data.tracks[0]));
      else oldCard.remove();
    } else {
      $("results").replaceChildren(...data.tracks.map(card));
      if (!data.tracks.length)
        $("results").append(
          node(
            "div",
            "本次目录结果为 0 首。可调整关键词或检查上方来源配置。",
            "empty",
          ),
        );
    }
    $("count").textContent =
      `${document.querySelectorAll(".track").length} / 8`;
    status(
      "resultStatus",
      `${{ manual: "手动模式", photo: "照片意图", album: "相册意图" }[data.inputMode]} · ${data.shortageReason || `已找到 ${data.returned} 首真实目录曲目。`}${(data.selectionIssues || []).map((issue) => ` ${issue.provider}：${issue.reasonCode === "official_track_mismatch" ? "官方页面跳转后未保留曲目 ID，请核对市场与平台地区可用性" : issue.reasonCode}（${issue.count} 次）。`).join("")}`,
    );
  } catch (error) {
    if (version === resultVersion && error.name !== "AbortError")
      status("resultStatus", `发现失败：${error.message}`, true);
  } finally {
    if (version === resultVersion) {
      $("discover").disabled = false;
      document
        .querySelectorAll(".track button")
        .forEach((b) => (b.disabled = false));
    }
  }
}
$("discover").onclick = () => runDiscovery();
$("stop").onclick = stop;
window.addEventListener("pagehide", () => {
  stop();
  photoRequest?.abort();
  albumController.cancel();
  resultRequest?.abort();
  if (objectUrl) URL.revokeObjectURL(objectUrl);
});
document.addEventListener("visibilitychange", () => {
  if (document.hidden) stop();
});
fetch("/v1/providers/status")
  .then((r) => r.json())
  .then((data) => {
    config = data;
    renderProviders(data);
    $("cloudNotice").textContent =
      data.vision.mode === "mock"
        ? "MOCK 模式：本机返回示例标签，云端调用关闭。"
        : `云端接收者：${data.cloudRecipient}${data.visionModel ? " / " + data.visionModel : ""}。`;
    if (data.vision.mode === "unconfigured")
      status(
        "analysisStatus",
        "视觉供应商未配置。可通过手动文字 / 标签发现音乐。",
      );
    analyzeEnabled();
  })
  .catch(() =>
    status("resultStatus", "服务状态读取失败，请检查后端进程。", true),
  );
