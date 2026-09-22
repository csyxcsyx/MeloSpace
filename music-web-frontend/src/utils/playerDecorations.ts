import type { Song } from "@/api/types";

export interface PlayerProgressDecoration {
  assetUrl: string;
  rotation: string;
}

interface PlayerProgressDecorationRule extends PlayerProgressDecoration {
  title: string;
  artistName: string;
}

const PLAYER_PROGRESS_DECORATIONS: PlayerProgressDecorationRule[] = [
  {
    title: "手写的从前",
    artistName: "周杰伦",
    assetUrl: "/decorations/handwritten-past-bouquet.png",
    rotation: "-9deg"
  }
];

type DecoratableSong = Pick<Song, "title" | "artistName">;

export function getPlayerProgressDecoration(song?: DecoratableSong | null) {
  if (!song) return null;
  const title = song.title.trim();
  const artistName = song.artistName?.trim();
  return PLAYER_PROGRESS_DECORATIONS.find((rule) => (
    rule.title === title && rule.artistName === artistName
  )) ?? null;
}

export function getPlayerProgressDecorationStyle(decoration?: PlayerProgressDecoration | null) {
  if (!decoration) return {};
  return {
    "--player-progress-thumb-image": `url("${decoration.assetUrl}")`,
    "--player-progress-thumb-rotation": decoration.rotation
  };
}

export function getDecoratedRangeFillPosition(progressPercent: number, thumbSize = 40) {
  const percent = Math.min(100, Math.max(0, progressPercent));
  const offset = thumbSize / 2 * (1 - 2 * percent / 100);
  if (Math.abs(offset) < 0.001) return `${percent}%`;
  const operator = offset < 0 ? "-" : "+";
  return `calc(${percent}% ${operator} ${Math.abs(offset)}px)`;
}
