import { beforeEach, describe, expect, it } from "vitest";
import {
  clearSearchHistory,
  createLatestRequestGate,
  readSearchHistory,
  rememberSearch,
  removeSearchHistory,
  splitHighlight
} from "@/utils/search";

describe("search utilities", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("只允许最后发起的搜索请求写入页面状态", async () => {
    const gate = createLatestRequestGate();
    const committed: string[] = [];
    let resolveFirst!: (value: string) => void;
    let resolveSecond!: (value: string) => void;
    const first = new Promise<string>((resolve) => {
      resolveFirst = resolve;
    });
    const second = new Promise<string>((resolve) => {
      resolveSecond = resolve;
    });

    const firstRun = gate.begin();
    const firstTask = first.then((value) => {
      if (gate.isCurrent(firstRun)) committed.push(value);
    });
    const secondRun = gate.begin();
    const secondTask = second.then((value) => {
      if (gate.isCurrent(secondRun)) committed.push(value);
    });

    resolveSecond("新结果");
    await secondTask;
    resolveFirst("过期结果");
    await firstTask;

    expect(committed).toEqual(["新结果"]);
  });

  it("保存最近十条搜索并将重复关键词移到最前", () => {
    for (let index = 1; index <= 12; index += 1) {
      rememberSearch(`  关键词 ${index}  `);
    }
    expect(readSearchHistory()).toHaveLength(10);
    expect(readSearchHistory()[0]).toBe("关键词 12");
    expect(readSearchHistory()).not.toContain("关键词 1");

    rememberSearch("关键词 8");
    expect(readSearchHistory()[0]).toBe("关键词 8");
    expect(readSearchHistory().filter((item) => item === "关键词 8")).toHaveLength(1);
  });

  it("支持单条删除和全部清除搜索历史", () => {
    rememberSearch("周杰伦");
    rememberSearch("陈奕迅");
    expect(removeSearchHistory("周杰伦")).toEqual(["陈奕迅"]);
    clearSearchHistory();
    expect(readSearchHistory()).toEqual([]);
  });

  it("将匹配片段拆分为文本节点而不生成 HTML", () => {
    expect(splitHighlight("<img onerror=alert(1)>音乐音乐", "音乐")).toEqual([
      { text: "<img onerror=alert(1)>", matched: false },
      { text: "音乐", matched: true },
      { text: "音乐", matched: true }
    ]);
  });
});
