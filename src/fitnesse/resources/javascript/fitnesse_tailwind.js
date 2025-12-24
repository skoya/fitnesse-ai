(function () {
  var toggle = document.getElementById("color-mode-toggle");
  if (!toggle || !document.body) {
    return;
  }

  var storageKey = "fitnesse_color_mode";

  function getStoredMode() {
    try {
      return window.localStorage.getItem(storageKey);
    } catch (e) {
      return null;
    }
  }

  function setStoredMode(mode) {
    try {
      window.localStorage.setItem(storageKey, mode);
    } catch (e) {
      // Ignore storage failures.
    }
  }

  function prefersDarkMode() {
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  function applyMode(mode) {
    var safeMode = mode === "dark" ? "dark" : "light";
    document.body.setAttribute("data-color-mode", safeMode);
    toggle.setAttribute("aria-pressed", safeMode === "dark" ? "true" : "false");
    toggle.textContent = safeMode === "dark" ? "Light" : "Dark";
  }

  var initialMode = getStoredMode() || (prefersDarkMode() ? "dark" : "light");
  applyMode(initialMode);

  toggle.addEventListener("click", function () {
    var current = document.body.getAttribute("data-color-mode") === "dark" ? "dark" : "light";
    var next = current === "dark" ? "light" : "dark";
    setStoredMode(next);
    applyMode(next);
  });
})();
