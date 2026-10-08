// Runs synchronously, before <body> paints - see index.html for why. Mirrors uiStore's own
// zustand-persist read (localStorage key "hibiki-ui", { state: { theme } } shape) without waiting
// on React, so <html> already has the right theme class for the very first paint.
(function () {
  try {
    var stored = JSON.parse(localStorage.getItem("hibiki-ui") || "null");
    if (stored && stored.state && stored.state.theme === "light") return;
  } catch (e) {}
  document.documentElement.classList.add("dark");
})();
