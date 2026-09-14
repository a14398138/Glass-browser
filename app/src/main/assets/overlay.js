(function () {
  const config = __CONFIG__;
  const id = '__glass_browser_style_v1';
  let style = document.getElementById(id);
  if (!config.enabled) { if (style) style.remove(); return; }
  if (!style) {
    style = document.createElement('style');
    style.id = id;
    (document.head || document.documentElement).appendChild(style);
  }
  const color = config.dark ? '#111111' : '#ffffff';
  const shadow = config.dark
    ? '0 0 2px #fff,0 0 4px #fff'
    : '0 1px 2px rgba(0,0,0,0.9),0 0 3px rgba(0,0,0,0.8),0 0 6px rgba(0,0,0,0.6)';

  let media = 'img,video,canvas {opacity:' + config.opacity + ' !important;}';
  if (config.mode === 1) {
    media = 'img,video,canvas {opacity:1 !important;outline:1px solid ' + color + ' !important;}';
  } else if (config.mode === 2) {
    media = 'img,video,canvas {visibility:hidden !important;}';
  }

  // Force transparent backgrounds across pages, dark mode text readability,
  // and maintain clean styling without dark opaque overlays blocking the video behind
  style.textContent = `
    html, body {
      background-color: transparent !important;
      background-image: none !important;
    }
    header, nav, [role="banner"], [role="navigation"], [data-testid="primaryColumn"], [data-testid="sidebarColumn"] {
      background-color: transparent !important;
      background-image: none !important;
    }
    article, [data-testid="cellInnerDiv"], [data-testid="tweet"] {
      background-color: rgba(18, 24, 34, 0.28) !important;
      border-bottom: 1px solid rgba(255, 255, 255, 0.12) !important;
      backdrop-filter: none !important;
      -webkit-backdrop-filter: none !important;
    }
    div, section, main {
      background-color: transparent !important;
      background-image: none !important;
    }
    body, body * {
      color: ${color} !important;
      text-shadow: ${shadow} !important;
    }
    input, textarea, select, [role="dialog"], [role="menu"] {
      background-color: rgba(24, 30, 42, 0.85) !important;
      color: white !important;
      text-shadow: none !important;
      border-radius: 8px !important;
    }
    svg {
      color: ${color} !important;
      fill: currentColor !important;
    }
    ${media}
  `;
})();
