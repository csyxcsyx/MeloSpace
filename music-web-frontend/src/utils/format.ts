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

export function formatCount(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`;
  return new Intl.NumberFormat("zh-CN").format(value);
}

export function formatRelativeTime(value: string, now = Date.now()) {
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return "刚刚";
  const seconds = Math.max(0, Math.floor((now - timestamp) / 1000));
  if (seconds < 60) return "刚刚";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "short", day: "numeric" })
    .format(new Date(timestamp));
}
