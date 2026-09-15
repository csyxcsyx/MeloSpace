import { describe, expect, it } from "vitest";
import { compareDate, compareText, matchesQuery, normalizeSearch, pageCount, paginate } from "./admin";

describe("admin list utilities", () => {
  it("paginates without mutating source items", () => {
    const items = [1, 2, 3, 4, 5];
    expect(paginate(items, 2, 2)).toEqual([3, 4]);
    expect(items).toEqual([1, 2, 3, 4, 5]);
    expect(pageCount(17, 8)).toBe(3);
    expect(pageCount(0, 8)).toBe(1);
  });

  it("normalizes and matches localized search text", () => {
    expect(normalizeSearch("  MeloSpace ")).toBe("melospace");
    expect(matchesQuery("MeloSpace 音乐", "melospace")).toBe(true);
    expect(matchesQuery(null, "music")).toBe(false);
  });

  it("provides stable text and descending date comparators", () => {
    expect(compareText("A", "B")).toBeLessThan(0);
    expect(compareDate("2026-01-01", "2026-02-01")).toBeLessThan(0);
  });
});
