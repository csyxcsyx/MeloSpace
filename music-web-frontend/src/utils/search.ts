const SEARCH_HISTORY_KEY = "melospace-search-history";
const SEARCH_HISTORY_LIMIT = 10;

export interface HighlightSegment {
  text: string;
  matched: boolean;
}

export interface LatestRequestGate {
  begin: () => number;
  isCurrent: (runId: number) => boolean;
  invalidate: () => void;
}

export function createLatestRequestGate(): LatestRequestGate {
  let currentRunId = 0;
  return {
    begin() {
      currentRunId += 1;
      return currentRunId;
    },
    isCurrent(runId) {
      return runId === currentRunId;
    },
    invalidate() {
      currentRunId += 1;
    }
  };
}

export function splitHighlight(value: string | null | undefined, keyword: string): HighlightSegment[] {
  const text = value ?? "";
  const query = keyword.trim();
  if (!query) return [{ text, matched: false }];

  const lowerText = text.toLocaleLowerCase();
  const lowerQuery = query.toLocaleLowerCase();
  const segments: HighlightSegment[] = [];
  let cursor = 0;

  while (cursor < text.length) {
    const matchIndex = lowerText.indexOf(lowerQuery, cursor);
    if (matchIndex < 0) {
      segments.push({ text: text.slice(cursor), matched: false });
      break;
    }
    if (matchIndex > cursor) {
      segments.push({ text: text.slice(cursor, matchIndex), matched: false });
    }
    const matchEnd = matchIndex + query.length;
    segments.push({ text: text.slice(matchIndex, matchEnd), matched: true });
    cursor = matchEnd;
  }

  return segments.length ? segments : [{ text, matched: false }];
}

export function readSearchHistory(storage: Pick<Storage, "getItem" | "removeItem"> = window.localStorage): string[] {
  const raw = storage.getItem(SEARCH_HISTORY_KEY);
  if (!raw) return [];
  try {
    const values = JSON.parse(raw);
    if (!Array.isArray(values)) throw new Error("Invalid search history");
    return values
      .filter((value): value is string => typeof value === "string")
      .map(normalizeKeyword)
      .filter(Boolean)
      .slice(0, SEARCH_HISTORY_LIMIT);
  } catch {
    storage.removeItem(SEARCH_HISTORY_KEY);
    return [];
  }
}

export function rememberSearch(
  keyword: string,
  storage: Pick<Storage, "getItem" | "setItem" | "removeItem"> = window.localStorage
): string[] {
  const normalized = normalizeKeyword(keyword);
  if (!normalized) return readSearchHistory(storage);
  const history = readSearchHistory(storage)
    .filter((item) => item.toLocaleLowerCase() !== normalized.toLocaleLowerCase());
  const nextHistory = [normalized, ...history].slice(0, SEARCH_HISTORY_LIMIT);
  storage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(nextHistory));
  return nextHistory;
}

export function removeSearchHistory(
  keyword: string,
  storage: Pick<Storage, "getItem" | "setItem" | "removeItem"> = window.localStorage
): string[] {
  const normalized = normalizeKeyword(keyword);
  const nextHistory = readSearchHistory(storage)
    .filter((item) => item.toLocaleLowerCase() !== normalized.toLocaleLowerCase());
  if (nextHistory.length) {
    storage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(nextHistory));
  } else {
    storage.removeItem(SEARCH_HISTORY_KEY);
  }
  return nextHistory;
}

export function clearSearchHistory(storage: Pick<Storage, "removeItem"> = window.localStorage) {
  storage.removeItem(SEARCH_HISTORY_KEY);
}

function normalizeKeyword(keyword: string) {
  return keyword.trim().replace(/\s+/g, " ").slice(0, 50);
}
