(function () {
  function initMermaid() {
    if (!window.mermaid) {
      return;
    }
    window.mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict'
    });
    var nodes = document.querySelectorAll('pre.mermaid');
    if (nodes.length) {
      if (typeof window.mermaid.run === 'function') {
        window.mermaid.run({ nodes: nodes });
      } else {
        window.mermaid.init(undefined, nodes);
      }
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMermaid);
  } else {
    initMermaid();
  }
})();
