const KEYBOARD_NAVIGATION_KEYS = new Set([
  "Tab",
  "ArrowUp",
  "ArrowDown",
  "ArrowLeft",
  "ArrowRight",
  "Home",
  "End",
  "PageUp",
  "PageDown"
]);

export type InputModality = "keyboard" | "pointer";

export function installInputModalityTracking(documentTarget: Document = document) {
  const root = documentTarget.documentElement;

  const setModality = (modality: InputModality) => {
    root.dataset.inputModality = modality;
  };
  const handlePointer = () => setModality("pointer");
  const handleKeyboard = (event: KeyboardEvent) => {
    if (KEYBOARD_NAVIGATION_KEYS.has(event.key)) setModality("keyboard");
  };

  if (!root.dataset.inputModality) setModality("pointer");
  documentTarget.addEventListener("pointerdown", handlePointer, true);
  documentTarget.addEventListener("keydown", handleKeyboard, true);

  return () => {
    documentTarget.removeEventListener("pointerdown", handlePointer, true);
    documentTarget.removeEventListener("keydown", handleKeyboard, true);
  };
}
