const DEFAULT_TEXT_CACHE_LIMIT = 80;

const textCache = new Map<string, string>();
const textRequestCache = new Map<string, Promise<string>>();

export async function readCachedText(url: string, limit = DEFAULT_TEXT_CACHE_LIMIT) {
  const cachedText = textCache.get(url);
  if (cachedText != null) return cachedText;

  const pendingRequest = textRequestCache.get(url);
  if (pendingRequest) return pendingRequest;

  const request = fetch(url, { cache: "force-cache" })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return response.text();
    })
    .then((text) => {
      rememberCacheEntry(textCache, url, text, limit);
      return text;
    })
    .finally(() => {
      textRequestCache.delete(url);
    });

  textRequestCache.set(url, request);
  return request;
}

function rememberCacheEntry<T>(cache: Map<string, T>, key: string, value: T, limit: number) {
  if (!cache.has(key) && cache.size >= limit) {
    const oldestKey = cache.keys().next().value as string | undefined;
    if (oldestKey) cache.delete(oldestKey);
  }
  cache.set(key, value);
}
