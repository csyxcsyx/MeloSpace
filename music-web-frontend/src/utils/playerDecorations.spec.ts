import { describe, expect, it } from "vitest";
import {
  getDecoratedRangeFillPosition,
  getPlayerProgressDecoration,
  getPlayerProgressDecorationStyle
} from "@/utils/playerDecorations";

describe("player progress decorations", () => {
  it("selects the bouquet for Jay Chou's Handwritten Past", () => {
    const decoration = getPlayerProgressDecoration({
      title: " 手写的从前 ",
      artistName: "周杰伦"
    });

    expect(decoration).toMatchObject({
      assetUrl: "/decorations/handwritten-past-bouquet.png",
      rotation: "-9deg"
    });
  });

  it("does not decorate a different artist or song", () => {
    expect(getPlayerProgressDecoration({ title: "手写的从前", artistName: "其他歌手" })).toBeNull();
    expect(getPlayerProgressDecoration({ title: "晴天", artistName: "周杰伦" })).toBeNull();
  });

  it("exposes the image and rotation as reusable CSS variables", () => {
    expect(getPlayerProgressDecorationStyle({
      assetUrl: "/decorations/handwritten-past-bouquet.png",
      rotation: "-9deg"
    })).toEqual({
      "--player-progress-thumb-image": "url(\"/decorations/handwritten-past-bouquet.png\")",
      "--player-progress-thumb-rotation": "-9deg"
    });
  });

  it("aligns the decorated fill with the center of the large thumb", () => {
    expect(getDecoratedRangeFillPosition(0)).toBe("calc(0% + 20px)");
    expect(getDecoratedRangeFillPosition(25)).toBe("calc(25% + 10px)");
    expect(getDecoratedRangeFillPosition(50)).toBe("50%");
    expect(getDecoratedRangeFillPosition(75)).toBe("calc(75% - 10px)");
    expect(getDecoratedRangeFillPosition(100)).toBe("calc(100% - 20px)");
  });
});
