// Fades out and removes the static #splash placeholder once React has actually painted something
// into #root, instead of leaving it up on a timer that could outlast or cut off real content.
(function () {
  var splash = document.getElementById("splash");
  var root = document.getElementById("root");
  var observer = new MutationObserver(function () {
    if (root.childNodes.length === 0) return;
    splash.classList.add("hidden");
    observer.disconnect();
    setTimeout(function () {
      splash.remove();
    }, 300);
  });
  observer.observe(root, { childList: true });
})();
