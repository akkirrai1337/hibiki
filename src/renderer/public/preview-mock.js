// TEMP: preview-only stub, not shipped. Lets the settings screen render outside Electron
// (no preload) so it can be reviewed in a plain browser tab. Delete before committing.
window.hibiki = new Proxy(
  {},
  {
    get(_t, prop) {
      return new Proxy(() => Promise.resolve([]), {
        get(_t2, p2) {
          return () => Promise.resolve([]);
        },
      });
    },
  },
);
