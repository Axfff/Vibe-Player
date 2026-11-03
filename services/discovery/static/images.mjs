function invalid(code, message) {
  return Object.assign(new Error(message), { code });
}

export async function preparePhoto(file, limits) {
  if (file.size > limits.uploadBytes)
    throw invalid("image_byte_limit", "图片超过 5 MiB。");
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type))
    throw invalid(
      "unsupported_image",
      "仅支持 JPEG、PNG、WebP；请先转换其他格式。",
    );
  let bitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw invalid("client_decode_error", "图片解码失败。");
  }
  try {
    if (bitmap.width * bitmap.height > limits.pixels)
      throw invalid("image_pixel_limit", "图片超过 2000 万像素。");
    const scale = Math.min(
      1,
      limits.imageEdge / Math.max(bitmap.width, bitmap.height),
    );
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "white";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise((resolve) =>
      canvas.toBlob(resolve, "image/jpeg", 0.85),
    );
    if (!blob) throw invalid("client_decode_error", "图片转换失败。");
    return { blob, width: canvas.width, height: canvas.height };
  } finally {
    bitmap.close();
  }
}
