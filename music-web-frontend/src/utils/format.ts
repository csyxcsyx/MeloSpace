export function formatDuration(seconds?: number | null) {
  if (!seconds || seconds <= 0) return "--:--";
  const minutes = Math.floor(seconds / 60);
  const rest = Math.floor(seconds % 60).toString().padStart(2, "0");
  return `${minutes}:${rest}`;
}

export function resolveMediaUrl(url?: string | null) {
  if (!url) return "";
  if (/^https?:\/\//i.test(url)) return url;
  return url.startsWith("/") ? url : `/${url}`;
}

export function displayName(value?: string | null, fallback = "未命名") {
  return value && value.trim() ? value : fallback;
}
