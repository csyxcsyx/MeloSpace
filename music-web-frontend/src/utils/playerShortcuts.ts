export type PlayerShortcutAction =
  | "toggle-playback"
  | "previous-track"
  | "next-track"
  | "volume-up"
  | "volume-down";

export function resolvePlayerShortcut(event: KeyboardEvent): PlayerShortcutAction | null {
  if (event.defaultPrevented || event.repeat || event.metaKey) return null;

  if (event.ctrlKey && event.altKey) {
    if (event.key === "ArrowLeft") return "previous-track";
    if (event.key === "ArrowRight") return "next-track";
    if (event.key === "ArrowUp") return "volume-up";
    if (event.key === "ArrowDown") return "volume-down";
    return null;
  }

  if (event.ctrlKey || event.altKey || (event.key !== " " && event.code !== "Space")) return null;
  if (isTextEntryTarget(event.target)) return null;
  return "toggle-playback";
}

function isTextEntryTarget(target: EventTarget | null) {
  if (!(target instanceof Element)) return false;
  return Boolean(target.closest("input, textarea, select, [contenteditable='true'], [role='textbox']"));
}
