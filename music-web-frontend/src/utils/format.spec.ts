import { describe, expect, it } from "vitest";
import { displayName, formatCount, formatDuration, formatRelativeTime, resolveMediaUrl } from "./format";

describe("format utilities", () => {
  it("formats durations and display names", () => {
    expect(formatDuration(125)).toBe("2:05");
    expect(formatDuration(0)).toBe("--:--");
    expect(displayName("  ", "备用名称")).toBe("备用名称");
  });

  it("resolves local and remote media urls", () => {
    expect(resolveMediaUrl("media/song.mp3")).toBe("/media/song.mp3");
    expect(resolveMediaUrl("https://cdn.example/song.mp3")).toBe("https://cdn.example/song.mp3");
  });

  it("formats counts for compact display", () => {
    expect(formatCount(9999)).toBe("9,999");
    expect(formatCount(12500)).toBe("1.3万");
    expect(formatCount(120000)).toBe("12万");
  });

  it("formats recent and older timestamps", () => {
    const now = Date.parse("2026-09-15T12:00:00+08:00");
    expect(formatRelativeTime("2026-09-15T11:59:30+08:00", now)).toBe("刚刚");
    expect(formatRelativeTime("2026-09-15T10:00:00+08:00", now)).toBe("2 小时前");
    expect(formatRelativeTime("2026-08-01T12:00:00+08:00", now)).toContain("2026");
  });
});
