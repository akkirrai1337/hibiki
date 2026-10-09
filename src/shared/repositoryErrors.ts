// How "this repository is already in the list" (core/api/repositories.ts) reaches the add dialog.
// Errors cross IPC as plain text, so the marker rides in the message for the renderer to translate.

const MARKER = "[repository-already-added]";

export function repositoryAlreadyAddedError(url: string): Error {
  return new Error(`Repository is already added: ${url} ${MARKER}`);
}

export function isRepositoryAlreadyAdded(message: string): boolean {
  return message.includes(MARKER);
}
