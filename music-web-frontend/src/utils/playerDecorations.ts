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
