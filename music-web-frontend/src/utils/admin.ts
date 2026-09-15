export const ADMIN_PAGE_SIZE = 8;

export function paginate<T>(items: T[], page: number, pageSize = ADMIN_PAGE_SIZE) {
  const start = (page - 1) * pageSize;
  return items.slice(start, start + pageSize);
}

export function pageCount(total: number, pageSize = ADMIN_PAGE_SIZE) {
  return Math.max(1, Math.ceil(total / pageSize));
}

export function normalizeSearch(value: string) {
  return value.trim().toLocaleLowerCase();
}

export function matchesQuery(value: string | null | undefined, query: string) {
  return !query || (value || "").toLocaleLowerCase().includes(query);
}

export function compareText(first: string | null | undefined, second: string | null | undefined) {
  return (first || "").localeCompare(second || "", "zh-CN");
}

export function compareDate(first: string | null | undefined, second: string | null | undefined) {
  const firstTime = Date.parse(first || "") || 0;
  const secondTime = Date.parse(second || "") || 0;
  return firstTime - secondTime;
}
