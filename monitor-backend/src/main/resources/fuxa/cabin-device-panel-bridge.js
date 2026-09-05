/*
 * Shared visual helper for FUXA detail-card pool scripts.
 * FUXA runs each client script in its own function scope, so the pool script
 * calls this helper after it has found the newly created card.
 */
(function installN4CabinDevicePanelBridge() {
  const scrollStyleId = 'n4-device-card-scroll-style';

  function installCardScrollStyle() {
    if (document.getElementById(scrollStyleId)) return;
    const style = document.createElement('style');
    style.id = scrollStyleId;
    style.textContent = `
      .fab-card.n4-device-card-scroll {
        scrollbar-color: #5a626b #090d12;
        scrollbar-width: thin;
      }
      .fab-card.n4-device-card-scroll::-webkit-scrollbar {
        width: 8px;
        height: 8px;
      }
      .fab-card.n4-device-card-scroll::-webkit-scrollbar-track {
        background: #090d12;
      }
      .fab-card.n4-device-card-scroll::-webkit-scrollbar-thumb {
        background: #5a626b;
        border: 2px solid #090d12;
        border-radius: 4px;
      }
      .fab-card.n4-device-card-scroll::-webkit-scrollbar-thumb:hover {
        background: #858f99;
      }
      .fab-card.n4-device-card-scroll .card-close,
      .fab-card.n4-device-card-scroll .card-close i {
        color: #c8d6df !important;
      }
      .fab-card.n4-device-card-scroll .card-close:hover,
      .fab-card.n4-device-card-scroll .card-close:hover i {
        color: #ffffff !important;
      }
    `;
    document.head.appendChild(style);
  }

  function buildDeviceListUrl(panel) {
    const pageParameters = new URLSearchParams(globalThis.location.search);
    const deviceListUrl = pageParameters.get('n4DeviceListUrl') || '';
    if (!deviceListUrl) return '';

    try {
      const url = new URL(deviceListUrl);
      url.searchParams.set('panelKey', panel.panelKey || '');
      url.searchParams.set('templateType', panel.templateType || '');
      url.searchParams.set('deviceRole', panel.deviceRole || '');
      url.searchParams.set('mqttDeviceName', panel.mqttDeviceName || '');
      url.searchParams.set('fields', JSON.stringify(panel.fields || []));
      const n4AccessToken = globalThis.__n4CabinRuntime?.n4AccessToken || '';
      if (n4AccessToken) url.searchParams.set('n4AccessToken', n4AccessToken);
      return url.toString();
    } catch (error) {
      console.warn('[N4] Cannot build the detail-card list URL.', error);
      return '';
    }
  }

  function fitCardToViewport(card, preferredPosition, listHeight) {
    const margin = 12;
    const rect = card.getBoundingClientRect();
    const baseWidth = Math.ceil(rect.width);
    const baseHeight = Math.ceil(rect.height);
    const totalHeight = baseHeight + listHeight;
    const availableWidth = Math.max(1, globalThis.innerWidth - margin * 2);
    const availableHeight = Math.max(1, globalThis.innerHeight - margin * 2);
    const renderedWidth = Math.min(baseWidth, availableWidth);
    const renderedHeight = Math.min(totalHeight, availableHeight);
    const desiredLeft = Number(preferredPosition?.left) || margin;
    const desiredTop = Number(preferredPosition?.top) || margin;
    const left = Math.max(margin, Math.min(desiredLeft, globalThis.innerWidth - renderedWidth - margin));
    const top = Math.max(margin, Math.min(desiredTop, globalThis.innerHeight - renderedHeight - margin));
    const needsVerticalScroll = totalHeight > availableHeight;
    const needsHorizontalScroll = baseWidth > availableWidth;

    Object.assign(card.style, {
      position: 'fixed',
      left: `${left}px`,
      top: `${top}px`,
      right: 'auto',
      bottom: 'auto',
      zIndex: '1000',
      width: `${renderedWidth}px`,
      height: `${renderedHeight}px`,
      minHeight: `${renderedHeight}px`,
      overflowX: needsHorizontalScroll ? 'auto' : 'hidden',
      overflowY: needsVerticalScroll ? 'auto' : 'hidden',
      scrollbarGutter: 'stable',
      transform: '',
      transformOrigin: ''
    });
    return { baseHeight, totalHeight, viewportHeight: renderedHeight };
  }

  globalThis.n4AttachDevicePointList = function n4AttachDevicePointList(card, panel, preferredPosition) {
    if (!card || card.querySelector('[data-n4-device-points]')) return false;
    const src = buildDeviceListUrl(panel || {});
    if (!src) return false;

    const listHeight = 180;
    const layout = fitCardToViewport(card, preferredPosition, listHeight);
    card.dataset.n4DevicePoints = 'true';
    card.classList.add('n4-device-card-scroll');
    installCardScrollStyle();

    const container = document.createElement('section');
    container.dataset.n4DevicePoints = 'true';
    Object.assign(container.style, {
      position: 'absolute', left: '0', right: '0', top: `${layout.baseHeight}px`,
      height: `${listHeight}px`, overflow: 'hidden',
      border: '0', background: '#090d12', zIndex: '10'
    });
    const frame = document.createElement('iframe');
    frame.src = src;
    frame.title = 'N4 device point list';
    frame.setAttribute('scrolling', 'auto');
    Object.assign(frame.style, {
      display: 'block', width: '100%', height: '100%', border: '0', background: '#090d12'
    });
    container.appendChild(frame);
    card.appendChild(container);
    return true;
  };
}());
