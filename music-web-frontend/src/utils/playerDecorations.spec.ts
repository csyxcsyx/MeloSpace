import { describe, expect, it } from "vitest";
import {
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
});
