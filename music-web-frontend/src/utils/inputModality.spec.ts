import { afterEach, describe, expect, it } from "vitest";
import { installInputModalityTracking } from "@/utils/inputModality";

describe("installInputModalityTracking", () => {
  afterEach(() => {
    delete document.documentElement.dataset.inputModality;
  });

  it("distinguishes pointer interaction from keyboard navigation", () => {
    const dispose = installInputModalityTracking();

    expect(document.documentElement.dataset.inputModality).toBe("pointer");
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Tab", bubbles: true }));
    expect(document.documentElement.dataset.inputModality).toBe("keyboard");
    document.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true }));
    expect(document.documentElement.dataset.inputModality).toBe("pointer");

    dispose();
  });

  it("does not treat ordinary typing as keyboard focus navigation", () => {
    const dispose = installInputModalityTracking();

    document.dispatchEvent(new KeyboardEvent("keydown", { key: "a", bubbles: true }));
    expect(document.documentElement.dataset.inputModality).toBe("pointer");

    dispose();
  });
});
