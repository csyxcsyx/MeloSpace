import { describe, expect, it } from "vitest";
import { resolvePlayerShortcut } from "@/utils/playerShortcuts";

describe("player shortcuts", () => {
  it.each([
    ["ArrowLeft", "previous-track"],
    ["ArrowRight", "next-track"],
    ["ArrowUp", "volume-up"],
    ["ArrowDown", "volume-down"]
  ])("maps Ctrl+Alt+%s", (key, action) => {
    expect(resolvePlayerShortcut(keyboardEvent(key, { ctrlKey: true, altKey: true }))).toBe(action);
  });

  it("maps Space to playback instead of activating a focused lyric button", () => {
    const lyricButton = document.createElement("button");
    lyricButton.className = "lyric-line";
    expect(resolvePlayerShortcut(keyboardEvent(" ", { code: "Space", target: lyricButton }))).toBe("toggle-playback");
  });

  it("keeps Space available while entering text", () => {
    const input = document.createElement("input");
    expect(resolvePlayerShortcut(keyboardEvent(" ", { code: "Space", target: input }))).toBeNull();
  });

  it("ignores repeated shortcuts", () => {
    expect(resolvePlayerShortcut(keyboardEvent("ArrowRight", { ctrlKey: true, altKey: true, repeat: true }))).toBeNull();
  });
});

function keyboardEvent(
  key: string,
  options: KeyboardEventInit & { target?: Element } = {}
) {
  const { target, ...init } = options;
  const event = new KeyboardEvent("keydown", { key, bubbles: true, ...init });
  Object.defineProperty(event, "target", { configurable: true, value: target ?? document.body });
  return event;
}
