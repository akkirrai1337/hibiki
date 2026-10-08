// Every Provider function the host may call. Shared by the runtime, which decides what to call,
// and by each platform's worker, which dispatches the call into the script.
export type ExtensionMethod =
  | "search"
  | "latest"
  | "getById"
  | "getPlaybackGroups"
  | "getPlayerLinks"
  | "getSettings"
  | "resolve"
  | "browserScript"
  // Account and the things an account unlocks. Every one of these is optional on the script side:
  // a source that declares none of the matching capabilities never gets asked, and one that
  // declares them but lacks the function fails loudly rather than silently doing nothing.
  | "login"
  // A source whose ACCOUNT setting declares `webLoginUrl` gets this instead of/alongside "login" -
  // the cookies harvested from a real sign-in window (see BrowserPort.login) rather than
  // a login+password pair, since the host never collected or even saw either of those here.
  | "loginWeb"
  | "logout"
  | "getAccount"
  | "listComments"
  | "postComment"
  | "voteComment"
  | "listReviews"
  | "postReview"
  | "syncLibraryEntry"
  | "listLibrary"
  | "reportPlayback"
  | "pingOnline";
